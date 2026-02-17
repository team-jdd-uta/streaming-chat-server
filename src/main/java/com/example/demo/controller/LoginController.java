package com.example.demo.controller;

import com.example.demo.service.LoginService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoginController {

    LoginService loginService;

    public LoginController(LoginService loginService) {
        this.loginService = loginService;
    }

    @PostMapping("/login/{userId}/{password}")
    public boolean login(@PathVariable String userId, @PathVariable String password) {
        if (loginService.login(userId, password)) {
            return true;
        }
        return false;
    }

    @PostMapping("/signup/{userId}/{password}")
    public boolean signup(@PathVariable String userId, @PathVariable String password) {
        return loginService.signup(userId, password);
    }

    @PostMapping("/logout/{userId}")
    public boolean logout(@PathVariable String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("userId is required");
        }
        if (!loginService.logout(userId)) {
            throw new IllegalArgumentException("Failed to logout user: " + userId);
        }
        return true;
    }
}
