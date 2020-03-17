package notifiers;

import javax.naming.NamingException;

import models.User;

import play.Play;
import play.mvc.Mailer;
import uk.ac.imperial.cisbio.adam.Item;
import uk.ac.imperial.cisbio.adam.Util;

public class Emailer extends Mailer {

	public static void shared(Item item, String recipient) throws NamingException {
		User owner  = User.findById(item.owner);
		String ownerName = owner.cn;
		setSubject("[%s] New shared item", Play.configuration.getProperty("application.name"));
		addRecipient(User.<User>findById(recipient).email);
		setFrom(Play.configuration.getProperty("email"));
		send(ownerName, item);
	}
}
