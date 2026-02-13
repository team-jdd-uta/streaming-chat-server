package com.example.demo.service;

import com.example.demo.model.ChatRoom;
import com.example.demo.pubsub.RedisSubscriber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@RequiredArgsConstructor
@Service
@Slf4j
public class ChatRoomService {
    // Redis
    private static final String CHAT_ROOMS = "CHAT_ROOM";
    private final RedisTemplate<String, Object> redisTemplate;
    private HashOperations<String, String, ChatRoom> opsHashChatRoom;

    // Redis 메시지 리스너 컨테이너와 발행자 (Publisher)
    private final RedisMessageListenerContainer redisMessageListenerContainer;
    private final RedisSubscriber redisSubscriber; // Redis Pub/Sub 메시지를 처리하는 구독자
    // roomId -> topic 상태(구독 토픽 + 로컬 참조 카운트 + 마지막 활동 시간)
    private Map<String, TopicState> topics;

    @Value("${chat.topic.cleanup.idle-threshold:60s}")
    // topic 정리 임계시간: 로컬 참조가 0이고 이 시간 동안 활동이 없으면 구독 해지
    private Duration topicCleanupIdleThreshold;

    @PostConstruct
    private void init() {
        opsHashChatRoom = redisTemplate.opsForHash();
        topics = new ConcurrentHashMap<>();
    }

    /**
     * 모든 채팅방 조회
     */
    public List<ChatRoom> findAllRoom() {
        return opsHashChatRoom.values(CHAT_ROOMS);
    }

    /**
     * 특정 채팅방 조회
     */
    public ChatRoom findRoomById(String roomId) {
        return opsHashChatRoom.get(CHAT_ROOMS, roomId);
    }

    /**
     * 채팅방 생성
     */
    public ChatRoom createChatRoom(String name) {
        ChatRoom chatRoom = ChatRoom.create(name);
        opsHashChatRoom.put(CHAT_ROOMS, chatRoom.getRoomId(), chatRoom);
        return chatRoom;
    }

    /**
     * 채팅방 입장 시, 해당 채팅방의 메시지를 받기 위한 Redis Topic 구독
     */
    public void enterChatRoom(String roomId) {
        topics.compute(roomId, (id, state) -> {
            // 첫 입장(room topic 미존재) 시에만 Redis listener를 실제 등록
            if (state == null) {
                ChannelTopic newTopic = new ChannelTopic(id);
                redisMessageListenerContainer.addMessageListener(redisSubscriber, newTopic);
                log.info("Subscribed to Redis topic: {}", id);
                state = new TopicState(newTopic);
            }
            // 동일 방에 로컬 사용자가 추가되었음을 카운트
            state.localRefCount.incrementAndGet();
            state.touch();
            return state;
        });
    }

    /**
     * 채팅방 퇴장 시 로컬 참조 카운트를 감소시킨다.
     * 바로 구독 해지하지 않고 스케줄러가 유예시간을 둔 뒤 정리한다.
     */
    public void leaveChatRoom(String roomId) {
        topics.computeIfPresent(roomId, (id, state) -> {
            // 중복 QUIT/비정상 흐름에서도 음수로 내려가지 않게 방어
            state.localRefCount.updateAndGet(current -> Math.max(0, current - 1));
            state.touch();
            return state;
        });
    }

    /**
     * 주기적으로 유휴 topic 정리:
     * - 로컬 참조 카운트가 0이고
     * - 마지막 활동 이후 일정 시간이 지난 경우
     */
    @Scheduled(fixedDelayString = "${chat.topic.cleanup.interval:30s}")
    public void cleanupIdleTopics() {
        Instant now = Instant.now();

        topics.forEach((roomId, state) -> {
            if (state.localRefCount.get() > 0) {
                return;
            }
            Instant lastTouchedAt = state.lastTouchedAt.get();
            if (lastTouchedAt.plus(topicCleanupIdleThreshold).isAfter(now)) {
                return;
            }

            // remove(roomId, state)로 CAS 제거하여 경쟁 상태에서 중복 해지를 방지
            if (topics.remove(roomId, state)) {
                redisMessageListenerContainer.removeMessageListener(redisSubscriber, state.topic);
                log.info("Unsubscribed idle Redis topic: {} (idle for {})", roomId, topicCleanupIdleThreshold);
            }
        });
    }

    /**
     * 채팅방의 Topic 가져오기
     */
    public ChannelTopic getTopic(String roomId) {
        TopicState state = topics.get(roomId);
        return state == null ? null : state.topic;
    }

    private static class TopicState {
        private final ChannelTopic topic;
        private final AtomicInteger localRefCount = new AtomicInteger(0);
        private final AtomicReference<Instant> lastTouchedAt = new AtomicReference<>(Instant.now());

        private TopicState(ChannelTopic topic) {
            this.topic = topic;
        }

        private void touch() {
            lastTouchedAt.set(Instant.now());
        }
    }
}
