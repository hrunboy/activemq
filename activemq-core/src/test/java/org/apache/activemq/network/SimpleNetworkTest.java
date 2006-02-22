/**
 * 
 * Copyright 2005-2006 The Apache Software Foundation
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.apache.activemq.network;

import java.net.URI;
import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import junit.framework.TestCase;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.command.ActiveMQTopic;
import org.apache.activemq.xbean.BrokerFactoryBean;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
public class SimpleNetworkTest extends TestCase{
    protected static final int MESSAGE_COUNT=10;
    protected AbstractApplicationContext context;
    protected Connection localConnection;
    protected Connection remoteConnection;
    protected BrokerService localBroker;
    protected BrokerService remoteBroker;
    protected Session localSession;
    protected Session remoteSession;
    protected ActiveMQTopic included;
    protected ActiveMQTopic excluded;
    protected String consumerName="durableSubs";

    public void testFiltering() throws Exception{
        MessageConsumer includedConsumer=remoteSession.createConsumer(included);
        MessageConsumer excludedConsumer=remoteSession.createConsumer(excluded);
        MessageProducer includedProducer=localSession.createProducer(included);
        MessageProducer excludedProducer=localSession.createProducer(excluded);
        Thread.sleep(1000);
        Message test=localSession.createTextMessage("test");
        includedProducer.send(test);
        excludedProducer.send(test);
        assertNull(excludedConsumer.receive(500));
        assertNotNull(includedConsumer.receive(500));
    }

    public void testConduitBridge() throws Exception{
        MessageConsumer consumer1=remoteSession.createConsumer(included);
        MessageConsumer consumer2=remoteSession.createConsumer(included);
        MessageProducer producer=localSession.createProducer(included);
        producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
        Thread.sleep(1000);
        for(int i=0;i<MESSAGE_COUNT;i++){
            Message test=localSession.createTextMessage("test-"+i);
            producer.send(test);
            assertNotNull(consumer1.receive(500));
            assertNotNull(consumer2.receive(500));
        }
        // ensure no more messages received
        assertNull(consumer1.receive(500));
        assertNull(consumer2.receive(500));
    }

    public void testDurableStoreAndForward() throws Exception{
        // create a remote durable consumer
        MessageConsumer remoteConsumer=remoteSession.createDurableSubscriber(included,consumerName);
        Thread.sleep(1000);
        // now close everything down and restart
        doTearDown();
        doSetUp();
        MessageProducer producer=localSession.createProducer(included);
        for(int i=0;i<MESSAGE_COUNT;i++){
            Message test=localSession.createTextMessage("test-"+i);
            producer.send(test);
        }
        Thread.sleep(1000);
        // close everything down and restart
        doTearDown();
        doSetUp();
        remoteConsumer=remoteSession.createDurableSubscriber(included,consumerName);
        for(int i=0;i<MESSAGE_COUNT;i++){
            Message test=localSession.createTextMessage("test-"+i);
            assertNotNull(remoteConsumer.receive(500));
        }
    }

    protected void setUp() throws Exception{
        super.setUp();
        doSetUp();
    }

    protected void tearDown() throws Exception{
        localBroker.deleteAllMessages();
        remoteBroker.deleteAllMessages();
        doTearDown();
        super.tearDown();
    }

    protected void doTearDown() throws Exception{
        localConnection.close();
        remoteConnection.close();
        localBroker.stop();
        remoteBroker.stop();
    }

    protected void doSetUp() throws Exception{
        Resource resource=new ClassPathResource("org/apache/activemq/network/localBroker.xml");
        BrokerFactoryBean factory=new BrokerFactoryBean(resource);
        factory.afterPropertiesSet();
        localBroker=factory.getBroker();
        resource=new ClassPathResource("org/apache/activemq/network/remoteBroker.xml");
        factory=new BrokerFactoryBean(resource);
        factory.afterPropertiesSet();
        remoteBroker=factory.getBroker();
        localBroker.start();
        remoteBroker.start();
        URI localURI=localBroker.getVmConnectorURI();
        ActiveMQConnectionFactory fac=new ActiveMQConnectionFactory(localURI);
        localConnection=fac.createConnection();
        localConnection.setClientID("local");
        localConnection.start();
        URI remoteURI=remoteBroker.getVmConnectorURI();
        fac=new ActiveMQConnectionFactory(remoteURI);
        remoteConnection=fac.createConnection();
        remoteConnection.setClientID("remote");
        remoteConnection.start();
        included=new ActiveMQTopic("include.test.bar");
        excluded=new ActiveMQTopic("exclude.test.bar");
        localSession=localConnection.createSession(false,Session.AUTO_ACKNOWLEDGE);
        remoteSession=remoteConnection.createSession(false,Session.AUTO_ACKNOWLEDGE);
    }
}
