package com.minialalipay.business.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minialalipay.business.application.monitoring.MonitoringEvent;
import com.minialalipay.business.application.monitoring.MonitoringStreamPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Redis Streams 监控事件适配器，只处理已脱敏的结构化字段。 */
@Component
public class RedisMonitoringStreamPort implements MonitoringStreamPort {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String EVENT_ID = "eventId";
    private static final String EVENT_TYPE = "eventType";
    private static final String EVENT_VERSION = "eventVersion";
    private static final String OCCURRED_AT = "occurredAt";
    private static final String TRACE_ID = "traceId";
    private static final String ATTRIBUTES = "attributes";

    private final StringRedisTemplate redis;
    private final String streamKey;

    public RedisMonitoringStreamPort(StringRedisTemplate redis,
                                     @Value("${minialalipay.monitoring.stream-key:minialalipay:monitoring-events}") String streamKey) {
        this.redis = redis;
        this.streamKey = streamKey;
    }

    @Override
    public void append(MonitoringEvent event) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(EVENT_ID, event.eventId());
        fields.put(EVENT_TYPE, event.eventType());
        fields.put(EVENT_VERSION, String.valueOf(event.version()));
        fields.put(OCCURRED_AT, String.valueOf(event.occurredAt().toEpochMilli()));
        fields.put(TRACE_ID, event.traceId());
        try {
            fields.put(ATTRIBUTES, JSON.writeValueAsString(event.attributes()));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("监控事件属性无法序列化", ex);
        }
        redis.opsForStream().add(StreamRecords.mapBacked(fields).withStreamKey(streamKey));
    }

    @Override
    public List<StreamMessage> readAfter(String cursor, int limit) {
        List<MapRecord<String, Object, Object>> records = redis.opsForStream().read(
                StreamReadOptions.empty().count(limit), StreamOffset.create(streamKey, ReadOffset.from(cursor)));
        if (records == null) return List.of();
        return records.stream().map(this::toMessage).toList();
    }

    private StreamMessage toMessage(MapRecord<String, Object, Object> record) {
        Map<String, String> fields = new LinkedHashMap<>();
        record.getValue().forEach((key, value) -> fields.put(String.valueOf(key), String.valueOf(value)));
        try {
            Map<String, String> attributes = JSON.readValue(required(fields, ATTRIBUTES), new TypeReference<>() { });
            MonitoringEvent event = new MonitoringEvent(required(fields, EVENT_ID), required(fields, EVENT_TYPE),
                    Integer.parseInt(required(fields, EVENT_VERSION)),
                    Instant.ofEpochMilli(Long.parseLong(required(fields, OCCURRED_AT))), required(fields, TRACE_ID), attributes);
            return new StreamMessage(record.getId().getValue(), event);
        } catch (JsonProcessingException | NumberFormatException ex) {
            throw new IllegalStateException("监控 Stream 消息格式不合法", ex);
        }
    }

    private static String required(Map<String, String> fields, String name) {
        String value = fields.get(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("监控 Stream 消息缺少字段: " + name);
        return value;
    }
}
