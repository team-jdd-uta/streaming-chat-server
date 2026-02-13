package com.example.demo.entity;

import lombok.*;
import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class FollowsId implements Serializable {

    private String followingUserId;
    private String followedUserId;
}

