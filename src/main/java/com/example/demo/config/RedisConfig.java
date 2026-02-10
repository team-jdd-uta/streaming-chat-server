package com.example.demo.config;

import com.example.demo.model.ChatMessage;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.internal.HostAndPort;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import io.lettuce.core.resource.MappingSocketAddressResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
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
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }

    @Bean
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

    /**
     * Redis 데이터 조작을 위한 템플릿
     * Redis 직렬화/역직렬화 설정
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new Jackson2JsonRedisSerializer<>(ChatMessage.class));
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(new Jackson2JsonRedisSerializer<>(ChatMessage.class));
        return redisTemplate;
    }
}
