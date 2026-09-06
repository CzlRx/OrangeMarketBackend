package com.czlr.orangemarketbackend.controller;

import com.czlr.orangemarketbackend.common.Result;
import com.czlr.orangemarketbackend.entity.dto.CancelOrderRequest;
import com.czlr.orangemarketbackend.entity.dto.CartOrderCreateRequest;
import com.czlr.orangemarketbackend.entity.dto.CartOrderPreviewRequest;
import com.czlr.orangemarketbackend.entity.dto.DirectOrderCreateRequest;
import com.czlr.orangemarketbackend.entity.dto.OrderCreateResultDTO;
import com.czlr.orangemarketbackend.entity.dto.OrderDTO;
import com.czlr.orangemarketbackend.entity.dto.OrderPageDTO;
import com.czlr.orangemarketbackend.entity.dto.OrderPreviewDTO;
import com.czlr.orangemarketbackend.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/cart/preview")
    public Result<OrderPreviewDTO> previewCart(
            @RequestAttribute("userId") Long userId,
            @RequestBody CartOrderPreviewRequest request) {
        return Result.success(orderService.previewCart(userId, request));
    }

    @PostMapping("/cart")
    public Result<OrderCreateResultDTO> createFromCart(
            @RequestAttribute("userId") Long userId,
            @RequestBody CartOrderCreateRequest request) {
        return Result.success(orderService.createFromCart(userId, request));
    }

    @PostMapping("/direct")
    public Result<OrderCreateResultDTO> createDirect(
            @RequestAttribute("userId") Long userId,
            @RequestBody DirectOrderCreateRequest request) {
        return Result.success(orderService.createDirect(userId, request));
    }

    @GetMapping
    public Result<OrderPageDTO> getOrders(
            @RequestAttribute("userId") Long userId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize) {
        return Result.success(orderService.getOrders(userId, status, page, pageSize));
    }

    @GetMapping("/{orderId}")
    public Result<OrderDTO> getOrder(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long orderId) {
        return Result.success(orderService.getOrder(userId, orderId));
    }

    @PostMapping("/{orderId}/cancel")
    public Result<Void> cancelOrder(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long orderId,
            @RequestBody CancelOrderRequest request) {
        orderService.cancelOrder(userId, orderId, request);
        return Result.success();
    }

    @PostMapping("/{orderId}/receive")
    public Result<Void> receiveOrder(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long orderId) {
        orderService.receiveOrder(userId, orderId);
        return Result.success();
    }
}
