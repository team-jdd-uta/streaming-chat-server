package com.example.demo.service;

import com.example.demo.repository.CustomerRepository;
import org.springframework.stereotype.Service;

@Service
public class LoginService {
    CustomerRepository customerRepository;

    public LoginService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public boolean login(String userId, String password) {
        return customerRepository.existsByUserIdAndPassword(userId, password);
    }

    public boolean logout(String userId) {
        // 유저 아이디가 DB에 존재하는지 확인
        if (!customerRepository.existsByUserId(userId)) {
            throw new IllegalArgumentException("User not found: " + userId);
        }
        return true;
    }
}
