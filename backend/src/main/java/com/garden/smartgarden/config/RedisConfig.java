package com.garden.smartgarden.config;

import com.garden.smartgarden.sse.SseBroadcastService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

/**
 * Redis 설정:
 *  - StringRedisTemplate: 캐시(GET/SET) 및 Pub/Sub 발행
 *  - MessageListenerContainer: Pub/Sub 구독 -> SseBroadcastService 로 전달
 */
@Configuration
public class RedisConfig {

    @Value("${app.redis.pubsub-channel}")
    private String pubsubChannel;

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory cf) {
        return new StringRedisTemplate(cf);
    }

    @Bean
    public MessageListenerAdapter redisMessageListener(SseBroadcastService broadcastService) {
        // broadcastService.onRedisMessage(String payload) 를 호출
        return new MessageListenerAdapter(broadcastService, "onRedisMessage");
    }

    @Bean
    public RedisMessageListenerContainer redisContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter listenerAdapter) {
        var container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listenerAdapter, new PatternTopic(pubsubChannel));
        return container;
    }
}
