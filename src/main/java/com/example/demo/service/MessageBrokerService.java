package com.example.demo.service;

public interface MessageBrokerService {
    /**
     * 특정 토픽(채널)으로 메시지를 발행(publish)한다.
     *
     * @param topic 발행할 토픽(채널)
     * @param message 발행할 메시지 객체
     */
    void publish(String topic, Object message);
}
