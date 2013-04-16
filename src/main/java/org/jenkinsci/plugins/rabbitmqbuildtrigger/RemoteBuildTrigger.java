package org.jenkinsci.plugins.rabbitmqbuildtrigger;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.ParameterValue;
import hudson.model.AbstractProject;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Project;
import hudson.model.StringParameterValue;
import hudson.model.listeners.ItemListener;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.rabbitmqconsumer.utils.ApplicationMessageNotifyUtil;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * The extension trigger builds by application message.
 * 
 * @author rinrinne a.k.a. rin_ne
 */
public class RemoteBuildTrigger extends Trigger<AbstractProject<?, ?>> {

    private static final String PLUGIN_NAME = Messages.RabbitMQBuildTrigger();

    private static final String KEY_PARAM_NAME = "name";
    private static final String KEY_PARAM_VALUE = "value";

    private static final Logger LOGGER = Logger.getLogger(RemoteBuildTrigger.class.getName());

    private String remoteBuildToken;

    /**
     * Creates instance with specified parameters.
     * 
     * @param remoteBuildToken
     *            the token for remote build.
     */
    @DataBoundConstructor
    public RemoteBuildTrigger(String remoteBuildToken) {
        super();
        this.remoteBuildToken = StringUtils.stripToNull(remoteBuildToken);
    }

    @Override
    public void start(AbstractProject<?, ?> project, boolean newInstance) {
        RemoteBuildListener listener = ApplicationMessageNotifyUtil.getAllListeners().get(RemoteBuildListener.class);
        if (listener != null) {
            listener.addTrigger(this);
        }
        super.start(project, newInstance);
    }

    @Override
    public void stop() {
        ApplicationMessageNotifyUtil.getAllListeners().get(RemoteBuildListener.class).removeTrigger(this);
        super.stop();
    }

    /**
     * Gets token.
     * 
     * @return the token.
     */
    public String getRemoteBuildToken() {
        return remoteBuildToken;
    }

    /**
     * Sets token.
     * 
     * @param remoteBuildToken the token.
     */
    public void setRemoteBuildToken(String remoteBuildToken) {
        this.remoteBuildToken = remoteBuildToken;
    }

    /**
     * Gets project name.
     * 
     * @return the project name.
     */
    public String getProjectName() {
        return job.getName();
    }

    /**
     * Schedules build for triggered job using application message.
     * 
     * @param queueName
     *            the queue name.
     * @param jsonArray
     *            the content of application message.
     */
    public void scheduleBuild(String queueName, JSONArray jsonArray) {
        List<ParameterValue> parameters = getUpdatedParameters(jsonArray, getDefinitionParameters(job));
        job.scheduleBuild2(0, new RemoteBuildCause(queueName), new ParametersAction(parameters));
    }

    /**
     * Gets updated parameters in job.
     * 
     * @param jsonParameters
     *            the array of JSONObjects.
     * @param definedParameters
     *            the list of defined paramters.
     * @return the list of parameter values.
     */
    private List<ParameterValue> getUpdatedParameters(JSONArray jsonParameters, List<ParameterValue> definedParameters) {
        List<ParameterValue> newParams = new ArrayList<ParameterValue>();
        for (ParameterValue defParam : definedParameters) {

            for (int i = 0; i < jsonParameters.size(); i++) {
                JSONObject jsonParam = jsonParameters.getJSONObject(i);

                if (defParam.getName().toUpperCase().equals(jsonParam.getString(KEY_PARAM_NAME).toUpperCase())) {
                    newParams.add(new StringParameterValue(defParam.getName(), jsonParam.getString(KEY_PARAM_VALUE)));
                }
            }
        }
        return newParams;
    }

    /**
     * Gets definition parameters.
     * 
     * @param project
     *            the project.
     * @return the list of parameter values.
     */
    private List<ParameterValue> getDefinitionParameters(AbstractProject<?, ?> project) {
        List<ParameterValue> parameters = new ArrayList<ParameterValue>();
        ParametersDefinitionProperty properties = (ParametersDefinitionProperty) project
                .getProperty(ParametersDefinitionProperty.class);

        if (properties != null) {
            for (ParameterDefinition paramDef : properties.getParameterDefinitions()) {
                ParameterValue param = paramDef.getDefaultParameterValue();
                if (param != null) {
                    parameters.add(param);
                }
            }
        }

        return parameters;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    /**
     * The descriptor for this trigger.
     * 
     * @author rinrinne a.k.a. rin_ne
     */
    @Extension
    public static class DescriptorImpl extends TriggerDescriptor {

        @Override
        public boolean isApplicable(Item item) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return PLUGIN_NAME;
        }

        /**
         * ItemListener implementation class.
         * 
         * @author rinrinne a.k.a. rin_ne
         */
        @Extension
        public static class ItemListenerImpl extends ItemListener {

            @Override
            public void onLoaded() {
                RemoteBuildListener listener = ApplicationMessageNotifyUtil.getAllListeners().get(
                        RemoteBuildListener.class);
                for (Project<?, ?> p : Jenkins.getInstance().getAllItems(Project.class)) {
                    RemoteBuildTrigger t = p.getTrigger(RemoteBuildTrigger.class);
                    if (t != null) {
                        listener.addTrigger(t);
                    }
                }
            }
        }
    }
}
