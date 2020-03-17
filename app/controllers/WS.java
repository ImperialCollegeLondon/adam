package controllers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import jobs.Notifier;
import models.User;

import org.apache.commons.io.FileUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.highlight.HighlightField;
import org.xml.sax.SAXException;

import play.Logger;
import play.cache.Cache;
import play.data.validation.Validation;
import play.mvc.Controller;
import play.mvc.With;
import uk.ac.imperial.cisbio.adam.ADAM;
import uk.ac.imperial.cisbio.adam.Util;

import com.mongodb.DBObject;
import com.mongodb.gridfs.GridFSDBFile;
import com.mongodb.util.JSON;

@With(Secure.class)
public class WS extends Controller {

	public static void attachment(String id, String name) {
		GridFSDBFile attachment = ADAM.du.attachment(Security.connected(), id, name);
		notFoundIfNull(attachment);
		response.contentType = attachment.getContentType();
		renderBinary(attachment.getInputStream());
	}

	public static void autocomplete(String term) {
		List<Map<String, String>> json = ADAM.du.autocomplete(Security.connected(), term);
		renderJSON(JSON.serialize(json));
	}

	//POST
	public static void favourite(String id) {
		String username = Security.connected();
		DBObject item = ADAM.du.favourite(username, id);
		//TODO this shouldn't notify the owner - only the person doing the favouriting
		ADAM.update(item, ADAM.Action.UPDATED, username);
	}

	public static void folders() {
		File home = ADAM.uu.home(Security.connected());
		Set<String> folders = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
		for (File folder : Util.folders(home)) {
			folders.add((folder.getPath() + "/").substring(home.getPath().length()));
		}
		renderJSON(JSON.serialize(folders));
	}

	//POST
	public static void prefs(String email, String cn) throws IOException {
		User user = User.findById(Security.connected());
		user.email = email;
		user.cn = cn;
		validation.valid(user);
		if (Validation.hasErrors()) {
			badRequest();
		}
		user.save();
		Cache.safeDelete(Security.connected());
		user();
	}

	public static void search(boolean starred, String type, String sort, int page, String q, String tag, String owner, String language, Integer year, String author, String journal, String mesh) {
		Map<String, Object> json = "article".equals(type) ? ADAM.du.articles(Security.connected(), starred, page, author, journal, mesh, year) : ADAM.du.search(Security.connected(), starred, page, q, type, tag, owner, year, language, sort);
		renderJSON(JSON.serialize(json));
	}

	public static void fulltext(String term, int page) {
		List<DBObject> items = new ArrayList<DBObject>();
		int fromIndex = page * ADAM.pageSize;
		int toIndex = fromIndex + ADAM.pageSize;
		if (!term.isEmpty()) {
			for (SearchHit hit : ADAM.eu.search(term)) {
				DBObject item = ADAM.du.getItem(Security.connected(), hit.getId());
				if (item != null) {
					Map<String, String[]> highlightFields = new HashMap<String, String[]>();
					for (Map.Entry<String, HighlightField> highlightField : hit.getHighlightFields().entrySet()) {
						highlightFields.put(highlightField.getKey(), highlightField.getValue().getFragments());
					}
					item.put("highlightFields", highlightFields);
					items.add(item);
					if (items.size() >= toIndex) {
						break;
					}
				}
			}
		}
		if (fromIndex > items.size()) {
			items.clear();
		}
		if (toIndex > items.size()) {
			toIndex = items.size();
		}
		renderJSON(JSON.serialize(ADAM.du.results(items.subList(fromIndex, toIndex), Security.connected(), page, items.size())));
	}

	//POST
	public static void share(String id, String recipient) {
		Map<String, Object> json = ADAM.du.share(Security.connected(), id, recipient);
		renderJSON(JSON.serialize(json));
	}

	@Check("admin")
	public static void stats() {
		HashMap<String, Object> json = ADAM.du.stats();
		renderJSON(JSON.serialize(json));
	}

	//POST, DELETE
	public static void tag(String id, String tag) {
		DBObject old = ADAM.du.tag(Security.connected(), id, tag, "DELETE".equals(request.method));
		ADAM.update(old, ADAM.Action.UPDATED);
	}

	public static void tags(String term) {
		List json = ADAM.du.tags(Security.connected(), term);
		renderJSON(JSON.serialize(json));
	}

	public static void text(String id) throws TransformerConfigurationException, TransformerFactoryConfigurationError, FileNotFoundException, IOException, SAXException, TikaException {
		StringWriter writer = new StringWriter();
		String username = Security.connected();
		DBObject item = ADAM.du.getItem(username, id, "document", "note");//ensure that don't allow people to convert non-text documents
		if (item != null) {
			TransformerHandler handler = ((SAXTransformerFactory) SAXTransformerFactory.newInstance()).newTransformerHandler();
			handler.getTransformer().setOutputProperty(OutputKeys.METHOD, "xml");
			handler.setResult(new StreamResult(writer));
			Metadata metadata = new Metadata();
			//this is necessary because for notes (unlike documents) the title is part of the metadata (and not the data)
			//if it isn't added then tika inserts an empty title element, making the resultant xhtml invalid
			metadata.set(Metadata.TITLE, (String) item.get("title"));
			new AutoDetectParser().parse(new FileInputStream(new File(ADAM.root, (String) item.get("path"))), handler, metadata);
		}
		renderHtml(writer);
	}

	public static void poll() {
		final String update = await(new Notifier(Security.connected(), TimeUnit.MINUTES.toMillis(1)).now());
		Map<String, Object> json = new HashMap<String, Object>() {{
			put("update", update);
		}};
		Logger.info("repoll %s", Security.connected());
		renderJSON(JSON.serialize(json));
	}

	//POST
	public static void upload(File file, String path) {
		try {
			FileUtils.moveFileToDirectory(file, new File(new File(ADAM.root, Security.connected()), path == null ? "/" : path), false);
			renderJSON("{'jsonrpc' : '2.0', 'result' : null, 'id' : 'id'}");
		} catch (IOException e) {
			//TODO this doesn't seem actually cause the plupload interface to report an error
			renderJSON("{'jsonrpc' : '2.0', 'error' : {'code': 103, 'message': 'Failed to move uploaded file.'}, 'id' : 'id'}");
		}
	}

	public static void user() throws IOException {
		renderJSON(JSON.serialize(ADAM.uu.getUserData(Security.getUser())));
	}
}