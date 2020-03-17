package jobs;

import play.jobs.Job;
import uk.ac.imperial.cisbio.adam.ADAM;

public class Notifier extends Job<String> {
	private String username;
	private long timeout;

	public Notifier(String username, long timeout) {
		this.username = username;
		this.timeout = timeout;
	}

	@Override
	public String doJobWithResult() {
		long expiration = System.currentTimeMillis() + timeout;
		while (System.currentTimeMillis() < expiration) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
			}
			synchronized (ADAM.updates) {
				if (ADAM.updates.contains(username)) {
					ADAM.updates.remove(username);
					return username;
				}
			}
		}
		return null;
	}
}