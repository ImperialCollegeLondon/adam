package util;

import play.Play;

public class Properties {

	public static String getString(String key) {
		return Play.configuration.getProperty(key);
	}

	public static String getRoot() {
		return Play.configuration.getProperty("root", "/home");
	}

}
