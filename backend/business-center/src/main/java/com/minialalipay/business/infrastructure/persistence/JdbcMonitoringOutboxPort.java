package com.minialalipay.business.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minialalipay.business.application.monitoring.MonitoringEvent;
import com.minialalipay.business.application.port.MonitoringOutboxPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** business_db Outbox 的监控发布持久化适配器。 */
@Repository
public class JdbcMonitoringOutboxPort implements MonitoringOutboxPort {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final JdbcTemplate jdbc;

    public JdbcMonitoringOutboxPort(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PendingMonitoringEvent> findReadyEvents(Instant now, int limit) {
        return jdbc.query("SELECT event_id,event_type,event_version,business_type,source_type,funding_source,transaction_id,trace_id,occurred_at,payload,retry_count "
                        + "FROM business_db.outbox_event WHERE status='PENDING' AND (next_retry_at IS NULL OR next_retry_at<=?) "
                        + "ORDER BY created_at ASC LIMIT ?",
                (rs, rowNum) -> mapPending(rs), Timestamp.from(now), limit);
    }

    @Override
    public void markPublished(String eventId, Instant publishedAt) {
        jdbc.update("UPDATE business_db.outbox_event SET status='PUBLISHED',published_at=?,next_retry_at=NULL "
                        + "WHERE event_id=? AND status='PENDING'",
                Timestamp.from(publishedAt), eventId);
    }

    @Override
    public void scheduleRetry(String eventId, Instant nextRetryAt) {
        jdbc.update("UPDATE business_db.outbox_event SET retry_count=retry_count+1,next_retry_at=? "
                        + "WHERE event_id=? AND status='PENDING'",
                Timestamp.from(nextRetryAt), eventId);
    }

    @Override
    public List<PendingMonitoringEvent> findPublishedEvents(List<String> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) return List.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(eventIds.size(), "?"));
        return jdbc.query("SELECT event_id,event_type,event_version,business_type,source_type,funding_source,transaction_id,trace_id,occurred_at,payload,retry_count "
                        + "FROM business_db.outbox_event WHERE status='PUBLISHED' AND event_id IN (" + placeholders + ")",
                (rs, rowNum) -> mapPending(rs), eventIds.toArray());
    }

    private static PendingMonitoringEvent mapPending(ResultSet rs) throws SQLException {
        Map<String, String> attributes = payloadAttributes(rs.getString("payload"));
        putIfPresent(attributes, "businessType", rs.getString("business_type"));
        putIfPresent(attributes, "sourceType", rs.getString("source_type"));
        putIfPresent(attributes, "fundingSource", rs.getString("funding_source"));
        putIfPresent(attributes, "transactionId", rs.getString("transaction_id"));
        MonitoringEvent event = new MonitoringEvent(rs.getString("event_id"), rs.getString("event_type"),
                rs.getInt("event_version"), rs.getTimestamp("occurred_at").toInstant(),
                rs.getString("trace_id"), attributes);
        return new PendingMonitoringEvent(event, rs.getInt("retry_count"));
    }

    private static Map<String, String> payloadAttributes(String payload) {
        Map<String, String> attributes = new LinkedHashMap<>();
        try {
            JsonNode root = JSON.readTree(payload);
            if (root != null && root.isObject()) {
                root.fields().forEachRemaining(entry -> {
                    if (entry.getValue().isValueNode() && !entry.getValue().isNull()) {
                        attributes.put(entry.getKey(), entry.getValue().asText());
                    }
                });
            } else {
                attributes.put("payloadParseError", "true");
            }
        } catch (JsonProcessingException ex) {
            attributes.put("payloadParseError", "true");
        }
        return attributes;
    }

    private static void putIfPresent(Map<String, String> attributes, String key, String value) {
        if (value != null && !value.isBlank()) attributes.put(key, value);
    }
}
