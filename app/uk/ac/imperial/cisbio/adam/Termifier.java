package uk.ac.imperial.cisbio.adam;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import play.libs.WS;
import play.libs.XPath;
import util.Properties;

public class Termifier {
	private static Map<String, String> types = new HashMap<String, String>() {{
		//whatizit
		put("disease", "disease");
		put("go", "GO term");
		put("uniprot", "protein");
		put("chebi", "compound");
		//reflect
		put("-14", "disease");
		put("9606", "protein");//(human), 7227 (melanogaster), 3702 (thaliana), 4932 (cerevisiae), 562 (coli)
		put("-1", "compound");
	}};
	public static Set<Map<String, String>> termify(String s) throws MalformedURLException, IOException {
		Set<Map<String, String>> terms = new HashSet<Map<String, String>>();
		List<Entity> entities = "reflect".equals(Properties.getString("termifier")) ? reflect(s) : whatizit(s);
		for (final Entity e : entities) {
			final String type = types.get(e.sem != null ? e.sem : e.z);
			if (type != null) {
				terms.add(new HashMap<String, String>() {{
					put("text", e.text);
					put("type", type);
				}});
			}
		}
		return terms;
	}

	private static List<Entity> reflect(String t) {
		List<Entity> entities = new ArrayList<Entity>();
		Document document = WS.url("http://reflect.ws/REST/GetEntities").setParameter("document", t).post().getXml();
		for (Node item : XPath.selectNodes("//item", document)) {
			String name = XPath.selectText("name", item);
			for (Node entity : XPath.selectNodes("entities/entity", item)) {
				String type = XPath.selectText("type", entity);
				String identifier = XPath.selectText("identifier", entity);
				entities.add(new Entity(null, type, identifier, name));

			}
		}
		return entities;
	}

	private static List<Entity> whatizit(String t) throws MalformedURLException, IOException {
		String request = String.format("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:ns1=\"http://www.ebi.ac.uk/webservices/whatizit/ws\">" +
				"<SOAP-ENV:Body>" +
				"<ns1:contact>" +
				"<pipelineName>whatizitUkPmcAll</pipelineName>" +
				"<text>%s</text>" +
				"<convertToHtml>false</convertToHtml>" +
				"</ns1:contact>" +
				"</SOAP-ENV:Body>" +
				"</SOAP-ENV:Envelope>", t);
		Document envelope = WS.url("http://www.ebi.ac.uk/webservices/whatizit/ws").setHeader("Content-Type", "text/xml").body(request).post().getXml();
		String xml = XPath.selectText("//return", envelope);
		final Matcher m = Pattern.compile("<z:(\\w+)(?: sem=\"(.*?)\")? .*?ids=\"(.*?)\".*?>(.*?)</z:(?:\\1)>").matcher(xml);
		List<Entity> entities = new ArrayList<Entity>();
		while (m.find()) {
			entities.add(new Entity(m.group(1), m.group(2), m.group(3), m.group(4)));
		}
		return entities;
	}

	private static class Entity {
		String z;
		String sem;
		String ids;
		String text;

		public Entity(String z, String sem, String ids, String text) {
			this.z = z;
			this.sem = sem;
			this.ids = ids;
			this.text = text;
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof Entity && text.equals(((Entity) obj).text) && z.equals(((Entity) obj).z);
		}

		@Override
		public String toString() {
			return String.format("%s %s %s %s", z, sem, ids, text);
		}
	}

}
