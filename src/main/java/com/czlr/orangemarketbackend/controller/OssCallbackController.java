package com.czlr.orangemarketbackend.controller;

import com.czlr.orangemarketbackend.common.Result;
import com.czlr.orangemarketbackend.entity.dto.OssCallbackResultDTO;
import com.czlr.orangemarketbackend.service.oss.OssCallbackService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/oss")
public class OssCallbackController {

    private final OssCallbackService ossCallbackService;

    public OssCallbackController(OssCallbackService ossCallbackService) {
        this.ossCallbackService = ossCallbackService;
    }

    @PostMapping("/callback")
    public Result<OssCallbackResultDTO> callback(HttpServletRequest request) {
        return Result.success(ossCallbackService.handle(request));
    }
}
