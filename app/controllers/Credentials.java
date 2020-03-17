package controllers;

import models.User;
import play.data.validation.Equals;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.mvc.Controller;
import play.mvc.With;

@With(Secure.class)
public class Credentials extends Controller {
	public static void index() {
		render();
	}

	public static void update(@Required String password, @Equals("password") String passwordConfirmation) {
		if (Validation.hasErrors()) {
			Validation.keep();
			index();
		}
		User user = User.findById(Security.connected());
		user.password = password;
		user.save();
		render();
	}
}
