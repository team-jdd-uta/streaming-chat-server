package com.example.demo.repository;

import com.example.demo.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, String> {

    // userId와 password로 고객 존재 여부 확인
    boolean existsByUserIdAndPassword(String userId, String password);

    Customer findByUserId(String userId);

    // userId와 password로 고객 조회
    Optional<Customer> findByUserIdAndPassword(String userId, String password);
}

