package com.jd.journalq.broker.producer.transaction.handler;

import com.jd.journalq.broker.BrokerContext;
import com.jd.journalq.broker.producer.Produce;
import com.jd.journalq.broker.producer.transaction.command.TransactionCommitRequest;
import com.jd.journalq.exception.JournalqCode;
import com.jd.journalq.exception.JournalqException;
import com.jd.journalq.message.BrokerCommit;
import com.jd.journalq.network.command.BooleanAck;
import com.jd.journalq.network.command.CommandType;
import com.jd.journalq.network.session.Producer;
import com.jd.journalq.network.transport.Transport;
import com.jd.journalq.network.transport.command.Command;
import com.jd.journalq.network.transport.command.Type;
import com.jd.journalq.network.transport.command.handler.CommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TransactionCommitRequestHandler
 * author: gaohaoxiang
 * email: gaohaoxiang@jd.com
 * date: 2019/4/12
 */
public class TransactionCommitRequestHandler implements CommandHandler, Type {

    protected static final Logger logger = LoggerFactory.getLogger(TransactionCommitRequestHandler.class);

    private Produce produce;

    public TransactionCommitRequestHandler(BrokerContext brokerContext) {
        this.produce = brokerContext.getProduce();
    }

    @Override
    public Command handle(Transport transport, Command command) {
        TransactionCommitRequest transactionCommitRequest = (TransactionCommitRequest) command.getPayload();
        Producer producer = new Producer(transactionCommitRequest.getTopic(), transactionCommitRequest.getTopic(), transactionCommitRequest.getApp(), Producer.ProducerType.JMQ);
        int code = JournalqCode.SUCCESS.getCode();

        for (String txId : transactionCommitRequest.getTxIds()) {
            BrokerCommit brokerCommit = new BrokerCommit();
            brokerCommit.setTopic(transactionCommitRequest.getTopic());
            brokerCommit.setApp(transactionCommitRequest.getApp());
            brokerCommit.setTxId(txId);

            try {
                produce.putTransactionMessage(producer, brokerCommit);
            } catch (JournalqException e) {
                if (e.getCode() == JournalqCode.CN_TRANSACTION_NOT_EXISTS.getCode()) {
                    logger.error("commit transaction error, transaction not exists, topic: {}, app: {}, txId: {}", transactionCommitRequest.getTopic(), transactionCommitRequest.getApp(), txId);
                } else {
                    logger.error("commit transaction exception, topic: {}, app: {}, txId: {}", transactionCommitRequest.getTopic(), transactionCommitRequest.getApp(), txId, e);
                }
                if (e.getCode() != JournalqCode.SUCCESS.getCode()) {
                    code = e.getCode();
                }
            } catch (Exception e) {
                logger.error("commit transaction exception, topic: {}, app: {}, txId: {}", transactionCommitRequest.getTopic(), transactionCommitRequest.getApp(), txId, e);
                code = JournalqCode.CN_UNKNOWN_ERROR.getCode();
            }
        }

        return BooleanAck.build(code);
    }

    @Override
    public int type() {
        return CommandType.TRANSACTION_COMMIT_REQUEST;
    }
}