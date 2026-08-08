package com.minialalipay.business.application.monitoring;

import com.minialalipay.business.application.port.MonitoringOutboxPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonitoringOutboxPublisherTest {
    private static final Instant NOW = Instant.parse("2026-08-08T07:00:00Z");

    @Test
    void Redis写入成功后才标记Outbox已发布() {
        RecordingOutbox outbox = new RecordingOutbox();
        RecordingStream stream = new RecordingStream();
        MonitoringEvent event = event("01J00000000000000000000001");
        outbox.ready.add(new MonitoringOutboxPort.PendingMonitoringEvent(event, 0));

        new MonitoringOutboxPublisher(outbox, stream, 10).publishReadyEvents();

        assertEquals(List.of(event), stream.events);
        assertEquals(List.of(event.eventId()), outbox.published);
        assertTrue(outbox.retries.isEmpty());
    }

    @Test
    void Redis失败时保留Pending并安排重试() {
        RecordingOutbox outbox = new RecordingOutbox();
        RecordingStream stream = new RecordingStream();
        stream.fail = true;
        outbox.ready.add(new MonitoringOutboxPort.PendingMonitoringEvent(event("01J00000000000000000000002"), 2));

        new MonitoringOutboxPublisher(outbox, stream, 10).publishReadyEvents();

        assertTrue(outbox.published.isEmpty());
        assertEquals(1, outbox.retries.size());
    }

    private static MonitoringEvent event(String id) {
        return new MonitoringEvent(id, "transaction.accepted", 1, NOW, "trace-1",
                Map.of("transactionId", "tx-1", "status", "PROCESSING"));
    }

    private static final class RecordingOutbox implements MonitoringOutboxPort {
        private final List<PendingMonitoringEvent> ready = new ArrayList<>();
        private final List<String> published = new ArrayList<>();
        private final List<Instant> retries = new ArrayList<>();

        @Override public List<PendingMonitoringEvent> findReadyEvents(Instant now, int limit) { return ready; }
        @Override public void markPublished(String eventId, Instant publishedAt) { published.add(eventId); }
        @Override public void scheduleRetry(String eventId, Instant nextRetryAt) { retries.add(nextRetryAt); }
    }

    private static final class RecordingStream implements MonitoringStreamPort {
        private final List<MonitoringEvent> events = new ArrayList<>();
        private boolean fail;
        @Override public void append(MonitoringEvent event) {
            if (fail) throw new IllegalStateException("Redis不可用");
            events.add(event);
        }
        @Override public List<StreamMessage> readAfter(String cursor, int limit) { return List.of(); }
    }
}
