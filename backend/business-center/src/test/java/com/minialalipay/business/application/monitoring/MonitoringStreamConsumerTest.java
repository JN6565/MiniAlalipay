package com.minialalipay.business.application.monitoring;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DeadlockLoserDataAccessException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.minialalipay.business.application.monitoring.MonitoringEventStore.InboxClaimResult.ALREADY_DONE;
import static com.minialalipay.business.application.monitoring.MonitoringEventStore.InboxClaimResult.CLAIMED;
import static com.minialalipay.business.application.monitoring.MonitoringEventStore.InboxClaimResult.RETRY_LATER;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MonitoringStreamConsumerTest {
    private static final Instant NOW = Instant.parse("2026-08-08T07:00:00Z");

    @Test
    void 投影完成后推进流游标() {
        RecordingStore store = new RecordingStore();
        RecordingStream stream = new RecordingStream(List.of(
                new MonitoringStreamPort.StreamMessage("1-0", event("01J00000000000000000000003")),
                new MonitoringStreamPort.StreamMessage("2-0", event("01J00000000000000000000004"))));

        new MonitoringStreamConsumer(stream, new MonitoringEventConsumer("ops-projection", store), store, 10)
                .consumeAvailableEvents();

        assertEquals("2-0", store.cursor);
        assertEquals(Set.of("01J00000000000000000000003", "01J00000000000000000000004"), store.completed);
    }

    @Test
    void 投影可重试失败时不推进游标() {
        RecordingStore store = new RecordingStore();
        store.failNext = true;
        RecordingStream stream = new RecordingStream(List.of(
                new MonitoringStreamPort.StreamMessage("3-0", event("01J00000000000000000000005"))));

        new MonitoringStreamConsumer(stream, new MonitoringEventConsumer("ops-projection", store), store, 10)
                .consumeAvailableEvents();

        assertEquals("0-0", store.cursor);
        assertEquals(Set.of(), store.completed);
    }

    @Test
    void Inbox仍在退避窗口时不越过消息推进流游标() {
        RecordingStore store = new RecordingStore();
        store.retryLaterEventId = "01J00000000000000000000006";
        RecordingStream stream = new RecordingStream(List.of(
                new MonitoringStreamPort.StreamMessage("4-0", event("01J00000000000000000000006")),
                new MonitoringStreamPort.StreamMessage("5-0", event("01J00000000000000000000007"))));

        new MonitoringStreamConsumer(stream, new MonitoringEventConsumer("ops-projection", store), store, 10)
                .consumeAvailableEvents();

        assertEquals("0-0", store.cursor);
        assertEquals(Set.of(), store.completed);
    }

    @Test
    void 领取遭遇死锁回滚时不抛异常也不推进游标() {
        // 多实例并发领取同一事件时 MySQL 会选一个死锁受害者整体回滚，
        // 消费者必须降级为保留游标待下轮重试，而不是把调度器打成 ERROR
        RecordingStore store = new RecordingStore();
        store.deadlockOnClaim = true;
        RecordingStream stream = new RecordingStream(List.of(
                new MonitoringStreamPort.StreamMessage("6-0", event("01J00000000000000000000008"))));

        new MonitoringStreamConsumer(stream, new MonitoringEventConsumer("ops-projection", store), store, 10)
                .consumeAvailableEvents();

        assertEquals("0-0", store.cursor);
        assertEquals(Set.of(), store.completed);
    }

    private static MonitoringEvent event(String id) {
        return new MonitoringEvent(id, "transaction.accepted", 1, NOW, "trace-1",
                Map.of("transactionId", "tx-1", "status", "PROCESSING"));
    }

    private static final class RecordingStream implements MonitoringStreamPort {
        private final List<StreamMessage> messages;
        private RecordingStream(List<StreamMessage> messages) { this.messages = messages; }
        @Override public void append(MonitoringEvent event) { }
        @Override public List<StreamMessage> readAfter(String cursor, int limit) { return messages; }
    }

    private static final class RecordingStore implements MonitoringEventStore {
        private String cursor = "0-0";
        private boolean failNext;
        private boolean deadlockOnClaim;
        private String retryLaterEventId;
        private final Set<String> claimed = new HashSet<>();
        private final Set<String> completed = new HashSet<>();
        private final List<MonitoringEvent> projected = new ArrayList<>();

        @Override public InboxClaimResult claim(String consumerName, String eventId) {
            if (deadlockOnClaim) throw new DeadlockLoserDataAccessException("Deadlock found when trying to get lock", null);
            if (eventId.equals(retryLaterEventId)) return RETRY_LATER;
            return claimed.add(eventId) ? CLAIMED : ALREADY_DONE;
        }
        @Override public void project(MonitoringEvent event) {
            if (failNext) { failNext = false; throw new IllegalStateException("投影失败"); }
            projected.add(event);
        }
        @Override public void complete(String consumerName, String eventId) { completed.add(eventId); }
        @Override public void fail(String consumerName, String eventId, String reason) { claimed.remove(eventId); }
        @Override public void quarantine(MonitoringEvent event, String reason) { }
        @Override public String currentStreamCursor(String consumerName) { return cursor; }
        @Override public boolean advanceStreamCursor(String consumerName, String expectedCursor, String nextCursor) {
            if (!cursor.equals(expectedCursor)) return false;
            cursor = nextCursor;
            return true;
        }
    }
}
