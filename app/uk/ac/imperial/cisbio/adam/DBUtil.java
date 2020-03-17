package uk.ac.imperial.cisbio.adam;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

import org.bson.types.ObjectId;

import play.Logger;
import play.Play;
import play.mvc.Router;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.DBRef;
import com.mongodb.MapReduceCommand.OutputType;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSDBFile;

public class DBUtil {
	private DBCollection itemsColl;
	private int pageSize;

	public DBUtil(DBCollection itemsColl, int pageSize) {
		this.itemsColl = itemsColl;
		this.pageSize = pageSize;
	}

	public Map<String, Object> search(final String username, boolean starred, int page, String q, String type, String tag, String owner, Integer year, String language, String sort) {
		final BasicDBObject query = getBaseQuery(username);
		if (!q.isEmpty()) {
			if (q.startsWith("id:")) {
				query.put("_id", new ObjectId(q.split(":")[1]));
			} else {
				query.put("title", Pattern.compile(q, Pattern.CASE_INSENSITIVE));
			}
		}
		if (starred) {
			query.append(String.format("metadata.%s.favourite", username), true);
		}
		if (!type.isEmpty()) {
			query.put("type", type);
		}
		if (!tag.isEmpty()) {
			query.put(String.format("metadata.%s.tags", username), tag);
		}
		if (!owner.isEmpty()) {
			query.put("owner", owner);
		}
		if (year != null) {
			query.put("modified_at", new BasicDBObject().append("$gte", new GregorianCalendar(year, 0, 1).getTimeInMillis() / 1000).append("$lt", new GregorianCalendar(year + 1, 0, 1).getTimeInMillis() / 1000));
		}
		if (!language.isEmpty() && "document".equals(query.get("type"))) {
			query.put("language", language);
		}

		Map<String, Object> results = results(itemsColl.find(query).sort(new BasicDBObject(sort, "name".equals(sort) ? 1 : -1)).skip(pageSize * page).limit(pageSize).toArray(), username, page, itemsColl.count(query));

		final Set<Integer> years = new HashSet<Integer>();
		for (DBObject item : mapReduce(itemsColl, "function() { if (this.modified_at) emit((new Date(this.modified_at * 1000)).getFullYear(), null); }", "function(key, values) { return key; }", query)) {
			years.add(((Double) item.get("_id")).intValue());
		}
		List<Map<String, Object>> facets = new ArrayList<Map<String, Object>>() {{
			add(new HashMap<String, Object>() {{
				put("name", "type");
				put("values", new TreeSet<String>(itemsColl.distinct("type", query)));
			}});
			add(new HashMap<String, Object>() {{
				put("name", "tag");
				put("values", ADAM.du.tags(username, null));//TODO incorporate query
			}});
			add(new HashMap<String, Object>() {{
				put("name", "owner");
				put("values", new TreeSet<String>(itemsColl.distinct("owner", query)));
			}});
			add(new HashMap<String, Object>() {{
				put("name", "year");
				put("values", years);
			}});
		}};
		if ("document".equals(type)) {
			facets.add(new HashMap<String, Object>() {{
				put("name", "language");
				put("values", new TreeSet<String>(itemsColl.distinct("language", query)));
			}});
		}
		results.put("facets", facets);

		return results;
	}

	public Map<String, Object> articles(String username, boolean starred, int page, String author, String journal, String mesh, Integer year) {
		final BasicDBObject query = getBaseQuery(username).append("type", "article");
		if (starred) {
			query.append(String.format("metadata.%s.favourite", username), true);
		}
		if (!author.isEmpty()) {
			query.put("creator", author);
		}
		if (!journal.isEmpty()) {
			query.put("source", journal);
		}
		if (!mesh.isEmpty()) {
			query.put("mesh", mesh);
		}
		if (year != null) {
			query.put("date", Pattern.compile("^" + year));
		}

		Map<String, Object> results = results(itemsColl.find(query).skip(pageSize * page).limit(pageSize).toArray(), username, page, itemsColl.count(query));

		final Set<Integer> years = new TreeSet<Integer>();
		for (DBObject item : mapReduce(itemsColl, "function() { if (this.date) emit(Number(/^[0-9]{4}/.exec(this.date)[0]), null); }", "function(key, values) { return key; }", query)) {
			years.add(((Double) item.get("_id")).intValue());
		}
		results.put("facets", new ArrayList<Map<String, Object>>() {{
			add(new HashMap<String, Object>() {{
				put("name", "type");
				put("values", new TreeSet<String>(itemsColl.distinct("type", query)));
			}});
			add(new HashMap<String, Object>() {{
				put("name", "author");
				put("values", new TreeSet<String>(itemsColl.distinct("creator", query)));
			}});
			add(new HashMap<String, Object>() {{
				put("name", "journal");
				put("values", new TreeSet<String>(itemsColl.distinct("source", query)));
			}});
			add(new HashMap<String, Object>() {{
				put("name", "year");
				put("values", years);
			}});
			add(new HashMap<String, Object>() {{
				put("name", "mesh");
				put("values", new TreeSet<String>(itemsColl.distinct("mesh", query)));
			}});
		}});

		return results;
	}

	public DBObject favourite(String username, String objectId) {
		DBObject query = getBaseQuery(username).append("_id", new ObjectId(objectId));
		DBObject item = itemsColl.findOne(query);
		boolean favourite = !Boolean.TRUE.equals(getValue(item, String.format("metadata.%s.favourite", username)));
		itemsColl.update(item, new BasicDBObject("$set", new BasicDBObject(String.format("metadata.%s.favourite", username), favourite)));
		Logger.info(String.format("%s (un)starred %s %s", username, objectId, favourite));
		return item;
	}

	public List tags(String username, String prefix) {
		BasicDBObject query = getBaseQuery(username);
		if (prefix != null && !prefix.isEmpty()) {
			query.append(String.format("metadata.%s.tags", username), Pattern.compile("^" + Pattern.quote(prefix)));
		}
		return itemsColl.distinct(String.format("metadata.%s.tags", username), query);
	}

	public List distinct(String username, String field) {
		return itemsColl.distinct(field, getBaseQuery(username));
	}

	public DBObject tag(String username, String objectId, String tag, boolean remove) {
		DBObject query = getBaseQuery(username).append("_id", new ObjectId(objectId));
		DBObject update = new BasicDBObject(remove ? "$pull" : "$addToSet", new BasicDBObject(String.format("metadata.%s.tags", username), tag));
		DBObject old = itemsColl.findAndModify(query, update);
		Logger.info(String.format("%s (un)tagged %s '%s'", username, objectId, tag));
		return old;
	}

	public GridFSDBFile attachment(String username, String objectId, String attachmentName) {
		DBObject query = getBaseQuery(username).append("_id", new ObjectId(objectId));
		DBObject item = itemsColl.findOne(query, new BasicDBObject(attachmentName, true));
		if (item == null) {
			return null;//parent item has been deleted
		}
		DBRef attachment = (DBRef) item.get(attachmentName);
		//unfortunately the java driver can't automatically dereference dbrefs that point at files (rather than documents)
		return new GridFS(itemsColl.getDB(), attachment.getRef()).findOne((ObjectId) attachment.getId());
	}

	public List<Map<String, String>> autocomplete(String username, String q) {
		DBObject query = getBaseQuery(username).append("title", Pattern.compile(q, Pattern.CASE_INSENSITIVE));
		List<Map<String, String>> results = new ArrayList<Map<String, String>>();
		for (final DBObject item : itemsColl.find(query, new BasicDBObject("title", true))) {
			results.add(new HashMap<String, String>() {{
				put("id", item.get("_id").toString());//not currently used
				put("value", (String) item.get("title"));
			}});
		}
		return results;
	}

	public Map<String, Object> share(String username, String id, String target) {
		Map<String, Object> result = new HashMap<String, Object>();
		DBObject query = getBaseQuery(username).append("_id", new ObjectId(id));
		if (target.isEmpty()) {//unshare
			DBObject old = itemsColl.findAndModify(query, new BasicDBObject("$unset", new BasicDBObject("recipient", 1)));
			itemsColl.update(query, new BasicDBObject("$unset", new BasicDBObject("shared", 1)));
			ADAM.update(old, ADAM.Action.UNSHARED);
		} else {//share
			if (ADAM.uu.getUsers().contains(target) || ADAM.uu.getGroups(username).contains(target)) {//user or group
				DBObject update = new BasicDBObject("$unset", new BasicDBObject("shared", 1)).append("$set", new BasicDBObject("recipient", target));
				DBObject old = itemsColl.findAndModify(query, update);
				ADAM.update(old, ADAM.Action.SHARED, target);
				result.put("recipient", target);
			} else {//public
				String uuid = UUID.randomUUID().toString();
				DBObject update = new BasicDBObject("$unset", new BasicDBObject("recipient", 1)).append("$set", new BasicDBObject("shared", uuid));
				DBObject old = itemsColl.findAndModify(query, update);
				ADAM.update(old, ADAM.Action.UNSHARED);
				result.put("path", old.get("path"));
				result.put("shared", uuid);
				result.put("link", Play.configuration.getProperty("application.baseUrl") + Router.reverse("Download.index", result));
			}
		}
		return result;
	}

	public HashMap<String, Object> stats() {
		final List types = (List) itemsColl.group(new BasicDBObject("type", true), null, new BasicDBObject("count", 0), "function(doc, out) { out.count++; }");

		String SUM = "function(key, values) { var sum = 0; for (var i in values) { sum += values[i]; }; return sum; }";
		final Iterable<DBObject> dates = mapReduce(itemsColl, "function() { if (this.created_at > Math.round(new Date().getTime() / 1000) - 7 * 24 * 60 * 60) { emit(new Date(this.created_at * 1000).getDay(), 1); } }", SUM, null);

		final Iterable<DBObject> users = mapReduce(itemsColl, "function() { if (this.owner) { emit(this.owner, this.size); } }", SUM, null);

		final int totalUsers = itemsColl.distinct("owner").size();
		final long totalFileCount = itemsColl.count();
		List total = (List) itemsColl.group(null, new BasicDBObject("size", new BasicDBObject("$exists", true)), new BasicDBObject("size", 0), "function(doc, out) { out.size += doc.size; }");
		final String totalFileSize = Util.humanReadableByteCount(total.isEmpty() ? 0 : ((Number) ((DBObject) total.get(0)).get("size")).longValue() * 1024);
		List latest = (List) itemsColl.group(null, new BasicDBObject("created_at", new BasicDBObject("$exists", true)), new BasicDBObject("created_at", 0), "function(doc, out) { out.created_at = Math.max(out.created_at, doc.created_at); }");
		final long latestFile = latest.isEmpty() ? 0 : ((Number) ((DBObject) latest.get(0)).get("created_at")).longValue();

		return new HashMap<String, Object>() {{
			put("types", types);
			put("dates", dates);
			put("users", users);
			put("summary", new HashMap<String, Object>() {{
				put("totalUsers", totalUsers);
				put("totalFileCount", totalFileCount);
				put("totalFileSize", totalFileSize);
				put("latestFile", latestFile);
			}});
		}};
	}

	public boolean published(String path, String shared) {
		DBObject query = new BasicDBObject()
		.append("path", new BasicDBObject("$in", Util.paths(path)))
		.append("shared", shared);
		return itemsColl.count(query) != 0;
	}

	public boolean shared(String path, String username) {
		Set<String> recipients = new HashSet<String>(ADAM.uu.getGroups(username));
		recipients.add(username);
		DBObject query = new BasicDBObject()
		.append("path", new BasicDBObject("$in", Util.paths(path)))
		.append("recipient", new BasicDBObject("$in", recipients));
		return itemsColl.count(query) != 0;
	}

	public DBObject getItem(String username, String id, String... type) {
		BasicDBObject query = getBaseQuery(username).append("_id", new ObjectId(id));
		if (type.length > 0) {
			query = query.append("type", new BasicDBObject("$in", Arrays.asList(type)));
		}
		return itemsColl.findOne(query);

	}

	//just items i own
	public long itemCount(String username) {
		return itemsColl.count(new BasicDBObject("owner", username));
	}

	private Object getValue(DBObject item, String path) {
		String[] parts = path.split("[.]");
		for (int i = 0; i < parts.length - 1; i++) {
			String field = parts[i];
			if (item.containsField(field) && item.get(field) instanceof DBObject) {
				item = (DBObject) item.get(field);
			} else {
				return null;
			}
		}
		return item.get(parts[parts.length - 1]);
	}

	//items i own + items shared with me
	private BasicDBObject getBaseQuery(String username) {
		Set<DBObject> queries = new HashSet<DBObject>();
		queries.add(new BasicDBObject("owner", username));
		Set<String> recipients = new HashSet<String>(ADAM.uu.getGroups(username));
		recipients.add(username);
		for (DBObject item : itemsColl.find(new BasicDBObject("recipient", new BasicDBObject("$in", recipients)), new BasicDBObject("path", true))) {
			queries.add(new BasicDBObject("_id", item.get("_id")));
			queries.add(new BasicDBObject("path", Pattern.compile("^" + Pattern.quote(item.get("path") + "/"))));
		}
		return new BasicDBObject("$or", queries);
	}

	private static Iterable<DBObject> mapReduce(DBCollection coll, String map, String reduce, DBObject query) {
		return coll.count() == 0 ? Collections.<DBObject>emptyList() : coll.mapReduce(map, reduce, null, OutputType.INLINE, query).results();
	}

	public Map<String, Object> results(final List<DBObject> items, String username, final int page, final long count) {
		for (DBObject item : items) {
			DBObject metadata = (DBObject) getValue(item, String.format("metadata.%s", username));
			if (metadata != null) {
				for (String key : metadata.keySet()) {
					item.put(key, metadata.get(key));
				}
				item.removeField("metadata");
			}
		}

		return new HashMap<String, Object>() {{
			put("page", page);
			put("of", (count + pageSize - 1) / pageSize);
			put("documents", items);
		}};
	}

}
