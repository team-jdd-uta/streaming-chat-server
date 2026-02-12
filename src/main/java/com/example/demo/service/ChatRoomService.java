package com.example.demo.service;

import com.example.demo.model.ChatRoom;
import com.example.demo.pubsub.RedisSubscriber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private Map<String, ChannelTopic> topics; // 채팅방별 Topic 정보를 담아 Redis Listener에 등록

    @PostConstruct
    private void init() {
        opsHashChatRoom = redisTemplate.opsForHash();
        topics = new HashMap<>();
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
        ChannelTopic topic = topics.get(roomId);
        if (topic == null) {
            topic = new ChannelTopic(roomId);
            redisMessageListenerContainer.addMessageListener(redisSubscriber, topic);
            topics.put(roomId, topic);
            log.info("Subscribed to Redis topic: {}", roomId);
        }
    }

    /**
     * 채팅방 퇴장 시, Redis Topic 구독 해지 (선택적)
     * 실제 서비스에서는 구독 해지가 항상 필요한 것은 아니며,
     * 연결이 끊어지면 자동으로 RedisMessageListenerContainer에서 관리.
     */
    public void leaveChatRoom(String roomId) {
        ChannelTopic topic = topics.get(roomId);
        if (topic != null) {
            redisMessageListenerContainer.removeMessageListener(redisSubscriber, topic);
            topics.remove(roomId);
            log.info("Unsubscribed from Redis topic: {}", roomId);
        }
    }

    /**
     * 채팅방의 Topic 가져오기
     */
    public ChannelTopic getTopic(String roomId) {
        return topics.get(roomId);
    }
}
