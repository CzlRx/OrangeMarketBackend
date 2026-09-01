package com.czlr.orangemarketbackend.controller;


import com.czlr.orangemarketbackend.common.Result;
import com.czlr.orangemarketbackend.entity.dto.CaptchaDTO;
import com.czlr.orangemarketbackend.service.AuthService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService){
        this.authService = authService;
    }

    @GetMapping("/captcha")
    public Result<CaptchaDTO> getCaptcha(){
        CaptchaDTO captchaDTO = authService.getCaptcha();
        return Result.success(captchaDTO);
    }

}
