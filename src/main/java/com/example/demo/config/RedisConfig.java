package com.example.demo.config;

import io.lettuce.core.SocketOptions;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;

@Configuration
public class RedisConfig {

    @Value("${spring.redis.cluster.nodes}")
    private List<String> redisNodes;

    @Value("${chat.stream.redis.host:localhost}")
    // 변경: Stream 저장용 Redis(6379) 호스트를 별도 설정으로 분리
    // 이유: 기존 Redis Cluster Pub/Sub 설정을 건드리지 않고 독립적으로 Stream 대상 서버를 지정하기 위해
    private String streamRedisHost;

    @Value("${chat.stream.redis.port:6379}")
    // 변경: Stream 저장용 Redis 포트를 설정값으로 주입 (기본 6379)
    // 이유: 환경별 포트 차이를 코드 수정 없이 설정 파일로 제어하기 위해
    private int streamRedisPort;
    @Value("${chat.redis.listener.task-executor.core-pool-size:8}")
    private int redisListenerCorePoolSize;
    @Value("${chat.redis.listener.task-executor.max-pool-size:64}")
    private int redisListenerMaxPoolSize;
    @Value("${chat.redis.listener.task-executor.queue-capacity:20000}")
    private int redisListenerQueueCapacity;
    @Value("${chat.redis.listener.subscription-executor.core-pool-size:4}")
    private int redisSubscriptionCorePoolSize;
    @Value("${chat.redis.listener.subscription-executor.max-pool-size:16}")
    private int redisSubscriptionMaxPoolSize;
    @Value("${chat.redis.listener.subscription-executor.queue-capacity:1000}")
    private int redisSubscriptionQueueCapacity;

    @Bean(destroyMethod = "shutdown")
    public ClientResources clientResources() {
        // 변경 요청 반영(도커 환경 전용):
        // - Redis Cluster에서 광고하는 redis-node-* 호스트명을 그대로 사용한다.
        // 요청 배경:
        // - "도커에서만 동작하게 해줘도 될 거 같은데" 요청에 맞춰 localhost 강제 치환 로직을 제거했다.
        return DefaultClientResources.create();
    }


    /**
     * redis와 상호작용하는 가장 낮은 수준의 객체
     * Redis 서버와 연결을 관리하며, 연결이 끊어지면 다시 연결을 시도
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            @Qualifier("redisConnectionFactory") RedisConnectionFactory connectionFactory,
            @Qualifier("redisListenerTaskExecutor") ThreadPoolTaskExecutor redisListenerTaskExecutor,
            @Qualifier("redisSubscriptionTaskExecutor") ThreadPoolTaskExecutor redisSubscriptionTaskExecutor
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setTaskExecutor(redisListenerTaskExecutor);
        container.setSubscriptionExecutor(redisSubscriptionTaskExecutor);
        return container;
    }

    @Bean("redisListenerTaskExecutor")
    public ThreadPoolTaskExecutor redisListenerTaskExecutor() {
        // Redis 메시지 리스너 콜백 처리용 풀
        // 목적: 구독 채널 처리량이 순간 증가해도 메인 애플리케이션 스레드와 분리해 보호
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("redis-listener-");
        executor.setCorePoolSize(redisListenerCorePoolSize);
        executor.setMaxPoolSize(redisListenerMaxPoolSize);
        executor.setQueueCapacity(redisListenerQueueCapacity);
        executor.setAllowCoreThreadTimeOut(true);
        executor.initialize();
        return executor;
    }

    @Bean("redisSubscriptionTaskExecutor")
    public ThreadPoolTaskExecutor redisSubscriptionTaskExecutor() {
        // 구독 등록/해지 등 subscription 관리용 풀
        // 목적: 리스닝 처리와 subscription 관리를 분리해 지연 전파를 줄임
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("redis-subscription-");
        executor.setCorePoolSize(redisSubscriptionCorePoolSize);
        executor.setMaxPoolSize(redisSubscriptionMaxPoolSize);
        executor.setQueueCapacity(redisSubscriptionQueueCapacity);
        executor.setAllowCoreThreadTimeOut(true);
        executor.initialize();
        return executor;
    }

    @Bean
    @Primary
    // 변경: 기존 Cluster 연결 팩토리를 Primary로 명시
    // 이유: RedisTemplate/PubSub 리스너 등 기존 빈 주입 동작을 유지하기 위해
    public LettuceConnectionFactory redisConnectionFactory(ClientResources clientResources) {
        RedisClusterConfiguration clusterConfiguration = new RedisClusterConfiguration(redisNodes);
        clusterConfiguration.setMaxRedirects(3);

        LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
                .clientResources(clientResources)
                .clientOptions(ClusterClientOptions.builder()
                        .socketOptions(SocketOptions.builder().build())
                        .build())
                .build();

        return new LettuceConnectionFactory(clusterConfiguration, clientConfiguration);
    }

    @Bean(name = "streamRedisConnectionFactory")
    // 변경: Stream 전용 Standalone Redis 연결 팩토리 추가
    // 이유: Pub/Sub는 Cluster를 유지하고, XADD는 6379 단일 Redis로 분리하기 위해
    public LettuceConnectionFactory streamRedisConnectionFactory() {
        RedisStandaloneConfiguration standaloneConfiguration =
                new RedisStandaloneConfiguration(streamRedisHost, streamRedisPort);
        return new LettuceConnectionFactory(standaloneConfiguration);
    }

    /**
     * Redis 데이터 조작을 위한 템플릿
     * Redis 직렬화/역직렬화 설정
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);
        RedisSerializer<Object> jsonSerializer = RedisSerializer.json();
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(jsonSerializer);
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(jsonSerializer);
        return redisTemplate;
    }

    @Bean(name = "streamStringRedisTemplate")
    // 변경: Stream(XADD) 전용 StringRedisTemplate 추가
    // 이유: Stream 필드를 문자열 기반으로 저장하고, 기존 Object RedisTemplate과 역할을 분리하기 위해
    public StringRedisTemplate streamStringRedisTemplate(
            @Qualifier("streamRedisConnectionFactory") RedisConnectionFactory connectionFactory
    ) {
        return new StringRedisTemplate(connectionFactory);
    }
}
