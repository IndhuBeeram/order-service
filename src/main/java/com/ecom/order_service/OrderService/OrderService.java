package com.ecom.order_service.OrderService;

import com.ecom.order_service.dto.OrderRequest;
import com.ecom.order_service.dto.OrderResponse;

import java.util.List;

public interface OrderService {

    OrderResponse createOrder(
            Long userId,
            OrderRequest request
    );

    OrderResponse getOrderById(
            Long userId,
            Long orderId
    );

    List<OrderResponse> getOrdersByUserId(
            Long userId
    );

    List<OrderResponse> getAllOrders();

    OrderResponse updateOrderStatus(
            Long id,
            String status
    );

    void cancelOrder(
            Long userId,
            Long orderId
    );
}