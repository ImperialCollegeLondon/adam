package uk.ac.imperial.cisbio.adam;

import org.bson.types.ObjectId;

import com.google.code.morphia.Datastore;
import com.google.code.morphia.dao.BasicDAO;

public class ItemDAO extends BasicDAO<Item, ObjectId> {
	public ItemDAO(Datastore datastore) {
		super(datastore);
	}
}