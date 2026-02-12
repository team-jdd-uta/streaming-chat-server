package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "follows")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Follows {

    @Id
    @Column(name = "following_user_id", length = 50)
    private String followingUserId;

    @Id
    @Column(name = "followed_user_id", length = 50)
    private String followedUserId;

    @Column(nullable = false)
    private LocalDateTime followedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "following_user_id", insertable = false, updatable = false)
    private Customer followingUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "followed_user_id", insertable = false, updatable = false)
    private Customer followedUser;

    @PrePersist
    protected void onCreate() {
        if (followedAt == null) {
            followedAt = LocalDateTime.now();
        }
    }
}

