package com.minialalipay.business.application.monitoring;

import com.minialalipay.business.application.port.MonitoringOutboxPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 失败监控事件回放任务测试，验证只回放已发布 Outbox 且不修改 Inbox 状态。 */
class MonitoringFailedEventReplayJobTest {
    @Test
    void 按失败事件从已发布Outbox追加回放() {
        RecordingInbox inbox = new RecordingInbox();
        RecordingOutbox outbox = new RecordingOutbox();
        RecordingStream stream = new RecordingStream();
        MonitoringEvent event = new MonitoringEvent("01J00000000000000000000001", "transaction.accepted", 1,
                Instant.parse("2026-08-08T00:00:00Z"), "trace-1",
                Map.of("transactionId", "tx-1", "status", "SUCCESS"));
        outbox.events = List.of(new MonitoringOutboxPort.PendingMonitoringEvent(event, 2));

        new MonitoringFailedEventReplayJob(inbox, outbox, stream, 100).replayFailedEvents();

        assertEquals(List.of("01J00000000000000000000001"), inbox.requested);
        assertEquals(List.of(event), stream.appended);
    }

    private static final class RecordingInbox implements MonitoringEventStore {
        private List<String> requested = List.of();
        @Override public List<String> findRetryableEventIds(Instant now, int limit) {
            requested = List.of("01J00000000000000000000001");
            return requested;
        }
        @Override public InboxClaimResult claim(String consumerName, String eventId) { return InboxClaimResult.RETRY_LATER; }
        @Override public void project(MonitoringEvent event) { }
        @Override public void complete(String consumerName, String eventId) { }
        @Override public void fail(String consumerName, String eventId, String reason) { }
        @Override public void quarantine(MonitoringEvent event, String reason) { }
    }

    private static final class RecordingOutbox implements MonitoringOutboxPort {
        private List<PendingMonitoringEvent> events = List.of();
        @Override public List<PendingMonitoringEvent> findReadyEvents(Instant now, int limit) { return List.of(); }
        @Override public void markPublished(String eventId, Instant publishedAt) { }
        @Override public void scheduleRetry(String eventId, Instant nextRetryAt) { }
        @Override public List<PendingMonitoringEvent> findPublishedEvents(List<String> eventIds) { return events; }
    }

    private static final class RecordingStream implements MonitoringStreamPort {
        private final List<MonitoringEvent> appended = new ArrayList<>();
        @Override public void append(MonitoringEvent event) { appended.add(event); }
        @Override public List<StreamMessage> readAfter(String cursor, int limit) { return List.of(); }
    }
}
