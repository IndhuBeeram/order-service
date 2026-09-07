package com.ecom.order_service.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class OrderCreatedEvent {

    private Long orderId;

    private Long userId;

    private BigDecimal totalAmount;

    private List<OrderCreatedItem> items;

    private LocalDateTime orderDate;

    public OrderCreatedEvent() {
    }

    public OrderCreatedEvent(
            Long orderId,
            Long userId,
            BigDecimal totalAmount,
            List<OrderCreatedItem> items,
            LocalDateTime orderDate) {

        this.orderId = orderId;
        this.userId = userId;
        this.totalAmount = totalAmount;
        this.items = items;
        this.orderDate = orderDate;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public List<OrderCreatedItem> getItems() {
        return items;
    }

    public void setItems(List<OrderCreatedItem> items) {
        this.items = items;
    }

    public LocalDateTime getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(LocalDateTime orderDate) {
        this.orderDate = orderDate;
    }


    // =========================================================
    // INNER CLASS — ORDER ITEM EVENT
    // =========================================================

    public static class OrderCreatedItem {

        private Long productId;

        private Integer quantity;

        private BigDecimal price;

        private BigDecimal subtotal;

        public OrderCreatedItem() {
        }

        public OrderCreatedItem(
                Long productId,
                Integer quantity,
                BigDecimal price,
                BigDecimal subtotal) {

            this.productId = productId;
            this.quantity = quantity;
            this.price = price;
            this.subtotal = subtotal;
        }

        public Long getProductId() {
            return productId;
        }

        public void setProductId(Long productId) {
            this.productId = productId;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public void setPrice(BigDecimal price) {
            this.price = price;
        }

        public BigDecimal getSubtotal() {
            return subtotal;
        }

        public void setSubtotal(BigDecimal subtotal) {
            this.subtotal = subtotal;
        }
    }
}