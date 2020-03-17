package uk.ac.imperial.cisbio.adam;

import java.io.File;

import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.gridfs.GridFS;

public class ADAMListener implements FilesystemListener {
	private static Logger logger = LoggerFactory.getLogger(ADAMListener.class);

	private ItemDAO items;
	private GridFS fs;

	public ADAMListener(ItemDAO items, GridFS fs) {
		this.items = items;
		this.fs = fs;
	}

	@Override
	public void create(Item item) {
		createOrUpdate(item);
		logger.info("created " + item.path);
		//		updateParent(item);
	}
	@Override
	public void update(Item item) {
		createOrUpdate(item);
		logger.info("updated " + item.path);
	}
	@Override
	public void delete(Item item) {
		logger.info("deleted " + item.path);
		if (item.owner != null) {
			ADAM.update(item, ADAM.Action.DELETED);
			ADAM.eu.delete(item);
			//			updateParent(item);
		}
		if (item.thumbnail != null) {
			fs.remove((ObjectId) item.thumbnail.getId());
		}
	}

	private void createOrUpdate(final Item item) {
		Handler.handle(item, items, this);
	}

	void postUpdate(Item item) {
		try {
			File file = item.getFile();
			item.size = Util.size(file);
			item.modified_at = file.lastModified() / 1000;
			item.name = file.getName();
			if (item.owner == null) {//creation (rather than update)
				item.owner = item.path.split(File.separator)[1];
				item.created_at = System.currentTimeMillis() / 1000;
			}
			items.save(item);
			if (item.text != null) {
				ADAM.eu.index(item);
			}
			ADAM.update(item, ADAM.Action.CREATED_OR_UPDATED);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	//	private void updateParent(Item item) {
	//		Item parent = items.findOne("path", new File(item.path).getParent());
	//		if (parent != null) {
	//			createOrUpdate(parent);
	//		}
	//	}

	//	private static String md5sum(String path) throws IOException {
	//		return new Scanner(Runtime.getRuntime().exec(new String[]{"md5sum", path}).getInputStream()).next();
	//	}
}
