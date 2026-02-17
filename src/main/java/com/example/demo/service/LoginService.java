package com.example.demo.service;

import com.example.demo.entity.Customer;
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

    public boolean signup(String userId, String password) {
        if (isBlank(userId)) {
            throw new IllegalArgumentException("userId is required");
        }
        if (isBlank(password)) {
            throw new IllegalArgumentException("password is required");
        }

        String normalizedUserId = userId.trim();
        String normalizedPassword = password.trim();
        if (customerRepository.existsByUserId(normalizedUserId)) {
            throw new IllegalArgumentException("User already exists: " + normalizedUserId);
        }

        Customer customer = Customer.builder()
                .userId(normalizedUserId)
                .password(normalizedPassword)
                .build();
        customerRepository.save(customer);
        return true;
    }

    public boolean logout(String userId) {
        // 유저 아이디가 DB에 존재하는지 확인
        if (!customerRepository.existsByUserId(userId)) {
            throw new IllegalArgumentException("User not found: " + userId);
        }
        return true;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
