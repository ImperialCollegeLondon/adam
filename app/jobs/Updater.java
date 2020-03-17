package jobs;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import models.User;
import notifiers.Emailer;
import play.Logger;
import play.jobs.Job;
import uk.ac.imperial.cisbio.adam.ADAM;
import uk.ac.imperial.cisbio.adam.ADAM.Action;
import uk.ac.imperial.cisbio.adam.Item;

public class Updater extends Job<Void> {
	private Item item;
	private Action action;
	private String[] others;

	public Updater(Item item, Action action, String... others) {
		this.item = item;
		this.action = action;
		this.others = others;
	}

	@Override
	//TODO if (UN)SHARED then item may have recipient, in which case it's the old user or group. others may contain the new recipient (user or group).
	public void doJob() throws Exception {
		Set<String> users = new HashSet<String>();
		if (action != Action.UPDATED) {//add recipient user/group and specified others but not owner (because it might be un(sharing))
			Set<String> recipients = new HashSet<String>(Arrays.asList(others));
			if (item.recipient != null) {
				recipients.add(item.recipient);
			}
			for (String recipient : recipients) {
				if (ADAM.uu.getGroups(item.owner).contains(recipient)) {//recipient is a group
					users.addAll(ADAM.uu.getUsers(recipient));
				} else {//recipient is a user
					users.add(recipient);
				}
			}
			users.remove(item.owner);
		} else {//add specified others but not recipients - this is starring etc
			users.addAll(Arrays.asList(others));
		}
		//if (action == Action.CREATED_OR_UPDATED || action == Action.DELETED) {
		users.add(item.owner);
		//}
		Logger.debug(String.format("%s %s %s %s", item.owner, action, others.length > 0 ? Arrays.asList(others) : item.recipient, users));
		ADAM.updates.addAll(users);//TODO ensure that this only contains users, not groups
		if (action == Action.SHARED) {
			for (String recipient : others) {
				emailRecipient(item, recipient);
			}
		}
	}

	private static void emailRecipient(Item item, String recipient) {
		//only notify users, not groups
		if (!ADAM.uu.getGroups(item.owner).contains(recipient)) {
			//make sure they've opted-into email updates
			User user = User.findById(recipient);
			if (user != null && user.email != null && !user.email.isEmpty()) {
				try {
					Emailer.shared(item, recipient);
					Logger.debug(String.format("emailed %s", recipient));
				} catch (Exception e) {
					Logger.error(e.getMessage(), e);
				}
			}
		}
	}
}
