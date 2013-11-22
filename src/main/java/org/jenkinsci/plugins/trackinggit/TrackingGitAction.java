package org.jenkinsci.plugins.trackinggit;

import hudson.EnvVars;
import hudson.model.EnvironmentContributingAction;
import hudson.model.InvisibleAction;
import hudson.model.AbstractBuild;
import hudson.model.Hudson;
import hudson.model.Job;
import hudson.model.Run;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public class TrackingGitAction extends InvisibleAction implements
		EnvironmentContributingAction {

	private final String trackedBuildProject;
	private final int trackedBuildNumber;

	public TrackingGitAction(Run<?, ?> build) {
		trackedBuildProject = build.getParent().getName();
		trackedBuildNumber = build.getNumber();
	}

	public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
		env.put("TRACKING_GIT_BUILD", getTrackedBuildURL());
	}

	public Run<?, ?> getTrackedBuild() {
		Job<?, ?> job = (Job<?, ?>) Hudson.getInstance().getItem(
				trackedBuildProject);
		return job.getBuildByNumber(trackedBuildNumber);
	}

	@Exported(visibility = 2)
	public String getTrackedBuildProject() {
		return trackedBuildProject;
	}

	@Exported(visibility = 2)
	public int getTrackedBuildNumber() {
		return trackedBuildNumber;
	}

	@Exported(visibility = 2)
	public String getTrackedBuildURL() {
		Run<?, ?> r = getTrackedBuild();
		if (r == null)
			return null;
		return Hudson.getInstance().getRootUrl() + r.getUrl();
	}

}
