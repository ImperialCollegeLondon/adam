package uk.ac.imperial.cisbio.adam.handlers;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.activation.MimeType;

import jobs.Bootstrap;

import org.apache.tika.language.LanguageIdentifier;
import org.apache.tika.metadata.MSOffice;
import org.apache.tika.metadata.Metadata;

import uk.ac.imperial.cisbio.adam.Handler;
import uk.ac.imperial.cisbio.adam.Item;
import uk.ac.imperial.cisbio.adam.ItemDAO;
import uk.ac.imperial.cisbio.adam.Termifier;
import uk.ac.imperial.cisbio.adam.TikaUtil;

public class DocumentHandler extends Handler {
	private static Properties languages = new Properties();
	static {
		try {
			languages.load(Bootstrap.class.getResourceAsStream("/org/apache/tika/language/tika.language.properties"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	private static Map<String, String> subtypes = new HashMap<String, String>() {{
		put("application/vnd.openxmlformats-officedocument.wordprocessingml.document",  "Document");
		put("application/msword", "Document");
		put("application/vnd.openxmlformats-officedocument.presentationml.presentation", "Presentation");
		put("application/vnd.ms-powerpoint", "Presentation");
		put("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Spreadsheet");
		put("application/vnd.ms-excel", "Spreadsheet");
	}};

	public DocumentHandler() {
		super("document",subtypes.keySet().toArray(new String[0]));
	}

	@Override
	public boolean handle(Item item, Metadata metadata, ItemDAO items) throws Exception {
		item.subtype = subtypes.get(item.format);
		if (metadata.get(Metadata.TITLE) != null && !metadata.get(Metadata.TITLE).trim().isEmpty()) {
			item.title = metadata.get(Metadata.TITLE);
		}
		if (metadata.get(MSOffice.LAST_AUTHOR) != null && !metadata.get(MSOffice.LAST_AUTHOR).replaceAll("\\W", "").trim().isEmpty()) {
			item.last_author = metadata.get(MSOffice.LAST_AUTHOR).trim();
		}
		if (metadata.get(MSOffice.LAST_SAVED) != null) {
			Date modified ;
			try {//Microsoft Office
				modified = new SimpleDateFormat("EEE MMM dd kk:mm:ss zzz yyyy").parse(metadata.get(MSOffice.LAST_SAVED));
			} catch (ParseException e) {//OpenOffice
				modified = new SimpleDateFormat("yyyy-MM-dd'T'kk:mm:ss'Z'").parse(metadata.get(MSOffice.LAST_SAVED));
			}
			item.modified = modified.getTime() / 1000;
		}
		if (metadata.get("Last-Modified") != null) {
			item.modified = new SimpleDateFormat("yyyy-MM-dd'T'kk:mm:ss'Z'").parse(metadata.get("Last-Modified")).getTime() / 1000;
		}
		if (Arrays.asList(
				"application/vnd.openxmlformats-officedocument.wordprocessingml.document",
				"application/msword"
				).contains(item.format)) {
			if (metadata.get(MSOffice.WORD_COUNT) != null) {
				item.word_count = Long.parseLong(metadata.get(MSOffice.WORD_COUNT));
			}
			String text = TikaUtil.parseToString(item.getFile().getPath(), new MimeType(item.format));
			LanguageIdentifier li = new LanguageIdentifier(text);
			//if (!"en".equals(li.getLanguage()) && li.isReasonablyCertain()) {
			if (!"en".equals(li.getLanguage()) && text.length() / Double.parseDouble(li.toString().split("[(|)]")[1]) > 50000) {
				item.language = languages.getProperty("name." + li.getLanguage());
			}
			item.text = text;
			item.entities = Termifier.termify(text);
		}
		return true;
	}
}
