package org.jenkinsci.plugins.rabbitmqbuildtrigger;

import hudson.model.Build;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Project;
import hudson.tasks.Shell;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.rabbitmqconsumer.extensions.MessageQueueListener;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

public class JenkinsTest {
    // CS IGNORE VisibilityModifier FOR NEXT 3 LINES. REASON: Mocks tests.
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testTriggerBuild() throws Exception {
        RemoteBuildTrigger trigger = new RemoteBuildTrigger("trigger-token");
        FreeStyleProject project = j.createFreeStyleProject("triggered-project");
        project.addTrigger(trigger);
        project.getBuildersList().add(new Shell("echo TRIGGERED"));
        trigger.start(project, false);

        String msg = "{\"project\":\"triggered-project\",\"token\":\"trigger-token\"}";
        RemoteBuildListener listener = MessageQueueListener.all().get(RemoteBuildListener.class);
        listener.onReceive("trigger-queue", "application/json", null, msg.getBytes("UTF-8"));

        waitForBuildCompleted(project);

        FreeStyleBuild build = project.getBuilds().getFirstBuild();
        String s = FileUtils.readFileToString(build.getLogFile());
        assertThat(s, containsString("TRIGGERED"));
    }

    @Test
    public void testNonTriggerBuild() throws Exception {
        RemoteBuildTrigger trigger = new RemoteBuildTrigger("trigger-token");
        FreeStyleProject project = j.createFreeStyleProject("triggered-project2");
        project.addTrigger(trigger);
        project.getBuildersList().add(new Shell("echo TRIGGERED"));
        trigger.start(project, false);

        String msg = "{\"project\":\"triggered-project\",\"token\":\"trigger-token\"}";
        RemoteBuildListener listener = MessageQueueListener.all().get(RemoteBuildListener.class);
        listener.onReceive("trigger-queue", "application/json", null, msg.getBytes("UTF-8"));

        try {Thread.sleep(3000);} catch (Exception e) {}

        assertThat(project.getBuilds().isEmpty(), is(true));
    }

    @Test
    @LocalData
    public void testTriggerBuildWithParameter() throws Exception {
        FreeStyleProject project = null;
        for (FreeStyleProject p : j.getInstance().getAllItems(FreeStyleProject.class)) {
            if ("triggered-project-with-parameter".equals(p.getName())) {
                project = p;
            }
        }
        assertThat(project, is(notNullValue()));

        String msg = "{\"project\":\"triggered-project-with-parameter\",\"token\":\"trigger-token\","
                + "\"parameter\":[{\"name\":\"HOGE\",\"value\":\"fuga\"}]}";
        RemoteBuildListener listener = MessageQueueListener.all().get(RemoteBuildListener.class);
        listener.onReceive("trigger-queue", "application/json", null, msg.getBytes("UTF-8"));

        waitForBuildCompleted(project);

        FreeStyleBuild build = project.getBuilds().getFirstBuild();
        String s = FileUtils.readFileToString(build.getLogFile());
        assertThat("This is untriggered build.", s, containsString("TRIGGERED"));
        ParametersAction act = build.getAction(ParametersAction.class);
        assertThat("Parameter is not 1.", act.getParameters().size(), is(1));
        ParameterValue p = act.getParameters().get(0);
        assertThat("Unknown parameter key.", p.getName(), containsString("HOGE"));
        assertThat("Unknown parameter value.", p.getShortDescription(), containsString("fuga"));
    }

    private void waitForBuildCompleted(Project<?, ?> project) throws Exception {
        int cnt = 0;

        while(project.getBuilds().isEmpty()) {
            try {Thread.sleep(1000);} catch (Exception e) {}
            cnt++;
            if (cnt > 10) {
                throw new Exception("Time out.");
            }
        }

        cnt = 0;
        Build<?, ?> build = project.getFirstBuild();

        while(build.isBuilding()) {
            try {Thread.sleep(1000);} catch (Exception e) {}
            cnt++;
            if (cnt > 10) {
                throw new Exception("Time out.");
            }
        }
    }
}
