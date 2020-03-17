package uk.ac.imperial.cisbio.adam;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import play.Logger;

import com.den_4.inotify_java.EventQueueFull;
import com.den_4.inotify_java.InotifyEvent;
import com.den_4.inotify_java.InotifyEventListener;
import com.den_4.inotify_java.MonitorService;
import com.den_4.inotify_java.enums.Event;
import com.den_4.inotify_java.exceptions.InotifyException;

class FilesystemMonitor implements Runnable {
	private String root;
	private FilesystemListener listener;
	private ItemDAO items;

	public FilesystemMonitor(String root, FilesystemListener listener, ItemDAO items) {
		this.root = root;
		this.listener = listener;
		this.items = items;
	}

	@Override
	public void run() {
		try {
			new InotifyEventListener() {
				Map<String, Long> pending = Collections.synchronizedMap(new HashMap<String, Long>());
				Map<String, Long> deleted = Collections.synchronizedMap(new HashMap<String, Long>());
				Map<Integer, String> moved = new HashMap<Integer, String>();
				private MonitorService monitorService = new MonitorService();
				{
					subscribe(new File(root));
					new Timer(true).schedule(new TimerTask() {
						@Override
						public void run() {
							synchronized (pending) {
								for (Iterator<Map.Entry<String, Long>> i = pending.entrySet().iterator(); i.hasNext();) {
									Map.Entry<String, Long> entry = i.next();
									if (System.currentTimeMillis() - entry.getValue() > 1000) {
										if (!items.exists("path", entry.getKey())) {
											Item item = new Item(entry.getKey());
											items.save(item);
											listener.create(item);
										}
										i.remove();
									}
								}
							}
						}
					}, 1000, 1000);
					new Timer(true).schedule(new TimerTask() {
						@Override
						public void run() {
							synchronized (deleted) {
								for (Iterator<Map.Entry<String, Long>> i = deleted.entrySet().iterator(); i.hasNext();) {
									Map.Entry<String, Long> entry = i.next();
									if (System.currentTimeMillis() - entry.getValue() > 5000) {
										String path = entry.getKey();
										if (new File(root + path).exists()) {
											listener.update(items.findOne("path", path));
										} else {
											Item item = items.getDatastore().findAndDelete(items.createQuery().filter("path", path));
											listener.delete(item);
										}
										i.remove();
									}
								}
							}
						}
					}, 1000, 2500);
				}
				@Override
				public void filesystemEventOccurred(InotifyEvent e) {
					Logger.debug(e.toString());
					File file = new File(e.getContextualName());
					String path = FilesystemUtil.relpath(root, e.getContextualName());
					pending.remove(path);
					try {
						if (e.isCreate() && e.aboutDirectory() && !ignored(file)) {
							subscribe(file);
							if (!file.getParent().equals(ADAM.root)) {
								Item item = new Item(path);
								items.save(item);
								listener.create(item);
							}
						}
						if (e.isCloseWrite() && !ignored(file)) {
							if (!items.exists("path", path)) {
								Item item = new Item(path);
								items.save(item);
								listener.create(item);
								deleted.remove(path);//TODO what is this for?
							} else {//hack to deal with CLOSE_WRITE sometimes occurring just before delete (e.g. move to Desktop)
								deleted.put(path, System.currentTimeMillis());
							}

						}
						if (e.isDelete() && e.aboutDirectory() && !ignored(file)) {
							System.out.println("UNSUBSCRIBE " + e.getContextualName());
							monitorService.removeListener(monitorService.getWatchDescriptor(e.getContextualName()), this);
						}
						if (e.isDelete() && items.exists("path", path)) {
							deleted.put(path, System.currentTimeMillis());
						}
						if (e.isMovedFrom() && items.exists("path", path)) {
							moved.put(e.getCookie(), e.getContextualName());
						}
						if (e.isMovedTo()) {
							if (moved.containsKey(e.getCookie())) {//moved from non-ignored file inside repository
								move(moved.remove(e.getCookie()), file);
							} else if (!e.aboutDirectory()) {
								if (items.exists("path", path)) {//moved from ignored file
									deleted.remove(path);
									listener.update(items.findOne("path", path));
								} else {//moved from outside repository
									Item item = new Item(path);
									items.save(item);
									listener.create(item);
								}
							}
						}
					} catch (InotifyException e1) {
						Logger.error("Fatal error", e);
						System.exit(-1);
					}
				}
				@Override
				public void queueFull(EventQueueFull e) {
					Logger.error("Fatal error", e);
					System.exit(-1);
				}
				private void subscribe(File folder) throws InotifyException {
					if (monitorService.getWatchDescriptor(folder.getPath()) == -1) {
						System.out.println("SUBSCRIBE " + folder);
						monitorService.addListener(monitorService.addWatch(folder.getPath(), Event.Modify, Event.Close_Write, Event.Create, Event.Delete, Event.Moved_From, Event.Moved_To), this);
					}
					for (File file : folder.listFiles()) {
						if (!ignored(file)) {
							if (file.isDirectory()) {
								subscribe(file);
							}
							String path = FilesystemUtil.relpath(root, file.getPath());
							if (!items.exists("path", path) && !file.getParent().equals(root)) {
								pending.put(path, System.currentTimeMillis());
							}
						}
					}
				}
				//TODO overwriting directories?
				private void move(String from, File to) throws InotifyException {
					String toPath = FilesystemUtil.relpath(root, to.getPath());
					if (items.exists("path", toPath)) {
						items.deleteByQuery(items.createQuery().filter("path", toPath));
					}
					items.updateFirst(items.createQuery().field("path").equal(FilesystemUtil.relpath(root, from)), items.createUpdateOperations().set("path", toPath));
					listener.update(items.createQuery().field("path").equal(toPath).get());
					if (to.isDirectory()) {
						System.out.println("MOVE UNSUBSCRIBE " + from);
						monitorService.removeListener(monitorService.getWatchDescriptor(from), this);
						System.out.println("MOVE SUBSCRIBE " + to);
						monitorService.addListener(monitorService.addWatch(to.getPath(), Event.Modify, Event.Close_Write, Event.Create, Event.Delete, Event.Moved_From, Event.Moved_To), this);
						for (File child : to.listFiles()) {
							move(new File(from, child.getName()).getPath(), child);
						}
					}
				}
				private boolean ignored(File file) {
					return file.getName().startsWith(".")
							|| file.getName().startsWith(".~lock.")//ooffice tmp file
							|| file.getName().matches("~\\w+\\.tmp")//gedit tmp file
							|| file.equals(new File(ADAM.root, "vagrant"));
					//|| file.getParent().equals(root);
				}
			};
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}
}
