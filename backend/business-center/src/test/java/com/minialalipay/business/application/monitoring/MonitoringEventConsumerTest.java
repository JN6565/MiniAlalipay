package com.minialalipay.business.application.monitoring;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.minialalipay.business.application.monitoring.MonitoringEventStore.InboxClaimResult.ALREADY_DONE;
import static com.minialalipay.business.application.monitoring.MonitoringEventStore.InboxClaimResult.CLAIMED;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MonitoringEventConsumerTest {
    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");

    @Test
    void 重复投递只投影一次且不重复执行业务动作() {
        RecordingStore store = new RecordingStore();
        MonitoringEventConsumer consumer = new MonitoringEventConsumer("ops-projection", store);
        MonitoringEvent event = event("event-1", "alert.status.changed", 1,
                Map.of("alertId", "alert-1", "status", "OPEN"));

        assertEquals(EventConsumeResult.PROJECTED, consumer.consume(event));
        assertEquals(EventConsumeResult.DUPLICATE, consumer.consume(event));
        assertEquals(1, store.projected.size());
        assertEquals(Set.of("event-1"), store.completed);
    }

    @Test
    void 未知版本和缺失字段进入隔离投影异常可重试() {
        RecordingStore store = new RecordingStore();
        MonitoringEventConsumer consumer = new MonitoringEventConsumer("ops-projection", store);

        assertEquals(EventConsumeResult.QUARANTINED,
                consumer.consume(event("event-2", "risk.decision.created", 2,
                        Map.of("decisionId", "risk-1", "subjectId", "qr-1", "action", "PASS"))));
        assertEquals(EventConsumeResult.QUARANTINED,
                consumer.consume(event("event-3", "alert.status.changed", 1,
                        Map.of("alertId", "alert-1"))));
        assertEquals(EventConsumeResult.RETRYABLE_FAILURE,
                consumer.consume(event("event-4", "data_quality.check.completed", 1,
                        Map.of("resultId", "quality-1", "status", "PASSED"))));
        assertEquals(EventConsumeResult.PROJECTED,
                consumer.consume(event("event-4", "data_quality.check.completed", 1,
                        Map.of("resultId", "quality-1", "status", "PASSED"))));
        assertEquals(2, store.quarantined.size());
        assertEquals(1, store.projected.size());
        // 隔离属于明确终态，Inbox 完成后 Stream 游标才能跳过坏消息继续消费后续事件。
        assertEquals(Set.of("event-2", "event-3", "event-4"), store.completed);
    }

    private static MonitoringEvent event(String id, String type, int version, Map<String, String> attributes) {
        return new MonitoringEvent(id, type, version, NOW, "trace-1", attributes);
    }

    private static final class RecordingStore implements MonitoringEventStore {
        private final Set<String> claimed = new HashSet<>();
        private final Set<String> completed = new HashSet<>();
        private final Set<String> failed = new HashSet<>();
        private final ArrayList<MonitoringEvent> projected = new ArrayList<>();
        private final ArrayList<MonitoringEvent> quarantined = new ArrayList<>();
        private boolean failFirstProjection = true;

        @Override
        public InboxClaimResult claim(String consumerName, String eventId) {
            return claimed.add(consumerName + ":" + eventId) ? CLAIMED : ALREADY_DONE;
        }

        @Override
        public void project(MonitoringEvent event) {
            if ("event-4".equals(event.eventId()) && failFirstProjection) {
                failFirstProjection = false;
                throw new IllegalStateException("模拟投影失败");
            }
            projected.add(event);
        }

        @Override
        public void complete(String consumerName, String eventId) { completed.add(eventId); }

        @Override
        public void fail(String consumerName, String eventId, String reason) {
            failed.add(eventId);
            claimed.remove(consumerName + ":" + eventId);
        }

        @Override
        public void quarantine(MonitoringEvent event, String reason) { quarantined.add(event); }
    }
}
