package com.czlr.orangemarketbackend.controller;

import com.czlr.orangemarketbackend.common.Result;
import com.czlr.orangemarketbackend.entity.dto.ServiceMessagePageDTO;
import com.czlr.orangemarketbackend.entity.dto.ServiceSessionDTO;
import com.czlr.orangemarketbackend.entity.dto.ServiceSessionPageDTO;
import com.czlr.orangemarketbackend.service.ServiceSessionService;
import org.apache.shiro.authz.annotation.Logical;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/service")
@RequiresRoles(value = {"admin", "ADMIN"}, logical = Logical.OR)
public class AdminServiceController {

    private final ServiceSessionService serviceSessionService;

    public AdminServiceController(ServiceSessionService serviceSessionService) {
        this.serviceSessionService = serviceSessionService;
    }

    @GetMapping("/sessions/lobby")
    public Result<ServiceSessionPageDTO> lobby(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return Result.success(serviceSessionService.listLobby(page, pageSize));
    }

    @GetMapping("/sessions/mine")
    public Result<ServiceSessionPageDTO> mine(
            @RequestAttribute("userId") Long userId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return Result.success(serviceSessionService.listMine(userId, page, pageSize));
    }

    @PostMapping("/sessions/{sessionId}/claim")
    public Result<ServiceSessionDTO> claim(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long sessionId) {
        return Result.success(serviceSessionService.claim(userId, sessionId));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public Result<ServiceMessagePageDTO> messages(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long sessionId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return Result.success(serviceSessionService.listMessagesForAgent(userId, sessionId, page, pageSize));
    }

    @PostMapping("/sessions/{sessionId}/close")
    public Result<Void> close(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long sessionId) {
        serviceSessionService.closeByAgent(userId, sessionId);
        return Result.success();
    }
}
