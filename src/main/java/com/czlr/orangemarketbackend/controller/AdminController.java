package com.czlr.orangemarketbackend.controller;

import com.czlr.orangemarketbackend.common.Result;
import com.czlr.orangemarketbackend.entity.dto.AdminProductImagesRequest;
import com.czlr.orangemarketbackend.entity.dto.AdminShipmentDTO;
import com.czlr.orangemarketbackend.entity.dto.AdminUserStatusDTO;
import com.czlr.orangemarketbackend.entity.dto.OrderPageDTO;
import com.czlr.orangemarketbackend.entity.dto.ProductDTO;
import com.czlr.orangemarketbackend.entity.dto.ShipOrderRequest;
import com.czlr.orangemarketbackend.service.AdminService;
import com.czlr.orangemarketbackend.service.OrderService;
import org.apache.shiro.authz.annotation.Logical;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiresRoles(value = {"admin", "ADMIN"}, logical = Logical.OR)
public class AdminController {

    private final AdminService adminService;
    private final OrderService orderService;

    public AdminController(AdminService adminService, OrderService orderService) {
        this.adminService = adminService;
        this.orderService = orderService;
    }

    @GetMapping("/orders")
    public Result<OrderPageDTO> listOrders(
            @RequestParam(name = "status", defaultValue = "pending_shipment") String status,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize) {
        String queryStatus = status == null || status.isBlank() ? "pending_shipment" : status;
        return Result.success(orderService.listAllOrders(queryStatus, page, pageSize));
    }

    @PostMapping("/orders/{orderId}/ship")
    public Result<AdminShipmentDTO> shipOrder(
            @PathVariable Long orderId,
            @RequestBody ShipOrderRequest request) {
        return Result.success(adminService.shipOrder(orderId, request));
    }

    @PutMapping("/users/{userId}/ban")
    public Result<AdminUserStatusDTO> banUser(@PathVariable Long userId) {
        return Result.success(adminService.banUser(userId));
    }

    @PutMapping("/products/{productId}/images")
    public Result<ProductDTO> updateProductImages(
            @PathVariable Long productId,
            @RequestBody AdminProductImagesRequest request) {
        return Result.success(adminService.updateProductImages(productId, request));
    }
}
