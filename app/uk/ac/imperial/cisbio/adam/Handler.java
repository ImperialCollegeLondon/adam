package uk.ac.imperial.cisbio.adam;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;

import org.apache.commons.collections.IteratorUtils;
import org.apache.tika.metadata.Metadata;

@SuppressWarnings("unchecked")
public abstract class Handler {
	private static List<Handler> handlers;
	static {
		handlers = IteratorUtils.toList(ServiceLoader.load(Handler.class).iterator());
	}
	public static void handle(final Item item, final ItemDAO items, final ADAMListener listener) {
		File file = item.getFile();
		final String path = file.getPath();
		//NB TikaUtil.mimetype(path) is deliberately more reliable than metadata.get(Metadata.CONTENT_TYPE) (viz .py)
		final MimeType mimeType = TikaUtil.mimetype(path);
		item.format = mimeType.getBaseType();//e.g. "inode/directory"
		//item now has path and format set (but nothing else)
		for (final Handler handler : handlers) {
			if (handler.handles(mimeType, file)) {
				item.type = handler.getType();
				handler.executor.execute(new Runnable() {
					@Override
					public void run() {
						try {
							if (handler.handle(item, TikaUtil.metadata(path, mimeType), items)) {
								listener.postUpdate(item);
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				});
			}
		}
	}

	private String type;
	private Set<MimeType> mimeTypes = new HashSet<MimeType>();

	private ExecutorService executor = Executors.newSingleThreadExecutor();

	protected Handler(String type, String... mimeTypes) {
		this.type = type;
		try {
			for (String mimeType : mimeTypes) {
				this.mimeTypes.add(new MimeType(mimeType));
			}
		} catch (MimeTypeParseException e) {
			throw new RuntimeException(e);
		}
	}

	public String getType() {
		return type;
	}

	//by default, just match on mimeType. override this to be more specific.
	public boolean handles(MimeType mimeType, File file) {
		for (MimeType _mimeType : mimeTypes) {
			if (_mimeType.match(mimeType)) {
				return true;
			}
		}
		return false;
	}

	protected abstract boolean handle(Item item, Metadata metadata, ItemDAO items) throws Exception;
}
