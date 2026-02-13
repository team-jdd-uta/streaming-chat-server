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
}
