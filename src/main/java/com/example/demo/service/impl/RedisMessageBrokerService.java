package com.example.demo.service.impl;

import com.example.demo.model.ChatMessage;
import com.example.demo.service.MessageBrokerService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class RedisMessageBrokerService implements MessageBrokerService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate streamStringRedisTemplate;
    // 변경: Stream 저장을 비동기 처리할 전용 실행기 추가
    // 이유: Pub/Sub 지연시간에 Stream XADD 네트워크 왕복이 직접 영향을 주지 않게 분리하기 위해
    private final ExecutorService streamAppendExecutor = Executors.newFixedThreadPool(
            2,
            new ThreadFactory() {
                private final AtomicInteger seq = new AtomicInteger();

                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "chat-stream-append-" + seq.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }
            }
    );

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
        // 변경: 실시간 전달(Pub/Sub)을 먼저 수행
        // 이유: 채팅 메시지 체감 지연을 낮추고, Stream 장애가 실시간 전달을 막지 않도록 하기 위해
        // TODO : 반대로 redis pub/sub이 죽으면 어떻게 되는지 해결
        redisTemplate.convertAndSend(topic, message);
        // 변경: Stream 저장(XADD)은 비동기로 분리
        // 이유: 저장 지연/장애를 실시간 브로드캐스트 경로와 격리하기 위해
        streamAppendExecutor.execute(() -> {
            try {
                appendToStream(topic, message);
            } catch (Exception e) {
                log.warn("Failed to append chat message to stream. topic={}", topic, e);
            }
        });
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
            // 변경 요청 반영:
            // - Stream에도 msgId를 저장해 메시지 단위 유실 추적/사후 분석이 가능하도록 한다.
            fields.put("msgId", safe(chatMessage.getMsgId()));
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

    @PreDestroy
    public void shutdownExecutor() {
        streamAppendExecutor.shutdown();
    }
}
