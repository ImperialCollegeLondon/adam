package controllers;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import play.mvc.Controller;
import play.mvc.With;
import uk.ac.imperial.cisbio.adam.ADAM;

@With(Secure.class)
public class Application extends Controller {

	public static void index() throws IOException {
		//		WSRequest entrezAjax = WS.url("http://entrezajax.appspot.com/%s", "efetch")
		//				.setParameter("apikey", "1d7c2923c1684726dc23d2901c4d8157")
		//				.setParameter("db", "pubmed");
		//		JsonElement json = entrezAjax
		//				.setParameter("id", 17615057)
		//				.get()
		//				.getJson().getAsJsonObject()
		//				.getAsJsonArray("result").get(0).getAsJsonObject();
		//		System.out.println(json);

		Map<String, Object> data = new HashMap<String, Object>() {{
			put("user", ADAM.uu.getUserData(Security.getUser()));
			put("users", ADAM.uu.getUsers());
		}};
		render(data);
	}
}