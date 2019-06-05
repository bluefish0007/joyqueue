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
package com.jd.journalq.nsr.ignite.service;


import com.google.inject.Inject;
import com.jd.journalq.domain.Broker;
import com.jd.journalq.event.BrokerEvent;
import com.jd.journalq.event.MetaEvent;
import com.jd.journalq.model.PageResult;
import com.jd.journalq.model.QPageQuery;
import com.jd.journalq.nsr.ignite.dao.BrokerDao;
import com.jd.journalq.nsr.ignite.model.IgniteBroker;
import com.jd.journalq.nsr.message.Messenger;
import com.jd.journalq.nsr.model.BrokerQuery;
import com.jd.journalq.nsr.service.BrokerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author lixiaobin6
 * 下午3:11 2018/8/13
 */
public class IgniteBrokerService implements BrokerService {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private BrokerDao brokerDao;

    @Inject
    protected Messenger messenger;

    @Inject
    public IgniteBrokerService(BrokerDao brokerDao) {
        this.brokerDao = brokerDao;
    }


    public IgniteBroker toIgniteModel(Broker model) {
        return new IgniteBroker(model);
    }

    @Override
    public Broker getByIpAndPort(String brokerIp, Integer brokerPort) {
        BrokerQuery brokerQuery = new BrokerQuery();
        brokerQuery.setIp(brokerIp);
        brokerQuery.setPort(brokerPort);
        List<IgniteBroker> list = brokerDao.list(brokerQuery);
        if (null == list || list.size() < 1) {
            return null;
        }

        if (list.size() > 1) {
            throw new RuntimeException("illegal state exception.too many brokers.");
        }
        return list.get(0);
    }

    @Override
    public List<Broker> getByRetryType(String retryType) {
        BrokerQuery query = new BrokerQuery();
        query.setRetryType(retryType);

        return convert(brokerDao.list(query));
    }

    @Override
    public Broker getById(Integer id) {
        return brokerDao.findById(id);
    }
    @Override
    public List<Broker> getByIds(List<Integer> ids) {
        if (ids == null || ids.size() <=0){
            return null;
        }
        return ids.stream().map(brokerId-> brokerDao.findById(brokerId)).filter(broker -> broker != null).collect(Collectors.toList());
    }

    @Override
    public Broker get(Broker model) {
        return brokerDao.findById(model.getId());
    }

    @Override
    public void addOrUpdate(Broker broker) {
        brokerDao.addOrUpdate(toIgniteModel(broker));
        publishEvent(BrokerEvent.event(broker));
    }

    public void publishEvent(MetaEvent event) {
        try {
            logger.info("publishEvent {}", event);
            messenger.publish(event);
        } catch (Exception ignored) {
            logger.warn("pulish event failure {}", event);
        }
    }

    @Override
    public void deleteById(Integer id) {
        brokerDao.deleteById(id);
    }

    @Override
    public void delete(Broker model) {
        brokerDao.deleteById(model.getId());
    }

    @Override
    public List<Broker> list() {
        return this.list(null);
    }

    @Override
    public List<Broker> list(BrokerQuery query) {
        return convert(brokerDao.list(query));
    }

    @Override
    public PageResult<Broker> pageQuery(QPageQuery<BrokerQuery> pageQuery) {
        PageResult<IgniteBroker> iBrokers = brokerDao.pageQuery(pageQuery);

        return new PageResult<>(iBrokers.getPagination(), convert(iBrokers.getResult()));
    }

    private List<Broker> convert(List<IgniteBroker> iBrokers) {
        if (iBrokers == null) {
            return Collections.EMPTY_LIST;
        }

        return new ArrayList<>(iBrokers);
    }
}
