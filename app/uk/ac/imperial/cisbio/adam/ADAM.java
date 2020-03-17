package uk.ac.imperial.cisbio.adam;

import java.io.File;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import jobs.Updater;
import play.Logger;

import com.google.code.morphia.Morphia;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.DBRef;
import com.mongodb.Mongo;
import com.mongodb.gridfs.GridFS;

public class ADAM {
	//TODO remove these globals (used by resources)
	public static UsersUtil uu;
	public static FSUtil fu;
	public static DBUtil du;
	public static ElasticSearchUtil eu;
	public static int pageSize;
	//TODO definitely get rid of these two
	public static String root;
	private static Morphia morphia;
	public static Set<String> updates = Collections.synchronizedSet(new HashSet<String>());

	private ADAM(String root, int pageSize) throws UnknownHostException {
		ADAM.root = root;
		ADAM.pageSize = pageSize;
		Mongo mongo = new Mongo();
		DB adam = mongo.getDB("adam");
		morphia = new Morphia();
		ItemDAO items = new ItemDAO(morphia.createDatastore(mongo, adam.getName()));
		items.ensureIndexes();
		DBCollection itemsColl = items.getCollection();
		GridFS fs = new GridFS(adam);
		eu = new ElasticSearchUtil();
		reconcile(root, itemsColl, fs);
		uu = new UsersUtil(new File(root));//TODO remove UsersUtil?
		fu = new FSUtil(fs);
		du = new DBUtil(itemsColl, pageSize);
		new FilesystemMonitor(root, new ADAMListener(items, fs), items).run();
	}

	private static void reconcile(String root, DBCollection itemsColl, GridFS fs) {
		for (DBObject item : itemsColl.find()) {
			File file = new File(root, (String) item.get("path"));
			if (!file.exists()) {
				Logger.warn(String.format("%s no longer exists, removing", file));
				itemsColl.remove(item);
			}
		}
		for (DBObject file : fs.getFileList()) {
			if (0 == itemsColl.count(new BasicDBObject("thumbnail", new DBRef(fs.getDB(), fs.getBucketName(), file.get("_id"))))) {
				Logger.warn(String.format("thumbnail %s no longer referenced, removing", file.get("_id")));
				fs.remove(file);
			}
		}
	}

	public enum Action {
		//DELETED, CREATED_OR_UPDATED come from filesystem listener
		//SHARED, UNSHARED, UPDATED come from web interface
		//UPDATED is 'private' updates (e.g. starring) - e.g. the owner should not be notified
		SHARED, UNSHARED, DELETED, CREATED_OR_UPDATED, UPDATED
	}

	//TODO eventually this method should be deprecated
	public static void update(DBObject item, Action action, String... others) {
		update(morphia.fromDBObject(Item.class, item), action, others);
	}

	public static void update(Item item, Action action, String... others) {
		//This has to be done in a job so that it gets a JPA context from Play
		new Updater(item, action, others).now();
	}

	private static ADAM instance = null;
	public static ADAM get(String root, int pageSize) {
		if (instance == null) {
			try {
				instance = new ADAM(root, pageSize);
			} catch (UnknownHostException e) {
				throw new RuntimeException(e);
			}
		}
		return instance;
	}

}
