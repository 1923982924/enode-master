package com.qianzhui.enode.rocketmq.applicationmessage;

import com.alibaba.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import com.alibaba.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import com.alibaba.rocketmq.common.message.MessageExt;
import com.qianzhui.enode.common.container.GenericTypeLiteral;
import com.qianzhui.enode.common.container.ObjectContainer;
import com.qianzhui.enode.common.logging.ILogger;
import com.qianzhui.enode.common.logging.ILoggerFactory;
import com.qianzhui.enode.common.serializing.IJsonSerializer;
import com.qianzhui.enode.common.utilities.BitConverter;
import com.qianzhui.enode.infrastructure.IApplicationMessage;
import com.qianzhui.enode.infrastructure.IMessageProcessor;
import com.qianzhui.enode.infrastructure.ITypeNameProvider;
import com.qianzhui.enode.infrastructure.ProcessingApplicationMessage;
import com.qianzhui.enode.rocketmq.RocketMQConsumer;
import com.qianzhui.enode.rocketmq.RocketMQMessageHandler;
import com.qianzhui.enode.rocketmq.RocketMQProcessContext;

import javax.inject.Inject;

/**
 * Created by junbo_xu on 2016/4/6.
 */
public class ApplicationMessageConsumer {

    private final RocketMQConsumer _consumer;
    private final IJsonSerializer _jsonSerializer;
    private final ITypeNameProvider _typeNameProvider;
    private final IMessageProcessor<ProcessingApplicationMessage, IApplicationMessage, Boolean> _processor;
    private final ILogger _logger;

    @Inject
    public ApplicationMessageConsumer(RocketMQConsumer consumer, IJsonSerializer jsonSerializer, ITypeNameProvider typeNameProvider,
                                      IMessageProcessor<ProcessingApplicationMessage, IApplicationMessage, Boolean> processor,
                                      ILoggerFactory loggerFactory) {
        _consumer = consumer;
        _jsonSerializer = jsonSerializer;
        _typeNameProvider = typeNameProvider;
        _processor = processor;
        _logger = loggerFactory.create(getClass());
    }

    public ApplicationMessageConsumer(RocketMQConsumer consumer) {
        _consumer = consumer;
        _jsonSerializer = ObjectContainer.resolve(IJsonSerializer.class);
        _processor = ObjectContainer.resolve(new GenericTypeLiteral<IMessageProcessor<ProcessingApplicationMessage, IApplicationMessage, Boolean>>() {
        });
        _typeNameProvider = ObjectContainer.resolve(ITypeNameProvider.class);
        _logger = ObjectContainer.resolve(ILoggerFactory.class).create(getClass());
    }

    public ApplicationMessageConsumer start() {
        _consumer.registerMessageHandler(new RocketMQMessageHandler() {
            @Override
            public boolean isMatched(String messageTags) {
                try {
                    Class type = _typeNameProvider.getType(messageTags);
                    return IApplicationMessage.class.isAssignableFrom(type);
                } catch (Exception e) {
                    return false;
                }
            }

            @Override
            public ConsumeConcurrentlyStatus handle(MessageExt message, ConsumeConcurrentlyContext context) {
                return ApplicationMessageConsumer.this.handle(message, context);
            }
        });
        return this;
    }

    public ApplicationMessageConsumer shutdown() {
        return this;
    }

    ConsumeConcurrentlyStatus handle(final MessageExt msg, final ConsumeConcurrentlyContext context) {
        Class applicationMessageType = _typeNameProvider.getType(msg.getTags());
        IApplicationMessage message = (IApplicationMessage) _jsonSerializer.deserialize(BitConverter.toString(msg.getBody()), applicationMessageType);
        RocketMQProcessContext processContext = new RocketMQProcessContext(msg, context);
        ProcessingApplicationMessage processingMessage = new ProcessingApplicationMessage(message, processContext);
        _processor.process(processingMessage);

        //TODO consume status ack
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }

    public RocketMQConsumer getConsumer() {
        return _consumer;
    }
}