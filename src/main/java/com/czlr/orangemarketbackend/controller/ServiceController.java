package com.czlr.orangemarketbackend.controller;

import com.czlr.orangemarketbackend.common.Result;
import com.czlr.orangemarketbackend.entity.dto.ServiceMessagePageDTO;
import com.czlr.orangemarketbackend.entity.dto.ServiceSessionDTO;
import com.czlr.orangemarketbackend.service.ServiceSessionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/service")
public class ServiceController {

    private final ServiceSessionService serviceSessionService;

    public ServiceController(ServiceSessionService serviceSessionService) {
        this.serviceSessionService = serviceSessionService;
    }

    @PostMapping("/sessions")
    public Result<ServiceSessionDTO> createOrGet(
            @RequestAttribute("userId") Long userId) {
        return Result.success(serviceSessionService.createOrGetActive(userId));
    }

    @GetMapping("/sessions/current")
    public Result<ServiceSessionDTO> current(
            @RequestAttribute("userId") Long userId) {
        return Result.success(serviceSessionService.getCurrent(userId));
    }

    @GetMapping("/sessions/{sessionId}")
    public Result<ServiceSessionDTO> get(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long sessionId) {
        return Result.success(serviceSessionService.getForUser(userId, sessionId));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public Result<ServiceMessagePageDTO> messages(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long sessionId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return Result.success(serviceSessionService.listMessagesForUser(userId, sessionId, page, pageSize));
    }

    @PostMapping("/sessions/{sessionId}/close")
    public Result<Void> close(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long sessionId) {
        serviceSessionService.closeByUser(userId, sessionId);
        return Result.success();
    }
}
