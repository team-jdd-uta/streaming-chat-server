package com.example.demo.config;

import com.example.demo.model.ChatMessage;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.internal.HostAndPort;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import io.lettuce.core.resource.MappingSocketAddressResolver;
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
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    @Bean(destroyMethod = "shutdown")
    public ClientResources clientResources() {
        final Set<Integer> clusterPorts = redisNodes.stream()
                .map(node -> Integer.parseInt(node.split(":")[1]))
                .collect(Collectors.toSet());

        return DefaultClientResources.builder()
                .socketAddressResolver(MappingSocketAddressResolver.create(new Function<HostAndPort, HostAndPort>() {
                    @Override
                    public HostAndPort apply(HostAndPort hostAndPort) {
                        // When running Redis Cluster in Docker, nodes often advertise container hostnames
                        // (e.g., redis-node-4). Remap those to localhost for local development.
                        if (!"localhost".equals(hostAndPort.getHostText()) && clusterPorts.contains(hostAndPort.getPort())) {
                            System.out.println("Remapping Redis node hostname from " + hostAndPort.getHostText() + " to localhost for port " + hostAndPort.getPort());
                            return HostAndPort.of("localhost", hostAndPort.getPort());
                        }
                        return hostAndPort;
                    }
                }))
                .build();
    }


    /**
     * redis와 상호작용하는 가장 낮은 수준의 객체
     * Redis 서버와 연결을 관리하며, 연결이 끊어지면 다시 연결을 시도
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            @Qualifier("redisConnectionFactory") RedisConnectionFactory connectionFactory
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
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
    public RedisTemplate<String, Object> redisTemplate(
            @Qualifier("redisConnectionFactory") RedisConnectionFactory connectionFactory
    ) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new Jackson2JsonRedisSerializer<>(ChatMessage.class));
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(new Jackson2JsonRedisSerializer<>(ChatMessage.class));
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
