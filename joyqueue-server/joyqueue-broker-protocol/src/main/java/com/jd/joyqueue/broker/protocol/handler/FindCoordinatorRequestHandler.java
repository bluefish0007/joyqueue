/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jd.joyqueue.broker.protocol.handler;

import com.google.common.collect.Maps;
import com.jd.joyqueue.broker.protocol.JoyQueueCommandHandler;
import com.jd.joyqueue.broker.protocol.JoyQueueContext;
import com.jd.joyqueue.broker.protocol.JoyQueueContextAware;
import com.jd.joyqueue.broker.protocol.converter.BrokerNodeConverter;
import com.jd.joyqueue.broker.protocol.coordinator.Coordinator;
import com.jd.joyqueue.broker.helper.SessionHelper;
import com.jd.joyqueue.domain.Broker;
import com.jd.joyqueue.domain.DataCenter;
import com.jd.joyqueue.exception.JoyQueueCode;
import com.jd.joyqueue.network.command.BooleanAck;
import com.jd.joyqueue.network.command.FindCoordinatorAckData;
import com.jd.joyqueue.network.command.FindCoordinatorRequest;
import com.jd.joyqueue.network.command.FindCoordinatorResponse;
import com.jd.joyqueue.network.command.JoyQueueCommandType;
import com.jd.joyqueue.network.domain.BrokerNode;
import com.jd.joyqueue.network.session.Connection;
import com.jd.joyqueue.network.transport.Transport;
import com.jd.joyqueue.network.transport.command.Command;
import com.jd.joyqueue.network.transport.command.Type;
import com.jd.joyqueue.nsr.NameService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * FindCoordinatorRequestHandler
 * author: gaohaoxiang
 * email: gaohaoxiang@jd.com
 * date: 2018/12/3
 */
public class FindCoordinatorRequestHandler implements JoyQueueCommandHandler, Type, JoyQueueContextAware {

    protected static final Logger logger = LoggerFactory.getLogger(FindCoordinatorRequestHandler.class);

    private Coordinator coordinator;
    private NameService nameService;

    @Override
    public void setJoyQueueContext(JoyQueueContext joyQueueContext) {
        this.coordinator = joyQueueContext.getCoordinator();
        this.nameService = joyQueueContext.getBrokerContext().getNameService();
    }

    @Override
    public Command handle(Transport transport, Command command) {
        FindCoordinatorRequest findCoordinatorRequest = (FindCoordinatorRequest) command.getPayload();
        Connection connection = SessionHelper.getConnection(transport);

        if (connection == null || !connection.isAuthorized(findCoordinatorRequest.getApp())) {
            logger.warn("connection is not exists, transport: {}, app: {}", transport, findCoordinatorRequest.getApp());
            return BooleanAck.build(JoyQueueCode.FW_CONNECTION_NOT_EXISTS.getCode());
        }

        Map<String, FindCoordinatorAckData> coordinators = findCoordinators(connection, findCoordinatorRequest.getTopics(), findCoordinatorRequest.getApp());

        FindCoordinatorResponse findCoordinatorResponse = new FindCoordinatorResponse();
        findCoordinatorResponse.setCoordinators(coordinators);
        return new Command(findCoordinatorResponse);
    }

    protected Map<String, FindCoordinatorAckData> findCoordinators(Connection connection, List<String> topics, String app) {
        Broker coordinatorBroker = coordinator.findGroup(app);
        JoyQueueCode code = JoyQueueCode.SUCCESS;
        BrokerNode coordinatorNode = null;

        if (coordinatorBroker != null) {
            DataCenter brokerDataCenter = nameService.getDataCenter(coordinatorBroker.getIp());
            coordinatorNode = BrokerNodeConverter.convertBrokerNode(coordinatorBroker, brokerDataCenter, connection.getRegion());
        } else {
            logger.warn("find coordinator error, coordinator not exist, topics: {}, app: {}, remoteAddress: {}", topics, app, connection.getAddressStr());
            code = JoyQueueCode.FW_COORDINATOR_NOT_AVAILABLE;
        }

        Map<String, FindCoordinatorAckData> result = Maps.newHashMap();
        for (String topic : topics) {
            result.put(topic, new FindCoordinatorAckData(coordinatorNode, code));
        }

        return result;
    }

    @Override
    public int type() {
        return JoyQueueCommandType.FIND_COORDINATOR_REQUEST.getCode();
    }
}