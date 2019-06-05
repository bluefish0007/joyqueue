package com.jd.journalq.broker.protocol.handler;

import com.google.common.collect.Maps;
import com.jd.journalq.broker.BrokerContext;
import com.jd.journalq.broker.BrokerContextAware;
import com.jd.journalq.broker.protocol.JournalqCommandHandler;
import com.jd.journalq.broker.cluster.ClusterManager;
import com.jd.journalq.broker.helper.SessionHelper;
import com.jd.journalq.broker.monitor.SessionManager;
import com.jd.journalq.domain.TopicName;
import com.jd.journalq.exception.JournalqCode;
import com.jd.journalq.network.command.AddConsumerRequest;
import com.jd.journalq.network.command.AddConsumerResponse;
import com.jd.journalq.network.command.BooleanAck;
import com.jd.journalq.network.command.JournalqCommandType;
import com.jd.journalq.network.session.Connection;
import com.jd.journalq.network.session.Consumer;
import com.jd.journalq.network.transport.Transport;
import com.jd.journalq.network.transport.command.Command;
import com.jd.journalq.network.transport.command.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * AddConsumerRequestHandler
 * author: gaohaoxiang
 * email: gaohaoxiang@jd.com
 * date: 2018/12/10
 */
public class AddConsumerRequestHandler implements JournalqCommandHandler, Type, BrokerContextAware {

    protected static final Logger logger = LoggerFactory.getLogger(AddConsumerRequestHandler.class);

    private SessionManager sessionManager;
    private ClusterManager clusterManager;

    @Override
    public void setBrokerContext(BrokerContext brokerContext) {
        this.sessionManager = brokerContext.getSessionManager();
        this.clusterManager = brokerContext.getClusterManager();
    }

    @Override
    public Command handle(Transport transport, Command command) {
        AddConsumerRequest addConsumerRequest = (AddConsumerRequest) command.getPayload();
        Connection connection = SessionHelper.getConnection(transport);

        if (connection == null || !connection.isAuthorized(addConsumerRequest.getApp())) {
            logger.warn("connection is not exists, transport: {}", transport);
            return BooleanAck.build(JournalqCode.FW_CONNECTION_NOT_EXISTS.getCode());
        }

        Map<String, String> result = Maps.newHashMap();

        for (String topic : addConsumerRequest.getTopics()) {
            TopicName topicName = TopicName.parse(topic);

            if (clusterManager.tryGetConsumer(topicName, addConsumerRequest.getApp()) == null) {
                logger.warn("addConsumer failed, transport: {}, topic: {}, app: {}", transport, topicName, addConsumer.getApp());
                return BooleanAck.build(JournalqCode.CN_NO_PERMISSION);
            }

            Consumer consumer = buildConsumer(connection, topic, addConsumerRequest.getApp(), addConsumerRequest.getSequence());
            sessionManager.addConsumer(consumer);
            result.put(topic, consumer.getId());
        }

        AddConsumerResponse addConsumerResponse = new AddConsumerResponse();
        addConsumerResponse.setConsumerIds(result);
        return new Command(addConsumerResponse);
    }

    protected Consumer buildConsumer(Connection connection, String topic, String app, long sequence) {
        Consumer consumer = new Consumer();
        consumer.setId(generateConsumerId(connection, topic, app, sequence));
        consumer.setConnectionId(connection.getId());
        consumer.setApp(app);
        consumer.setTopic(topic);
        consumer.setType(Consumer.ConsumeType.JMQ);
        return consumer;
    }

    protected String generateConsumerId(Connection connection, String topic, String app, long sequence) {
        return String.format("%s_%s_consumer_%s_%s", connection.getId(), sequence, app, topic);
    }

    @Override
    public int type() {
        return JournalqCommandType.ADD_CONSUMER_REQUEST.getCode();
    }
}