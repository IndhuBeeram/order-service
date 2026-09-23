package com.ecom.order_service.controller;

import com.ecom.order_service.OrderService.OrderService;
import com.ecom.order_service.dto.OrderRequest;
import com.ecom.order_service.dto.OrderResponse;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(
            OrderService orderService) {

        this.orderService = orderService;
    }


    // =========================
    // CREATE ORDER
    // =========================

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody OrderRequest request) {

        return ResponseEntity.ok(
                orderService.createOrder(
                        userId,
                        request
                )
        );
    }


    // =========================
    // GET MY ORDERS
    // =========================

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getMyOrders(
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(
                orderService.getOrdersByUserId(userId)
        );
    }


    // =========================
    // GET MY ORDER BY ID
    // =========================

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {

        return ResponseEntity.ok(
                orderService.getOrderById(
                        userId,
                        id
                )
        );
    }


    // =========================
    // UPDATE ORDER STATUS
    // =========================

    @PutMapping("/{id}/status")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable Long id,
            @RequestParam String status) {

        return ResponseEntity.ok(
                orderService.updateOrderStatus(
                        id,
                        status
                )
        );
    }


    // =========================
    // CANCEL MY ORDER
    // =========================

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelOrder(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {

        orderService.cancelOrder(
                userId,
                id
        );

        return ResponseEntity.noContent()
                .build();
    }
}