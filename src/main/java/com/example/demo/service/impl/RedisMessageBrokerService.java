package com.example.demo.service.impl;

import com.example.demo.model.ChatMessage;
import com.example.demo.service.MessageBrokerService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class RedisMessageBrokerService implements MessageBrokerService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate streamStringRedisTemplate;

    @Value("${chat.stream.key-prefix:chat:stream:room:}")
    // 변경: Stream 키 접두사 설정 추가
    // 이유: roomId만으로 충돌/가독성 문제가 생기지 않도록 키 네이밍 규칙을 통일하기 위해
    private String streamKeyPrefix;

    public RedisMessageBrokerService(
            RedisTemplate<String, Object> redisTemplate,
            @Qualifier("streamStringRedisTemplate") StringRedisTemplate streamStringRedisTemplate
    ) {
        this.redisTemplate = redisTemplate;
        this.streamStringRedisTemplate = streamStringRedisTemplate;
    }

    @Override
    public void publish(String topic, Object message) {
        // 변경: Pub/Sub publish 전에 Stream에 먼저 XADD 수행
        // 이유: 실시간 전달(convertAndSend) 이전에 메시지 영속 흔적을 남겨 재처리/조회 가능성을 확보하기 위해
        // TODO : stream이 죽어있으면 publish 자체가 실패하는 문제 처리 필요
        // 재시도 로직 또는 장애 격리 방안 고민 필요
        appendToStream(topic, message);
        redisTemplate.convertAndSend(topic, message);
    }

    private void appendToStream(String topic, Object message) {
        // 변경: room(topic) 단위 Stream 키를 생성해 메시지 로그를 분리 저장
        // 이유: 채팅방별 이력 조회 및 운영 시 추적성을 높이기 위해
        String streamKey = streamKeyPrefix + topic;
        Map<String, String> fields = new LinkedHashMap<>();

        if (message instanceof ChatMessage chatMessage) {
            fields.put("type", chatMessage.getType() == null ? "" : chatMessage.getType().name());
            fields.put("roomId", safe(chatMessage.getRoomId()));
            fields.put("sender", safe(chatMessage.getSender()));
            fields.put("message", safe(chatMessage.getMessage()));
        } else {
            fields.put("payload", String.valueOf(message));
        }
        fields.put("publishedAt", String.valueOf(System.currentTimeMillis()));

        MapRecord<String, String, String> record = StreamRecords.mapBacked(fields).withStreamKey(streamKey);
        // 변경: Redis Stream에 XADD 실행
        // 이유: 기존 Pub/Sub(휘발성)만으로는 과거 메시지를 복구/재조회하기 어려워 보완하기 위해
        streamStringRedisTemplate.opsForStream().add(record);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
