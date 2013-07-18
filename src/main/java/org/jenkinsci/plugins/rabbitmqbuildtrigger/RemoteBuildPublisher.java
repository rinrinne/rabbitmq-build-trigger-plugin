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
import hudson.util.ListBoxModel;

/**
 * The extension publish build result using rabbitmq.
 * 
 * @author rinrinne a.k.a. rin_ne
 */
public class RemoteBuildPublisher extends Notifier {

    private static final Logger LOGGER = Logger.getLogger(RemoteBuildPublisher.class.getName());

    private static final String KEY_PROJECT = "project";
    private static final String KEY_NUMBER = "number";
    private static final String KEY_STATUS = "status";
    private static final String JSON_CONTENT_TYPE = "application/json";

    private static final String LOG_HEADER = "Publish to RabbitMQ: ";
    private static final String TYPE_EXCHANGE = "exchange";
    private static final String TYPE_QUEUE = "queue";
    
    private String brokerType;
    private String brokerName;

    /**
     * Creates instance with specified parameters.
     */
    @DataBoundConstructor
    public RemoteBuildPublisher(String brokerType, String brokerName) {
        this.brokerType = brokerType;
        this.brokerName = brokerName;
    }

    /**
     * Gets broker type.
     *
     * @return the broker type.
     */
    public String getBrokerType() {
        return brokerType;
    }

    /**
     * Sets broker type.
     *
     * @param brokerType the broker type.
     */
    public void setBrokerType(String brokerType) {
        this.brokerType = brokerType;
    }

    /**
     * Gets broker name.
     *
     * @return the broker name.
     */
    public String getBrokerName() {
        return brokerName;
    }

    /**
     * Sets broker name.
     *
     * @param brokerName the broker name.
     */
    public void setBrokerName(String brokerName) {
        this.brokerName = brokerName;
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

        if (brokerName == null || brokerName.length() == 0) {
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
            if (TYPE_QUEUE.equals(brokerType) && getDescriptor().getExchangeName() == null) {
                // Setup - decrare exchange then bind to queue.
                try {
                    PublishResult result = ch.setupExchange(null, brokerName);
                    if (result.isSuccess()) {
                        getDescriptor().setExchangeName(result.getExchangeName());
                    } else {
                        getDescriptor().setExchangeName(null);
                        listener.getLogger().println(LOG_HEADER + "Fail - " + result.getMessage());
                        return true;
                    }
                } catch (Exception e) {
                    getDescriptor().setExchangeName(null);
                    listener.getLogger().println(LOG_HEADER + "Fail due to exception during exchange declaration.");
                    return true;
                }
            }

            String exchangeName = null;
            if (TYPE_QUEUE.equals(brokerType)) {
                exchangeName = getDescriptor().getExchangeName();
            } else if (TYPE_EXCHANGE.equals(brokerType)) {
                exchangeName = brokerName;
            }

            if (exchangeName != null) {
                // return value is not needed if you don't need to wait.
                Future<PublishResult> future = ch.publish(exchangeName, routingKey, builder.build(), json.toString().getBytes());

                // Wait until publish is completed.
                try {
                    PublishResult result = future.get();

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

        private static String exchangeName = null;

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

        /**
         * Sets exchange name.
         *
         * @param exchangeName the exchange name.
         */
        synchronized public void setExchangeName(String exchangeName) {
            DescriptorImpl.exchangeName = exchangeName;
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
         * Fills listbox item for brokerType.
         * @return instance.
         */
        public ListBoxModel doFillBrokerTypeItems() {
            ListBoxModel items = new ListBoxModel();
            items.add(Messages.Exchange(), TYPE_EXCHANGE);
            items.add(Messages.Queue(), TYPE_QUEUE);
            return items;
        }
    }
}
