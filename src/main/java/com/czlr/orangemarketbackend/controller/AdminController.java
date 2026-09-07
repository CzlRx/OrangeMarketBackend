package com.czlr.orangemarketbackend.controller;

import com.czlr.orangemarketbackend.common.Result;
import com.czlr.orangemarketbackend.entity.dto.AdminShipmentDTO;
import com.czlr.orangemarketbackend.entity.dto.AdminUserStatusDTO;
import com.czlr.orangemarketbackend.entity.dto.ShipOrderRequest;
import com.czlr.orangemarketbackend.service.AdminService;
import org.apache.shiro.authz.annotation.Logical;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiresRoles(value = {"admin", "ADMIN"}, logical = Logical.OR)
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
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
}
