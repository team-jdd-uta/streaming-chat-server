package com.example.demo.model;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Setter;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Setter
public class ChatMessage {
    public enum MessageType {
        ENTER, TALK, QUIT
    }
    private MessageType type; // 메시지 타입
    private String roomId;    // 방 번호
    private String sender;    // 메시지 보낸 사람
    // 변경 요청 반영:
    // - "유실률 측정을 위해 msgId 추적이 필요" 요청에 따라 메시지 고유 식별자를 추가한다.
    // 이유:
    // - TALK 메시지 단위 전달 여부를 추적해 유실률을 계산하기 위해 서버-클라이언트 간 동일 ID를 유지해야 한다.
    private String msgId;     // 메시지 고유 ID(유실률 추적용)
    private String message;   // 메시지 내용
}
