package jobs;

import models.User;
import play.Play;
import play.jobs.Job;
import play.jobs.OnApplicationStart;
import play.test.Fixtures;
import uk.ac.imperial.cisbio.adam.ADAM;
import util.Properties;

@OnApplicationStart
public class Bootstrap extends Job<Void> {
	@Override
	public void doJob() throws Exception {
		//if jpa.ddl=create (and using h2 mem?) cache isn't cleared automatically on app reload (even though database is)
		//so we need Fixtures.delete() to clear cache to prevent duplicate pk errors when loading fixtures
		if (User.count() == 0) {
			Fixtures.deleteAllModels();
			Fixtures.loadModels("initial-data.yml");
		}

		int pageSize = Integer.parseInt(Play.configuration.getProperty("pageSize", "20"));
		ADAM.get(Properties.getRoot(), pageSize);
	}
}
