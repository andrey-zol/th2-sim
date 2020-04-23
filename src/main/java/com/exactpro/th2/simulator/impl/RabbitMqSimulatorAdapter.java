/******************************************************************************
 * Copyright 2009-2020 Exactpro (Exactpro Systems Limited)
 *
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
 ******************************************************************************/
package com.exactpro.th2.simulator.impl;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.exactpro.evolution.RabbitMqMessageSender;
import com.exactpro.evolution.RabbitMqSubscriber;
import com.exactpro.evolution.api.phase_1.ConnectivityGrpc;
import com.exactpro.evolution.api.phase_1.ConnectivityGrpc.ConnectivityBlockingStub;
import com.exactpro.evolution.api.phase_1.Message;
import com.exactpro.evolution.api.phase_1.QueueInfo;
import com.exactpro.evolution.api.phase_1.QueueRequest;
import com.exactpro.evolution.configuration.RabbitMQConfiguration;
import com.exactpro.evolution.configuration.Th2Configuration.Address;
import com.exactpro.th2.simulator.IServiceSimulator;
import com.exactpro.th2.simulator.configuration.SimulatorConfiguration;
import com.google.protobuf.InvalidProtocolBufferException;
import com.rabbitmq.client.Delivery;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class RabbitMqSimulatorAdapter implements AutoCloseable {

    private final Logger logger = LoggerFactory.getLogger(this.getClass() + "@" + this.hashCode());

    private final IServiceSimulator simulator;
    private final SimulatorConfiguration configuration;
    private final RabbitMqSubscriber subscriber;
    private final RabbitMqMessageSender sender;
    //private final Map<String, RabbitMqMessageSender> senders = new HashMap<>();

    public RabbitMqSimulatorAdapter(IServiceSimulator simulator, SimulatorConfiguration configuration) {
        this.simulator = Objects.requireNonNull(simulator, "Simulator can not be null");
        this.configuration = Objects.requireNonNull(configuration, "Configuration can not be null");

        QueueInfo queueInfo = getQueueInfo(configuration);

        subscriber = new RabbitMqSubscriber(queueInfo.getExchangeName(),
                this::processIncomingMessage,
                null,
                queueInfo.getInMsgQueue());

        sender = new RabbitMqMessageSender(configuration.getRabbitMQ(), configuration.getConnectivityID(), queueInfo.getExchangeName(), queueInfo.getSendMsgQueue());
    }

    public void start() {
        if (subscriber != null && configuration != null && configuration.getRabbitMQ() != null) {
            RabbitMQConfiguration rabbitConf = configuration.getRabbitMQ();
            try {
                subscriber.startListening(rabbitConf.getHost(),
                        rabbitConf.getVirtualHost(),
                        rabbitConf.getPort(),
                        rabbitConf.getUsername(),
                        rabbitConf.getPassword());
            } catch (IOException | TimeoutException e) {
                throw new IllegalStateException("Can not start listening rabbit mq", e);
            }
        }
    }

    @Override
    public void close() throws Exception {
        if (subscriber != null) {
            try {
                subscriber.close();
            } catch (Exception e) {
                logger.error("Can not close rabbit mq subscriber", e);
            }
        }
        if (sender != null) {
            try {
                sender.close();
            } catch (Exception e) {
                logger.error("Can not close rabbit mq sender", e);
            }
        }
    }

    public void processIncomingMessage(String consumingTag, Delivery delivery) {
        try {

            if (sender == null) {
                logger.error("Can not process message, because sender did not init");
                return;
            }

            Message message = Message.parseFrom(delivery.getBody());
            if (message == null) {
                return;
            }

            //FIXME: move to debug section
            logger.info("Handle message name = " + message.getMetadata().getMessageType());

            logger.trace("Handle message body = " + message.toString());

            for (Message messageToSend : simulator.handle(message)) {
                try {
                    sender.send(messageToSend);
                } catch (Exception e) {
                    logger.error("Can not send message: " + messageToSend.toString(), e);
                }
            }
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
    }

    private QueueInfo getQueueInfo(SimulatorConfiguration configuration) {
        Address connectivityAddress = configuration.getTh2().getConnectivityAddresses().get(configuration.getConnectivityID());
        QueueInfo queueInfo = null;
        if (connectivityAddress != null) {
            queueInfo =  downloadQueueInfo(connectivityAddress);
        }

        return queueInfo != null ? queueInfo :
                QueueInfo
                        .newBuilder()
                        .setExchangeName(Objects.requireNonNull(configuration.getExchangeName(), "Can not get exchange name"))
                        .setInMsgQueue(Objects.requireNonNull(configuration.getInMsgQueue(), "Can not get in message queue"))
                        .setSendMsgQueue(Objects.requireNonNull(configuration.getSendMsgQueue(), "Can not get send message queue"))
                        .build();
    }

    private QueueInfo downloadQueueInfo(Address connectivityAddress) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(connectivityAddress.getHost(), connectivityAddress.getPort()).usePlaintext().build();
        try {
            ConnectivityBlockingStub blockingStub = ConnectivityGrpc.newBlockingStub(channel);
            return blockingStub.getQueueInfo(QueueRequest.newBuilder().build());
        } catch (Exception e) {
            logger.error("Can not download queue info");
            return null;
        }
        finally {
            if (!channel.isShutdown()) {
                channel.shutdownNow();
            }
        }
    }

}
