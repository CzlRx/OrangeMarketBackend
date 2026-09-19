package com.czlr.orangemarketbackend.controller;

import com.czlr.orangemarketbackend.common.Result;
import com.czlr.orangemarketbackend.entity.dto.OssCallbackResultDTO;
import com.czlr.orangemarketbackend.entity.dto.OssSignDTO;
import com.czlr.orangemarketbackend.entity.dto.OssSignRequest;
import com.czlr.orangemarketbackend.service.oss.LocalImageUploadService;
import com.czlr.orangemarketbackend.service.oss.OssPostPolicyService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private final OssPostPolicyService ossPostPolicyService;
    private final LocalImageUploadService localImageUploadService;

    public UploadController(
            OssPostPolicyService ossPostPolicyService,
            LocalImageUploadService localImageUploadService) {
        this.ossPostPolicyService = ossPostPolicyService;
        this.localImageUploadService = localImageUploadService;
    }

    @PostMapping("/sign")
    public Result<OssSignDTO> sign(
            @RequestAttribute("userId") Long userId,
            @RequestBody OssSignRequest request) {
        return Result.success(ossPostPolicyService.sign(userId, request));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<OssCallbackResultDTO> upload(
            @RequestAttribute("userId") Long userId,
            @RequestParam("scene") String scene,
            @RequestParam("file") MultipartFile file) {
        return Result.success(localImageUploadService.upload(userId, scene, file));
    }
}
