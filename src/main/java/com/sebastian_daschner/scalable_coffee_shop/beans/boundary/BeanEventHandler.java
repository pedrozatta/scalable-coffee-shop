package com.sebastian_daschner.scalable_coffee_shop.beans.boundary;

import com.sebastian_daschner.scalable_coffee_shop.barista.entity.CoffeeBrewStarted;
import com.sebastian_daschner.scalable_coffee_shop.events.entity.AbstractEvent;
import com.sebastian_daschner.scalable_coffee_shop.events.entity.HandledBy;
import com.sebastian_daschner.scalable_coffee_shop.orders.entity.OrderPlaced;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.enterprise.concurrent.ManagedExecutorService;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.util.Properties;
import java.util.logging.Logger;

import static com.sebastian_daschner.scalable_coffee_shop.events.entity.HandledBy.Group.BEANS_HANDLER;
import static java.util.Arrays.asList;

@Singleton
@Startup
public class BeanEventHandler {

    @Resource
    ManagedExecutorService mes;

    @Inject
    @HandledBy(BEANS_HANDLER)
    Event<AbstractEvent> events;

    @Inject
    Properties kafkaProperties;

    @Inject
    BeanService beanService;

    @Inject
    Logger logger;

    public void handle(@Observes @HandledBy(BEANS_HANDLER) OrderPlaced event) {
        beanService.validateBeans(event.getOrderInfo().getBeanOrigin(), event.getOrderInfo().getOrderId());
    }

    public void handle(@Observes @HandledBy(BEANS_HANDLER) CoffeeBrewStarted event) {
        beanService.fetchBeans(event.getOrderInfo().getBeanOrigin());
    }

    @PostConstruct
    private void initConsumer() {
        kafkaProperties.put("group.id", "beans-handler");

        KafkaConsumer<String, AbstractEvent> consumer = new KafkaConsumer<>(kafkaProperties);
        consumer.subscribe(asList("order", "barista"));

        mes.execute(() -> consumeEvent(consumer));
    }

    private void consumeEvent(final KafkaConsumer<String, AbstractEvent> consumer) {
        ConsumerRecords<String, AbstractEvent> records = consumer.poll(Long.MAX_VALUE);
        for (ConsumerRecord<String, AbstractEvent> record : records) {
            logger.info("firing = " + record.value());
            events.fire(record.value());
        }
        consumer.commitSync();
        mes.execute(() -> consumeEvent(consumer));
    }

}