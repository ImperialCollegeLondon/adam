package uk.ac.imperial.cisbio.adam.handlers;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.activation.MimeType;
import javax.ws.rs.core.MediaType;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import loci.formats.FormatException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.util.PDFImageWriter;
import org.apache.tika.metadata.Metadata;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import play.Logger;
import uk.ac.imperial.cisbio.adam.ADAM;
import uk.ac.imperial.cisbio.adam.Handler;
import uk.ac.imperial.cisbio.adam.Item;
import uk.ac.imperial.cisbio.adam.ItemDAO;
import uk.ac.imperial.cisbio.adam.TikaUtil;
import util.Properties;

import com.mongodb.util.JSON;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;

public class ArticleHandler extends Handler {
	private static DocumentBuilder documentBuilder;
	static {
		try {
			documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			throw new RuntimeException(e);
		}
	}
	private static WebResource entrezAjax;
	static {
		entrezAjax = Client.create()
				.resource("http://entrezajax.appspot.com")
				.queryParam("apikey", Properties.getString("entrezajax.apikey"))
				.queryParam("db", "pubmed");
	}
	private static XPath xpath = XPathFactory.newInstance().newXPath();

	private static Map<String, String> termMapping = new HashMap<String, String>() {{
		put("Nuclear Magnetic Resonance, Biomolecular", "NMR");
	}};

	public ArticleHandler() {
		super("article", "application/pdf");
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean handle(Item item, Metadata metadata, ItemDAO items) throws Exception {
		String path = item.getFile().getPath();
		String doi = extractDOI(TikaUtil.data(path, new MimeType(item.format)));
		JSONArray esummary = esummary(doi, metadata);
		if (esummary == null) {
			Logger.info("No result from PubMed for " + path);
			return false;
		}

		item.thumbnail = ADAM.fu.store(pdfToThumbnail(path), "image/png");
		JSONObject article = esummary.getJSONObject(0);
		item.source = article.getString("Source");
		item.creator = (List<String>) JSON.parse(article.getJSONArray("AuthorList").toString());
		item.title = article.getString("Title");
		item.date = article.getString("SO");
		item.pmid = article.getString("Id");
		item.identifier = doi != null ? doi : article.getString("DOI");
		JSONObject efetch = getEntrezFetch(item.pmid);
		if (efetch == null) {
			Logger.debug("efetch %s is null", item.pmid);
		} else {
			if (efetch.getJSONObject("MedlineCitation").has("MeshHeadingList")) {
				JSONArray meshHeadingList = efetch.getJSONObject("MedlineCitation").getJSONArray("MeshHeadingList");
				Set<String> terms = new HashSet<String>();
				for (int i = 0; i < meshHeadingList.length(); i++) {
					String term = meshHeadingList.getJSONObject(i).getString("DescriptorName");
					if (termMapping.containsKey(term)) {
						item.tags.add(termMapping.get(term));
					}
					terms.add(term);
				}
				item.mesh = terms;
			} else {
				Logger.info("No MESH terms found for " + item.pmid);
			}
			item.text = efetch.getJSONObject("MedlineCitation").getJSONObject("Article").getJSONObject("Abstract").getJSONArray("AbstractText").getString(0);
		}

		return true;
	}

	private static JSONArray getEntrezSummary(String field, String term) throws JSONException {
		Logger.debug("esearch+esummary " + field + "=" + term);
		return entrezAjax
				.path("esearch+esummary")
				.queryParam("retmax", "1")
				.queryParam("max", "1")
				.queryParam("start", "0")
				.queryParam("field", field)
				.queryParam("term", term)
				.accept("application/json")
				.get(JSONObject.class)
				.optJSONArray("result");
	}

	private static JSONObject getEntrezFetch(String id) throws JSONException {
		Logger.debug("efetch " + id);
		return entrezAjax
				.path("efetch")
				.queryParam("id", id)
				.accept(MediaType.APPLICATION_JSON_TYPE)
				.get(JSONObject.class)
				.getJSONArray("result")
				.optJSONObject(0);
	}

	private static WebResource crossRef;
	static {
		crossRef = Client.create()
				.resource("http://www.crossref.org")
				.path("openurl")
				.queryParam("pid", Properties.getString("crossref.pid"))
				.queryParam("noredirect", "true");
	}
	private static String getCrossRef(String doi) {
		return crossRef
				.queryParam("id", doi)
				.get(String.class);
	}

	private static byte[] pdfToThumbnail(String path) throws IOException, FormatException {
		byte[] thumbnail = null;
		String outPath = pdfToImage(path);
		if (outPath != null) {
			thumbnail = ImageHandler.thumbnail(outPath);
			new File(outPath).delete();
		}
		return thumbnail;
	}

	private static String pdfToImage(String path) throws IOException {
		PDDocument document = null;
		File tempFile = File.createTempFile("adam", "_");
		String outPath = null;
		try {
			document = PDDocument.load(path);
			if (document.isEncrypted()) {
				try {
					document.decrypt("");
				} catch (Exception e) {
					Logger.warn("Unable to decrypt " + path);
				}
			}
			String prefix = tempFile.getPath();
			if (new PDFImageWriter().writeImage(document, "png", "", 1, 1, prefix)) {
				outPath =  prefix + "1.png";
			} else {
				Logger.warn("Unable to thumbnail %s", path);
			}
			return outPath;
		} finally {
			if (document != null) {
				document.close();
			}
			//it is faintly possible that another temp file could be allocated with same name before we've read our file, but we'll ignore that
			tempFile.delete();
		}
	}

	private static Pattern doiPattern = Pattern.compile("10\\.\\d+/[ ]?[a-zA-Z0-9\\-.]+[a-zA-Z0-9\\-]");
	private static String extractDOI(String data) {
		String doi = null;
		String[] lines = data.split("\n");
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
			Matcher m = doiPattern.matcher(line);
			if (m.find()) {
				doi = m.group(0).replace(" ", "");
				break;
			} else if (i + 1 < lines.length) {
				//deal with line-spanning DOIs (e.g. Willbanks2010)chipSeq_algorithms.pone.0011471.pdf)
				m = doiPattern.matcher(line + lines[i+1]);
				if (m.find()) {
					doi = m.group(0).replace(" ", "");
					break;
				}
			}
		}
		return doi;
	}

	private static JSONArray esummary(String doi, Metadata metadata) throws JSONException, XPathExpressionException, SAXException, IOException {
		JSONArray esummary = null;

		String title = null;
		if (doi == null) {
			Logger.debug("No DOI found in data or metadata, looking for title");
			if (metadata.get(Metadata.SUBJECT) != null && metadata.get(Metadata.SUBJECT).startsWith("N Engl J Med")) {
				Logger.debug("Title found");
				title = metadata.get(Metadata.TITLE);
			}
		} else {
			Logger.debug("Searching PubMed using DOI: " + doi);
			esummary = getEntrezSummary("aid", doi);
			if (esummary == null) {
				Logger.debug("DOI not found in PubMed, getting title from CrossRef");
				String crossrefResult = getCrossRef(doi);
				title = xpath.evaluate("//article_title", documentBuilder.parse(new InputSource(new StringReader(crossrefResult))));
			}
		}

		if (esummary == null) {
			if (title == null) {
				Logger.debug("Title not found");
			} else {
				Logger.debug("Searching PubMed using title: " + title);
				JSONArray esummaryCandidate = getEntrezSummary("titl", title);
				if (esummaryCandidate == null || esummaryCandidate.length() == 0) {
					Logger.debug("Searching PubMed by title returned no results");
				} else {
					String titleCandidate = esummaryCandidate.getJSONObject(0).getString("Title");
					if (!Pattern.compile(title + "[.]?", Pattern.CASE_INSENSITIVE).matcher(titleCandidate).matches()) {
						Logger.debug("Found a document in PubMed but title doesn't match: " + titleCandidate);
					} else {
						esummary = esummaryCandidate;
					}
				}
			}
		}

		return esummary;
	}

	public static void main(String[] args) throws Exception {
		//		String path = "/tmp/StomatalClosure/Bibliography/Young2006.pdf";
		//		for (File file : new File("/tmp/StomatalClosure/Bibliography/").listFiles(new FilenameFilter() {
		//			@Override
		//			public boolean accept(File dir, String name) {
		//				return name.endsWith(".pdf");
		//			}
		//		})) {
		//			String path = file.getPath();
		//			System.out.print(path + " ");
		//			String data = TikaUtil.data(path, new MimeType("application/pdf"));
		//			//String data = "www.pnas.org͞cgi͞doi͞10.1073͞pnas.0602225103";
		//			//data = data.replace("\u035E", "/");
		//			data = data.replace("\u0001", "/");
		//			String doi = extractDOI(data);
		//			System.out.println(doi != null ? doi : TikaUtil.metadata(path, new MimeType("application/pdf")).get(Metadata.TITLE));
		//		}
	}
}
