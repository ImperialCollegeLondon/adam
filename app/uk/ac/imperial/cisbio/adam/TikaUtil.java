package uk.ac.imperial.cisbio.adam;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;

import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.FormatException;
import loci.formats.IFormatReader;
import loci.formats.ImageReader;
import loci.formats.UnknownFormatException;
import loci.formats.ome.OMEXMLMetadata;
import loci.formats.services.OMEXMLService;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

public class TikaUtil {
	private static Logger logger = LoggerFactory.getLogger(TikaUtil.class);
	private static Tika tika = new Tika();

	public static MimeType mimetype(String path) {
		try {
			return new MimeType(new File(path).isDirectory() ? "inode/directory" : tika.detect(path));
		} catch (MimeTypeParseException e) {
			throw new RuntimeException(e);
		}
	}

	public static Metadata metadata(String path, MimeType mimeType) throws IOException, TikaException, DependencyException, ServiceException, FormatException {
		InputStream stream = new URL("file:" + path).openStream();
		Metadata metadata = new Metadata();
		metadata.set(Metadata.RESOURCE_NAME_KEY, new File(path).getName());
		tika.parse(stream, metadata);
		//TODO possibly register a custom tika listener for all image types
		if ("image".equals(mimeType.getPrimaryType())) {
			OMEXMLService service = new ServiceFactory().getInstance(OMEXMLService.class);
			OMEXMLMetadata omeXMLMetadata = service.createOMEXMLMetadata();
			IFormatReader imageReader = new ImageReader();
			imageReader.setMetadataStore(omeXMLMetadata);
			try {
				imageReader.setId(path);
				metadata.set("Image-Count", Integer.toString(omeXMLMetadata.getImageCount()));
			} catch (UnknownFormatException e) {
				logger.warn("Bio-Formats UnknownFormatException %s %s", path, metadata.get("Content-Type"));
			}

			//OME ome = .toString()(OME) omeXMLMetadata.getRoot();
			//if (omeXMLMetadata.getInstrumentCount() > 0) {
			//	response.getWriter().println(ome.getInstrument(0).getMicroscope());
			//}
		}
		return metadata;
	}

	public static String data(String path, MimeType mimeType) throws IOException, TikaException, DependencyException, ServiceException, FormatException, SAXException {
		StringBuilder sb = new StringBuilder();
		//TODO this replicates MetadataProvider
		Metadata metadata = metadata(path, mimeType);
		for (String name : metadata.names()) {
			sb.append(String.format("%s: %s%n", name, metadata.get(name)));
		}
		sb.append(String.format("%n"));
		//TODO consider calling tika.setMaxStringLength() - default is 100000 chars
		sb.append(parseToString(path, mimeType));

		//		StringWriter writer = new StringWriter();
		//		new AutoDetectParser().parse(new FileInputStream(path), new BoilerpipeContentHandler(writer), new Metadata());
		//		sb.append(writer.toString());

		return sb.toString();
	}

	//TODO get rid of this when tika is fixed
	public static String parseToString(String path, MimeType mimeType) throws IOException, TikaException {
		if ("application/pdf".equals(mimeType.getBaseType())) {
			return new java.util.Scanner(Runtime.getRuntime().exec(new String[] {"pdftotext", path, "-"}).getInputStream()).useDelimiter("\\A").next()
					.replace("\u035E", "/")//rakyan2.pdf
					.replace("\u2013", "-");//beste 05 bcg growth inventory.pdf
		} else {
			return tika.parseToString(new File(path));
		}
		//
		//		StringWriter writer = new StringWriter();
		//		try {
		//			PDFTextStripper stripper = new PDFTextStripper();
		//			PDDocument document = PDDocument.load(path);
		//			stripper.writeText(document, writer);
		//			document.close();
		//		} catch (IOException e) {
		//			//StomatalClosure/Bibliography/Pham2009.pdf
		//			logger.error(e.getMessage(), e);
		//		}
		//		return writer.toString().replace("\u0001", "/");//StomatalClosure/Bibliography/Young2006.pdf;
	}
}
