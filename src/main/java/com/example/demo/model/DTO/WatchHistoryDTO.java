package com.example.demo.model.DTO;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class WatchHistoryDTO {
    public int videoId;
    public String videoName;
    public String startedAt;
}
