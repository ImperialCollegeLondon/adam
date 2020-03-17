package ext;

import play.templates.JavaExtensions;

import com.mongodb.util.JSON;

public class JSONExtensions extends JavaExtensions {

	public static String json(Object object) {
		return JSON.serialize(object);
	}

}