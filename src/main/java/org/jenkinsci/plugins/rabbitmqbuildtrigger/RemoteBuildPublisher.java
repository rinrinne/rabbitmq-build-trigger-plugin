/**
 * 
 */
package org.jenkinsci.plugins.rabbitmqbuildtrigger;

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import net.sf.json.JSONObject;

import org.jenkinsci.plugins.rabbitmqconsumer.publishers.PublishChannel;
import org.jenkinsci.plugins.rabbitmqconsumer.publishers.PublishChannelFactory;
import org.jenkinsci.plugins.rabbitmqconsumer.publishers.PublishResult;
import org.kohsuke.stapler.DataBoundConstructor;

import com.rabbitmq.client.AMQP.BasicProperties;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;

/**
 * The extension publish build result using rabbitmq.
 * 
 * @author rinrinne a.k.a. rin_ne
 */
public class RemoteBuildPublisher extends Notifier {

    private static final Logger LOGGER = Logger.getLogger(RemoteBuildPublisher.class.getName());

    private final String KEY_PROJECT = "project";
    private final String KEY_NUMBER = "number";
    private final String KEY_STATUS = "status";
    private final String JSON_CONTENT_TYPE = "application/json";
    private final String LOG_HEADER = "Publish to RabbitMQ: ";
    
    private String exchangeName;

    /**
     * Creates instance with specified parameters.
     */
    @DataBoundConstructor
    public RemoteBuildPublisher(String exchangeName) {
        this.exchangeName = exchangeName;
    }

    /**
     * Gets exchange name.
     *
     * @return the exchange name.
     */
    public String getExchangeName() {
        return exchangeName;
    }

    /**
     * @inheritDoc
     */
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    /**
     * @inheritDoc
     */
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {

        if (exchangeName == null || exchangeName.length() == 0) {
            return true;
        }

        // Generate message (JSON format)
        JSONObject json = new JSONObject();
        json.put(KEY_PROJECT, build.getProject().getName());
        json.put(KEY_NUMBER, build.getNumber());
        json.put(KEY_STATUS, build.getResult().toString());

        // Build property
        BasicProperties.Builder builder = new BasicProperties.Builder();
        builder.appId(RemoteBuildTrigger.PLUGIN_APPID);
        builder.contentType(JSON_CONTENT_TYPE);
        
        // Routing key (ex.)
        String routingKey = RemoteBuildPublisher.class.getPackage().getName();

        // Publish message
        PublishChannel ch = PublishChannelFactory.getPublishChannel();
        if (ch != null && ch.isOpen()) {
            LOGGER.info("Perform: enter publish process");
            // return value is not needed if you don't need to wait.
            Future<PublishResult> future = ch.publish(exchangeName, routingKey, builder.build(), json.toString().getBytes());

            // Wait until publish is completed.
            try {
                PublishResult result = future.get();
                LOGGER.info("Message published.");

                if (result.isSuccess()) {
                    listener.getLogger().println(LOG_HEADER + "Success.");
                } else {
                    listener.getLogger().println(LOG_HEADER + "Fail - " + result.getMessage());
                }
            } catch (Exception e) {
                LOGGER.warning(e.getMessage());
                listener.getLogger().println(LOG_HEADER + "Fail due to exception.");
            }
        }
        return true;
    }

    /**
     * @inheritDoc
     */
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * The descriptor for this publisher.
     * 
     * @author rinrinne a.k.a. rin_ne
     */
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        /**
         * @inheritDoc
         */
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> arg0) {
            return true;
        }

        /**
         * @inheritDoc
         */
        @Override
        public String getDisplayName() {
            return Messages.RabbitMQBuildPublisher();
        }
    }
}
