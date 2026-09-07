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
import com.ecom.order_service.exception.*;
import com.ecom.order_service.repository.OrderRepository;
import feign.FeignException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ecom.order_service.event.OrderCreatedEvent;
import com.ecom.order_service.producer.OrderEventProducer;
import java.math.BigDecimal;
import java.util.List;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final OrderEventProducer orderEventProducer;
    public OrderServiceImpl(
            OrderRepository orderRepository,
            ProductClient productClient,
            OrderEventProducer orderEventProducer) {

        this.orderRepository = orderRepository;
        this.productClient = productClient;
        this.orderEventProducer = orderEventProducer;
    }

    // =========================================================
    // CREATE ORDER
    // =========================================================

    @Override
    @Transactional
    public OrderResponse createOrder(OrderRequest request) {

        Order order = new Order();

        order.setUserId(request.getUserId());

        // New orders always start with PENDING status
        order.setStatus(OrderStatus.PENDING);

        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderItemRequest itemRequest : request.getItems()) {

            // -------------------------------------------------
            // Get product details from Product Service
            // -------------------------------------------------

            ProductResponse product;

            try {

                product = productClient.getProductById(
                        itemRequest.getProductId()
                );

            } catch (FeignException.NotFound ex) {

                throw new ProductNotFoundException(
                        "Product not found with id: "
                                + itemRequest.getProductId()
                );
            }

            // -------------------------------------------------
            // Check whether product is active
            // -------------------------------------------------

            if (product.getActive() == null ||
                    !product.getActive()) {

                throw new ProductInactiveException(
                        "Product is not available: "
                                + itemRequest.getProductId()
                );
            }

            // -------------------------------------------------
            // Calculate subtotal
            // -------------------------------------------------

            BigDecimal subtotal =
                    product.getPrice()
                            .multiply(
                                    BigDecimal.valueOf(
                                            itemRequest.getQuantity()
                                    )
                            );

            // -------------------------------------------------
            // Create OrderItem
            // -------------------------------------------------

            OrderItem orderItem = new OrderItem();

            orderItem.setProductId(
                    itemRequest.getProductId()
            );

            orderItem.setQuantity(
                    itemRequest.getQuantity()
            );

            // Store price at the time of purchase
            orderItem.setPrice(
                    product.getPrice()
            );

            orderItem.setSubtotal(subtotal);

            // Connect OrderItem to Order
            orderItem.setOrder(order);

            order.getOrderItems().add(orderItem);

            // Add item subtotal to total order amount
            totalAmount =
                    totalAmount.add(subtotal);
        }

        // -----------------------------------------------------
        // Set final order total
        // -----------------------------------------------------

        order.setTotalAmount(totalAmount);

        // -----------------------------------------------------
        // Save Order
        // -----------------------------------------------------

        Order savedOrder =
                orderRepository.save(order);

// Create Kafka event
        OrderCreatedEvent event =
                new OrderCreatedEvent();

        event.setOrderId(savedOrder.getId());
        event.setUserId(savedOrder.getUserId());
        event.setTotalAmount(savedOrder.getTotalAmount());
        event.setOrderDate(savedOrder.getOrderDate());

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

        event.setItems(eventItems);

// Publish event to Kafka
        orderEventProducer.publishOrderCreatedEvent(event);

// Convert Entity → Response DTO
        return mapToResponse(savedOrder);
    }


    // =========================================================
    // GET ORDER BY ID
    // =========================================================

    @Override
    public OrderResponse getOrderById(Long id) {

        Order order =
                orderRepository.findById(id)
                        .orElseThrow(() ->
                                new OrderNotFoundException(
                                        "Order not found with id: "
                                                + id
                                )
                        );

        return mapToResponse(order);
    }


    // =========================================================
    // GET ALL ORDERS
    // =========================================================

    @Override
    public List<OrderResponse> getAllOrders() {

        return orderRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }


    // =========================================================
    // UPDATE ORDER STATUS
    // =========================================================

    @Override
    public OrderResponse updateOrderStatus(
            Long id,
            String status) {

        // -----------------------------------------------------
        // Find order
        // -----------------------------------------------------

        Order order =
                orderRepository.findById(id)
                        .orElseThrow(() ->
                                new OrderNotFoundException(
                                        "Order not found with id: "
                                                + id
                                )
                        );

        // -----------------------------------------------------
        // Get current status
        // -----------------------------------------------------

        OrderStatus currentStatus =
                order.getStatus();

        // -----------------------------------------------------
        // Convert request String → OrderStatus enum
        // -----------------------------------------------------

        OrderStatus newStatus;

        try {

            newStatus =
                    OrderStatus.valueOf(
                            status.toUpperCase()
                    );

        } catch (IllegalArgumentException ex) {

            throw new InvalidOrderStatusException(
                    "Invalid order status: " + status
            );
        }

        // -----------------------------------------------------
        // Validate status transition
        // -----------------------------------------------------

        if (!isValidStatusTransition(
                currentStatus,
                newStatus)) {

            throw new InvalidOrderStatusException(
                    "Invalid order status transition: "
                            + currentStatus
                            + " → "
                            + newStatus
            );
        }

        // -----------------------------------------------------
        // Update status
        // -----------------------------------------------------

        order.setStatus(newStatus);

        Order updatedOrder =
                orderRepository.save(order);

        return mapToResponse(updatedOrder);
    }


    // =========================================================
    // CANCEL ORDER
    // =========================================================

    @Override
    public void cancelOrder(Long id) {

        // -----------------------------------------------------
        // Find order
        // -----------------------------------------------------

        Order order =
                orderRepository.findById(id)
                        .orElseThrow(() ->
                                new OrderNotFoundException(
                                        "Order not found with id: "
                                                + id
                                )
                        );

        // -----------------------------------------------------
        // Get current status
        // -----------------------------------------------------

        OrderStatus currentStatus =
                order.getStatus();

        // -----------------------------------------------------
        // Cancellation is allowed only for PENDING orders
        // -----------------------------------------------------

        if (currentStatus != OrderStatus.PENDING) {

            throw new InvalidOrderStatusException(
                    "Order cannot be cancelled when current status is: "
                            + currentStatus
            );
        }

        // -----------------------------------------------------
        // Change status to CANCELLED
        // -----------------------------------------------------

        order.setStatus(OrderStatus.CANCELLED);

        orderRepository.save(order);
    }


    // =========================================================
    // VALIDATE ORDER STATUS TRANSITION
    // =========================================================

    private boolean isValidStatusTransition(
            OrderStatus currentStatus,
            OrderStatus newStatus) {

        return switch (currentStatus) {

            case PENDING ->
                    newStatus == OrderStatus.CONFIRMED
                            || newStatus == OrderStatus.CANCELLED;

            case CONFIRMED ->
                    newStatus == OrderStatus.SHIPPED;

            case SHIPPED ->
                    newStatus == OrderStatus.DELIVERED;

            case CANCELLED, DELIVERED ->
                    false;
        };
    }


    // =========================================================
    // MAP ORDER → ORDER RESPONSE
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

        // Enum → String for API response
        response.setStatus(
                order.getStatus().name()
        );

        response.setOrderDate(
                order.getOrderDate()
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
    // MAP ORDER ITEM → RESPONSE
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