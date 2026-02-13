package com.example.demo.model.DTO;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserInfoDTO {
    public String userId;
    public String userName;
    public int followers;
    public int following;
    public int streams; //내가 스트리밍한 방송 수
}
