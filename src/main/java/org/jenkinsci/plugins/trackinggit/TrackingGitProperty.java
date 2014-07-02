package org.jenkinsci.plugins.trackinggit;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.model.Action;
import hudson.model.Item;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.ProminentProjectAction;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.Job;
import hudson.model.Run;
import hudson.plugins.git.GitSCM;
import hudson.security.AccessControlled;
import hudson.util.FormValidation;

import java.util.Collection;
import java.util.Collections;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

public class TrackingGitProperty extends JobProperty<AbstractProject<?, ?>> {

	public enum ToTrack {
		LAST_STABLE("Last stable build") {
			@Override
			public Run<?, ?> getBuild(Job<?, ?> project) {
				return project.getLastStableBuild();
			}
		},
		LAST_SUCCESSFUL("Last successful build") {
			@Override
			public Run<?, ?> getBuild(Job<?, ?> project) {
				return project.getLastSuccessfulBuild();
			}
		},
		LAST_BUILD("Last build") {

			@Override
			public Run<?, ?> getBuild(Job<?, ?> project) {
				return project.getLastCompletedBuild();
			}
		},
		LAST_FAILED_BUILD("Last failed build") {

			@Override
			public Run<?, ?> getBuild(Job<?, ?> project) {
				return project.getLastFailedBuild();
			}
		},
		BY_NUMBER("By Number") {

			@Override
			public Run<?, ?> getBuild(Job<?, ?> project) {
				throw new UnsupportedOperationException();
			}
			
			@Override
			public boolean canDetermineNextTrackedBuild(){
				return false;
			}
		};

		private String displayValue;

		private ToTrack(String displayValue) {
			this.displayValue = displayValue;
		}

		public abstract Run<?, ?> getBuild(Job<?, ?> project);

		@Override
		public String toString() {
			return displayValue;
		}
		
		public boolean canDetermineNextTrackedBuild(){
			return true;
		}
	}

	private final String sourceProject;
	private final String buildNumberVariable;
	private final ToTrack toTrack;

	@DataBoundConstructor
	public TrackingGitProperty(String sourceProject, ToTrack toTrack, String buildNumberVariable) {
		super();
		this.sourceProject = Util.fixEmptyAndTrim(sourceProject);
		this.buildNumberVariable = Util.fixEmptyAndTrim(buildNumberVariable);
		this.toTrack = toTrack;

		if (sourceProject == null) {
			throw new NullPointerException("'project to track' is required");
		}

		if (Hudson.getInstance().getItem(sourceProject) == null) {
			throw new IllegalArgumentException("Project to track unknown: "
					+ sourceProject);
		}
	}

	// do we need to keep the old constructor for backwards compatability?
	public TrackingGitProperty(String sourceProject, ToTrack toTrack) {
		this(sourceProject, toTrack, null);
	}

	public String getSourceProject() {
		return sourceProject;
	}

	public ToTrack getToTrack() {
		return toTrack;
	}
	
	public String getBuildNumberVariable(){
		return buildNumberVariable;
	}
	

	@Extension
	public static class DescriptorImpl extends JobPropertyDescriptor {

		@Override
		public boolean isApplicable(@SuppressWarnings("rawtypes") Class<? extends Job> jobType) {
			return Job.class.isAssignableFrom(jobType);
		}

		@Override
		public String getDisplayName() {
			return "Track another Git project";
		}

		@Override
		public JobProperty<?> newInstance(StaplerRequest req,
				JSONObject formData) throws FormException {
			if (formData.getJSONObject("track-git") == null
					|| formData.getJSONObject("track-git").isNullObject()) {
				return null;
			}

			return super.newInstance(req, formData.getJSONObject("track-git"));
		}

		/**
		 * Form validation method.
		 */
		@SuppressWarnings("rawtypes")
		public FormValidation doCheckSourceProject(
				@AncestorInPath AccessControlled subject,
				@QueryParameter String value) {
			// Require CONFIGURE permission on this project
			if (!subject.hasPermission(Item.CONFIGURE))
				return FormValidation.ok();

			if (value == null)
				return FormValidation.ok();

			value = value.trim();

			if (value.equals("")) {
				return FormValidation.error("This field is required");
			}

			Item item = Hudson.getInstance().getItem(value);
			if (item == null)
				return FormValidation.error("No such project '" + value
						+ "'. Did you mean '"
						+ AbstractProject.findNearest(value).getName() + "' ?");
			if (item instanceof Job
					&& (((AbstractProject) item).getScm() instanceof GitSCM)) {
				return FormValidation.ok();
			}

			return FormValidation.error("'" + value + "' is not a Git project");

		}
	}

	/**
	 * Lookup what build was used in the last build action
	 */
	@SuppressWarnings("rawtypes")
	public Run getLastTrackedBuild() throws TrackingGitException {
		Job<?, ?> job = (Job<?, ?>) Hudson.getInstance().getItem(sourceProject);
		if (job == null)
			throw new TrackingGitException(
					"Unknown source project for tracking-git : "
							+ sourceProject);
		

		Run<?, ?> lastOwnerRun = owner.getLastBuild();
		if (null == lastOwnerRun){
			return null;
		}
		
		TrackingGitAction tga = lastOwnerRun.getAction(org.jenkinsci.plugins.trackinggit.TrackingGitAction.class);
		if (null == tga){
			return null;
		}
		
		Run<?, ?> run = tga.getTrackedBuild();
		return run;
	}
	
	/**
	 * Can we determine the next build which will be tracked
	 * True for lastStable, lastSuccessful
	 * False if we have to work it out at build time.
	 */
	public boolean canDetermineNextTrackedBuild(){
		return toTrack.canDetermineNextTrackedBuild();
	}
	
	@SuppressWarnings("rawtypes")
	public Run getNextTrackedBuild() throws TrackingGitException {
		Job<?, ?> job = (Job<?, ?>) Hudson.getInstance().getItem(sourceProject);
		if (job == null)
			throw new TrackingGitException(
					"Unknown source project for tracking-git : "
							+ sourceProject);

		Run<?, ?> run = toTrack.getBuild(job);
		
		if (run == null)
			throw new TrackingGitException(toTrack + " not found for project "
					+ sourceProject);
		return run;
	}

	/**
	 * Get tracked build using current build and listener so we can resolve 
	 * variables if required.
	 */
	@SuppressWarnings("rawtypes")
	public Run getTrackedBuild(AbstractBuild<?, ?> r, TaskListener listener) throws TrackingGitException {
		Job<?, ?> job = (Job<?, ?>) Hudson.getInstance().getItem(sourceProject);
		if (job == null)
			throw new TrackingGitException(
					"Unknown source project for tracking-git : "
							+ sourceProject);

		Run<?, ?> run;
		
		switch (toTrack) {
		    case BY_NUMBER:
		    	run = job.getBuildByNumber(resolveBuildNumber(r, listener));
			    break;
		    default:
			    run = toTrack.getBuild(job);
			    break;
		}
		if (run == null)
			throw new TrackingGitException(toTrack + " not found for project "
					+ sourceProject);
		return run;
	}

	@SuppressWarnings("rawtypes")
	private int resolveBuildNumber(AbstractBuild r, TaskListener listener){
		try {
			EnvVars environmentVariables = r.getEnvironment(listener);
			int buildNumber = Integer.parseInt(environmentVariables.expand(buildNumberVariable));
            return buildNumber;
		} catch (Exception e) {
			e.printStackTrace(System.out);
			throw new TrackingGitException("Exception resolving build number to track");
		}
	}
	
	@Override
	public Collection<Action> getJobActions(AbstractProject<?, ?> job) {
		return Collections.<Action> singleton(new TrackingGitJobAction());
	}

	public class TrackingGitJobAction implements ProminentProjectAction {

		public TrackingGitProperty getParent() {
			return TrackingGitProperty.this;
		}

		public String getDisplayName() {
			return null;
		}

		public String getIconFileName() {
			return null;
		}

		public String getUrlName() {
			return null;
		}

	}

}
