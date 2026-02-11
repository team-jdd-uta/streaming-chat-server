package com.example.demo.pubsub;

import com.example.demo.model.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import com.example.demo.service.LongPollingService;

@Slf4j
@RequiredArgsConstructor
@Service
public class RedisSubscriber implements MessageListener {

    private final RedisTemplate<String, Object> redisTemplate; // RedisTemplate을 주입받아 사용
    private final LongPollingService longPollingService;

    /**
     * Redis에서 메시지가 발행(publish)되면 대기하고 있던 onMessage가 해당 메시지를 받아 처리
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            // redis에서 발행된 데이터를 받아 deserialize
            ChatMessage chatMessage = (ChatMessage) redisTemplate.getValueSerializer().deserialize(message.getBody());

            if (chatMessage != null) {
                log.info("[REDIS] Message received from Redis - RoomId: {}, Sender: {}, Message: {}",
                        chatMessage.getRoomId(), chatMessage.getSender(), chatMessage.getMessage());
                // Long Polling 구독자에게 채팅 메시지 발송
                longPollingService.broadcast(chatMessage.getRoomId(), chatMessage);
            } else {
                log.warn("[REDIS] Received null message from Redis");
            }
        } catch (Exception e) {
            log.error("[REDIS] Error deserializing message or sending to Long Polling: {}", e.getMessage(), e);
        }
    }
}
