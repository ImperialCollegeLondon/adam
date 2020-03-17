package uk.ac.imperial.cisbio.adam.handlers;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.core.MultivaluedMap;

import org.apache.tika.metadata.Metadata;

import play.Logger;
import uk.ac.imperial.cisbio.adam.Handler;
import uk.ac.imperial.cisbio.adam.Item;
import uk.ac.imperial.cisbio.adam.ItemDAO;
import affymetrix.fusion.cel.FusionCELData;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.core.util.MultivaluedMapImpl;

public class MicroarrayDatasetHandler extends Handler {

	public MicroarrayDatasetHandler() {
		super("dataset", "application/x-affymetrix-cel");
	}

	@Override
	public boolean handle(Item item, Metadata metadata, ItemDAO items) throws Exception {
		FusionCELData cel = new FusionCELData();
		cel.setFileName(item.getFile().getPath());
		if (cel.read()) {
			item.subtype = "Microarray";
			item.array_type = cel.getChipType();
			item.algorithm = cel.getAlg();
			//				int n = cel.getCells();
			//		        double sum = 0;
			//		        for (int i = 0; i < cel.getCells(); i++) {
			//		            sum += cel.getIntensity(i);
			//		        }
			//		        item.average_intensity = (int) sum / n;
			item.geo_platform = items.count("array_type", cel.getChipType()) > 0 ? items.findOne("array_type", cel.getChipType()).geo_platform : getGeoPlatform(cel.getChipType());
			return true;
		}
		return false;
	}

	private static WebResource geoPlatform;
	static {
		geoPlatform = Client.create()
				.resource("http://www.ncbi.nlm.nih.gov/geo/query/browse.cgi?mode=foundplatform");
	}
	private static String getGeoPlatform(String title) {
		Logger.debug("searching geo for platform %s", title);
		MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
		formData.add("title", title);
		String html = geoPlatform
				.type("application/x-www-form-urlencoded")
				.post(String.class, formData);
		Matcher matcher = Pattern.compile("HandleViewPlatformClick\\('(.*?)'\\)").matcher(html);
		return matcher.find() ? matcher.group(1) : null;
	}
}
