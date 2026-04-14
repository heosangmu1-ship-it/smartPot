package com.garden.smartgarden.config;

import com.garden.smartgarden.aggregate.SensorAggregateService;
import com.garden.smartgarden.sse.SseBroadcastService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

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

    /**
     * Redis Pub/Sub 컨테이너:
     *  - sensor:update    -> SSE event "sensor"    (raw 실시간)
     *  - sensor:aggregate -> SSE event "aggregate" (1분 평균)
     *
     * 둘 다 같은 SseBroadcastService 의 emitter 리스트에 푸시하지만,
     * 채널마다 다른 콜백을 등록해 SSE 이벤트 이름을 구분한다.
     */
    @Bean
    public RedisMessageListenerContainer redisContainer(
            RedisConnectionFactory connectionFactory,
            SseBroadcastService broadcastService) {
        var container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        MessageListener rawListener =
                (message, pattern) -> broadcastService.onRawSensorEvent(new String(message.getBody()));
        MessageListener aggregateListener =
                (message, pattern) -> broadcastService.onAggregateEvent(new String(message.getBody()));

        container.addMessageListener(rawListener, new ChannelTopic(pubsubChannel));
        container.addMessageListener(aggregateListener, new ChannelTopic(SensorAggregateService.AGGREGATE_CHANNEL));
        return container;
    }
}
