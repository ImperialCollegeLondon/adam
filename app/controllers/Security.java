package controllers;

import models.User;
import play.cache.Cache;
import util.BCrypt;

public class Security extends Secure.Security {
	static boolean authenticate(String username, String password) {
		User user = User.findById(username);
		return user != null && BCrypt.checkpw(password, user.password);
	}

	static boolean check(String profile) {
		if ("admin".equals(profile)) {
			return getUser().admin;
		}
		return true;
	}

	public static User getUser() {
		User user = Cache.get(connected(), User.class);
		if (user == null) {
			user = User.findById(connected());
			Cache.set(connected(), user);
		}
		return user;
	}

}
