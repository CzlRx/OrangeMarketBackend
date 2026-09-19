package com.czlr.orangemarketbackend.controller;

import com.czlr.orangemarketbackend.common.Result;
import com.czlr.orangemarketbackend.entity.dto.OssSignDTO;
import com.czlr.orangemarketbackend.entity.dto.OssSignRequest;
import com.czlr.orangemarketbackend.service.oss.OssPostPolicyService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private final OssPostPolicyService ossPostPolicyService;

    public UploadController(OssPostPolicyService ossPostPolicyService) {
        this.ossPostPolicyService = ossPostPolicyService;
    }

    @PostMapping("/sign")
    public Result<OssSignDTO> sign(
            @RequestAttribute("userId") Long userId,
            @RequestBody OssSignRequest request) {
        return Result.success(ossPostPolicyService.sign(userId, request));
    }
}
