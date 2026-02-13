package com.example.demo.controller;

import com.example.demo.service.LoginService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class LoginController {

    LoginService loginService;
    public LoginController (LoginService loginService) {
        this.loginService = loginService;
    }

    @PostMapping("/login/{userId}/{password}")
    public boolean login(@PathVariable String userId, @PathVariable String password) {
        if(loginService.login(userId, password)) {
            return true;
        }
        return false;
    }
}
