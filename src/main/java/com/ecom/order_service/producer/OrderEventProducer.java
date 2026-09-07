package com.ecom.order_service.producer;

import com.ecom.order_service.event.OrderCreatedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderEventProducer {

    private static final String ORDER_TOPIC = "orders";

    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    public OrderEventProducer(
            KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate) {

        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishOrderCreatedEvent(
            OrderCreatedEvent event) {

        kafkaTemplate.send(
                ORDER_TOPIC,
                String.valueOf(event.getOrderId()),
                event
        );
    }
}