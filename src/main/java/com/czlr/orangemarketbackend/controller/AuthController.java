package com.czlr.orangemarketbackend.controller;


import com.czlr.orangemarketbackend.common.Result;
import com.czlr.orangemarketbackend.entity.dto.*;
import com.czlr.orangemarketbackend.entity.po.UserAccount;
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

    @PostMapping("/sms/send")
    public Result<SmsInfo> sendSms(@RequestBody SmsRequest smsRequest){
        SmsInfo smsInfo = authService.sendSms(smsRequest);
        return Result.success(smsInfo);
    }

    @PostMapping("/login")
    public Result<LoginDTO> login(@RequestBody LoginRequest loginRequest){
        LoginDTO login = authService.login(loginRequest);
        return Result.success(login);
    }

    @DeleteMapping("/logout")
    public Result<Void> logout(@RequestAttribute("sessionId") String sessionId,
                               @RequestAttribute("userId") Long userId){
        authService.logout(sessionId,userId);
        return Result.success();
    }

    @GetMapping("/me")
    public Result<UserAccount> getMe(@RequestAttribute("sessionId") String sessionId,
                                     @RequestAttribute("userId") Long userId){
        UserAccount userInfo = authService.getMe(sessionId, userId);
        return Result.success(userInfo);
    }

}
