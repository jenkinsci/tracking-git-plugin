package org.jenkinsci.plugins.trackinggit;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.listeners.RunListener;
import hudson.plugins.git.RevisionParameterAction;
import hudson.plugins.git.util.BuildData;

@SuppressWarnings("rawtypes")
@Extension
public class TrackingGitRunListener extends RunListener<AbstractBuild> {

	public TrackingGitRunListener() {
		super(AbstractBuild.class);
	}

	@Override
	public void onStarted(AbstractBuild r, TaskListener listener) {
		TrackingGitProperty property = ((AbstractBuild<?, ?>) r).getProject()
				.getProperty(TrackingGitProperty.class);
		if (property == null) {
			return;
		}

		Run<?, ?> trackedBuild = property.getTrackedBuild();
		listener.getLogger().println(
				"Tracking Git of " + trackedBuild.getFullDisplayName());

		BuildData buildData = trackedBuild.getAction(BuildData.class);
		if (buildData == null) {
			listener.getLogger()
					.println(
							"The tracked project doesn't use Git as SCM. Will not set a revision.");
			return;
		}

		RevisionParameterAction action = new RevisionParameterAction(buildData
				.getLastBuiltRevision().getSha1String());
		r.addAction(action);
		r.addAction(new TrackingGitAction(trackedBuild));
	}

}
