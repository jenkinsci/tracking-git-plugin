package org.jenkinsci.plugins.trackinggit;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.util.DefaultBuildChooser;
import hudson.plugins.git.util.BuildData;

import java.util.Collections;

import org.jenkinsci.plugins.trackinggit.TrackingGitProperty.ToTrack;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.UnstableBuilder;

public class TrackingGitTest extends AbstractGitTestCase {

	public void test1() throws Exception {

		FreeStyleProject p1 = setupSimpleProject("master");
		FreeStyleProject p2 = setupSimpleProject("master");
		p2.addProperty(new TrackingGitProperty(p1.getName(),
				ToTrack.LAST_STABLE));

		// create initial commit and then run the builds against it:
		final String commitFile1 = "commitFile1";
		commit(commitFile1, johnDoe, "Commit number 1");

		String revision1 = buildAndGetRevision(p1);
		String revision2 = buildAndGetRevision(p2);
		assertEquals(revision1, revision2);

		final String commitFile2 = "commitFile2";
		commit(commitFile2, janeDoe, "Commit number 2");

		// Should still build r1 even though r2 now exists, since p1 hasn't
		// built r2 yet.
		revision2 = buildAndGetRevision(p2);
		assertEquals(revision1, revision2);

		revision1 = buildAndGetRevision(p1);
		revision2 = buildAndGetRevision(p2);
		assertEquals(revision1, revision2);

		final String commitFile3 = "commitFile3";
		commit(commitFile3, johnDoe, "Commit number 3");

		p1.getBuildersList().add(new UnstableBuilder());

		// Now p1 builds r3 but it is unstable, so p2 should still build r2.
		String newRevision1 = buildAndGetRevision(p1);
		revision2 = buildAndGetRevision(p2);
		assertFalse(newRevision1 == revision2);
		assertEquals(revision1, revision2);
	}

	private String buildAndGetRevision(FreeStyleProject p) throws Exception {
		FreeStyleBuild b = p.scheduleBuild2(0).get();
		System.out.println(getLog(b));
		BuildData buildData = b.getAction(BuildData.class);
		return buildData.getLastBuiltRevision().getSha1String();
	}

	private FreeStyleProject setupProject(String branchString,
			boolean authorOrCommitter) throws Exception {
		return setupProject(branchString, authorOrCommitter, null);
	}

	private FreeStyleProject setupProject(String branchString,
			boolean authorOrCommitter, String relativeTargetDir)
			throws Exception {
		return setupProject(branchString, authorOrCommitter, relativeTargetDir,
				null, null, null);
	}

	private FreeStyleProject setupProject(String branchString,
			boolean authorOrCommitter, String relativeTargetDir,
			String excludedRegions, String excludedUsers, String includedRegions)
			throws Exception {
		return setupProject(branchString, authorOrCommitter, relativeTargetDir,
				excludedRegions, excludedUsers, null, false, includedRegions);
	}

	private FreeStyleProject setupProject(String branchString,
			boolean authorOrCommitter, String relativeTargetDir,
			String excludedRegions, String excludedUsers, String localBranch,
			boolean fastRemotePoll, String includedRegions) throws Exception {
		FreeStyleProject project = createFreeStyleProject();
		project.setScm(new GitSCM(null,
				createRemoteRepositories(relativeTargetDir), Collections
						.singletonList(new BranchSpec(branchString)), null,
				false, Collections.<SubmoduleConfig> emptyList(), false, false,
				new DefaultBuildChooser(), null, null, authorOrCommitter,
				relativeTargetDir, null, excludedRegions, excludedUsers,
				localBranch, false, false, false, fastRemotePoll, null, null,
				false, includedRegions, false, false));
		project.getBuildersList().add(new CaptureEnvironmentBuilder());
		return project;
	}

	private FreeStyleProject setupSimpleProject(String branchString)
			throws Exception {
		return setupProject(branchString, false);
	}
}
