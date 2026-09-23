package com.ecom.order_service.dto;

import com.ecom.order_service.entity.PaymentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class OrderRequest {

    @NotEmpty(message = "Order must contain at least one item")
    private List<@Valid OrderItemRequest> items;

    @NotNull(message = "Payment type is required")
    private PaymentType paymentType;

    @NotNull(message = "Address is required")
    private Long addressId;


    public PaymentType getPaymentType() {
        return paymentType;
    }

    public void setPaymentType(PaymentType paymentType) {
        this.paymentType = paymentType;
    }

    public List<OrderItemRequest> getItems() {
        return items;
    }

    public void setItems(List<OrderItemRequest> items) {
        this.items = items;
    }

    public Long getAddressId() {
        return addressId;
    }

    public void setAddressId(Long addressId) {
        this.addressId = addressId;
    }
}