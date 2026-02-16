package com.example.demo.model.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Data
@Getter
@Setter
public class WatchHistoryDTO {
    @JsonProperty("streamerId")
    public Long videoId;
    @JsonProperty("user_id")
    public String userId;
    public String videoName;
    @JsonProperty("started_at")
    public LocalDateTime startedAt;
    @JsonProperty("ended_at")
    public LocalDateTime endedAt;
}
