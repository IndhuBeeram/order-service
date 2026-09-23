package com.ecom.order_service.OrderService;

import com.ecom.order_service.client.ProductClient;
import com.ecom.order_service.dto.OrderItemRequest;
import com.ecom.order_service.dto.OrderItemResponse;
import com.ecom.order_service.dto.OrderRequest;
import com.ecom.order_service.dto.OrderResponse;
import com.ecom.order_service.dto.ProductResponse;
import com.ecom.order_service.entity.Order;
import com.ecom.order_service.entity.OrderItem;
import com.ecom.order_service.entity.OrderStatus;
import com.ecom.order_service.event.OrderCreatedEvent;
import com.ecom.order_service.exception.InvalidOrderStatusException;
import com.ecom.order_service.exception.OrderNotFoundException;
import com.ecom.order_service.repository.OrderRepository;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    private static final String ORDER_CREATED_TOPIC = "order-created";

    public OrderServiceImpl(
            OrderRepository orderRepository,
            ProductClient productClient,
            KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate) {

        this.orderRepository = orderRepository;
        this.productClient = productClient;
        this.kafkaTemplate = kafkaTemplate;
    }

    // =========================================================
    // CREATE ORDER
    // =========================================================

    @Override
    @Transactional
    public OrderResponse createOrder(
            Long userId,
            OrderRequest request) {

        Order order = new Order();

        // User ID comes from X-User-Id header
        order.setUserId(userId);
        order.setAddressId(request.getAddressId());
        order.setPaymentType(request.getPaymentType());


        // New order starts as PENDING
        order.setStatus(OrderStatus.PENDING);

        BigDecimal totalAmount = BigDecimal.ZERO;

        // =====================================================
        // CREATE ORDER ITEMS
        // =====================================================

        for (OrderItemRequest itemRequest : request.getItems()) {

            // Get product from Product Service
            ProductResponse product =
                    productClient.getProductById(
                            itemRequest.getProductId()
                    );

            // Product not found
            if (product == null) {

                throw new RuntimeException(
                        "Product not found with id: "
                                + itemRequest.getProductId()
                );
            }

            // Product must be active
           if (!Boolean.TRUE.equals(product.getActive())) {

                throw new RuntimeException(
                        "Product is not active with id: "
                                + itemRequest.getProductId()
                );
            }

            // =================================================
            // CREATE ORDER ITEM
            // =================================================

            OrderItem orderItem = new OrderItem();

            orderItem.setProductId(product.getId());

            orderItem.setQuantity(
                    itemRequest.getQuantity()
            );

            // Get current product price
            BigDecimal price = product.getPrice();

            orderItem.setPrice(price);

            // Calculate subtotal
            BigDecimal subtotal =
                    price.multiply(
                            BigDecimal.valueOf(
                                    itemRequest.getQuantity()
                            )
                    );

            orderItem.setSubtotal(subtotal);

            // Connect OrderItem to Order
            orderItem.setOrder(order);

            order.getOrderItems().add(orderItem);

            // Add to order total
            totalAmount = totalAmount.add(subtotal);
        }

        order.setTotalAmount(totalAmount);

        // =====================================================
        // SAVE ORDER
        // =====================================================

        Order savedOrder =
                orderRepository.save(order);

        // =====================================================
        // CREATE KAFKA EVENT ITEMS
        // =====================================================

        List<OrderCreatedEvent.OrderCreatedItem> eventItems =
                savedOrder.getOrderItems()
                        .stream()
                        .map(item ->
                                new OrderCreatedEvent.OrderCreatedItem(
                                        item.getProductId(),
                                        item.getQuantity(),
                                        item.getPrice(),
                                        item.getSubtotal()
                                )
                        )
                        .toList();

        // =====================================================
        // CREATE KAFKA EVENT
        // =====================================================

        OrderCreatedEvent event =
                new OrderCreatedEvent(
                        savedOrder.getId(),
                        savedOrder.getUserId(),
                        savedOrder.getTotalAmount(),
                        eventItems,
                        savedOrder.getOrderDate()
                );

        // Your Event class has paymentType as a field,
        // but it is not included in the constructor.
        event.setPaymentType(
                savedOrder.getPaymentType()
        );

        // =====================================================
        // PUBLISH EVENT TO KAFKA
        // =====================================================

        kafkaTemplate.send(
                ORDER_CREATED_TOPIC,
                String.valueOf(savedOrder.getId()),
                event
        );

        return mapToResponse(savedOrder);
    }


    // =========================================================
    // GET ORDER BY ID
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(
            Long userId,
            Long orderId) {

        Order order =
                orderRepository.findById(orderId)
                        .orElseThrow(() ->
                                new OrderNotFoundException(
                                        "Order not found with id: "
                                                + orderId
                                )
                        );

        // User can access only their own order
        if (!order.getUserId().equals(userId)) {

            throw new OrderNotFoundException(
                    "Order not found with id: "
                            + orderId
            );
        }

        return mapToResponse(order);
    }


    // =========================================================
    // GET ORDERS BY USER ID
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByUserId(
            Long userId) {

        return orderRepository
                .findByUserId(userId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }


    // =========================================================
    // GET ALL ORDERS
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {

        return orderRepository
                .findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }


    // =========================================================
    // UPDATE ORDER STATUS
    // =========================================================

    @Override
    @Transactional
    public OrderResponse updateOrderStatus(
            Long id,
            String status) {

        Order order =
                orderRepository.findById(id)
                        .orElseThrow(() ->
                                new OrderNotFoundException(
                                        "Order not found with id: "
                                                + id
                                )
                        );

        OrderStatus newStatus;

        try {

            newStatus =
                    OrderStatus.valueOf(
                            status.toUpperCase()
                    );

        } catch (IllegalArgumentException e) {

            throw new InvalidOrderStatusException(
                    "Invalid order status: "
                            + status
            );
        }

        OrderStatus currentStatus =
                order.getStatus();

        boolean validTransition = false;

        // =====================================================
        // STATUS TRANSITIONS
        // =====================================================

        switch (currentStatus) {

            case PENDING:

                if (newStatus == OrderStatus.CONFIRMED
                        || newStatus == OrderStatus.CANCELLED) {

                    validTransition = true;
                }

                break;

            case CONFIRMED:

                if (newStatus == OrderStatus.SHIPPED) {

                    validTransition = true;
                }

                break;

            case SHIPPED:

                if (newStatus == OrderStatus.DELIVERED) {

                    validTransition = true;
                }

                break;

            case CANCELLED:
            case DELIVERED:

                validTransition = false;

                break;
        }

        if (!validTransition) {

            throw new InvalidOrderStatusException(
                    "Cannot change order status from "
                            + currentStatus
                            + " to "
                            + newStatus
            );
        }

        order.setStatus(newStatus);

        Order updatedOrder =
                orderRepository.save(order);

        return mapToResponse(updatedOrder);
    }


    // =========================================================
    // CANCEL ORDER
    // =========================================================

    @Override
    @Transactional
    public void cancelOrder(
            Long userId,
            Long orderId) {

        Order order =
                orderRepository.findById(orderId)
                        .orElseThrow(() ->
                                new OrderNotFoundException(
                                        "Order not found with id: "
                                                + orderId
                                )
                        );

        // User can cancel only their own order
        if (!order.getUserId().equals(userId)) {

            throw new OrderNotFoundException(
                    "Order not found with id: "
                            + orderId
            );
        }

        // Only PENDING orders can be cancelled
        if (order.getStatus()
                != OrderStatus.PENDING) {

            throw new InvalidOrderStatusException(
                    "Only PENDING orders can be cancelled"
            );
        }

        order.setStatus(
                OrderStatus.CANCELLED
        );

        orderRepository.save(order);
    }


    // =========================================================
    // ENTITY → ORDER RESPONSE
    // =========================================================

    private OrderResponse mapToResponse(
            Order order) {

        OrderResponse response =
                new OrderResponse();

        response.setId(
                order.getId()
        );

        response.setUserId(
                order.getUserId()
        );

        response.setTotalAmount(
                order.getTotalAmount()
        );

        /*
         * Your OrderResponse.setStatus() expects String,
         * while Order entity contains OrderStatus.
         */
        response.setStatus(
                order.getStatus().name()
        );

        response.setOrderDate(
                order.getOrderDate()
        );

        response.setPaymentType(
                order.getPaymentType()
        );

        List<OrderItemResponse> itemResponses =
                order.getOrderItems()
                        .stream()
                        .map(this::mapItemToResponse)
                        .toList();

        response.setItems(
                itemResponses
        );

        return response;
    }


    // =========================================================
    // ORDER ITEM → ORDER ITEM RESPONSE
    // =========================================================

    private OrderItemResponse mapItemToResponse(
            OrderItem item) {

        OrderItemResponse response =
                new OrderItemResponse();

        response.setId(
                item.getId()
        );

        response.setProductId(
                item.getProductId()
        );

        response.setQuantity(
                item.getQuantity()
        );

        response.setPrice(
                item.getPrice()
        );

        response.setSubtotal(
                item.getSubtotal()
        );

        return response;
    }
}