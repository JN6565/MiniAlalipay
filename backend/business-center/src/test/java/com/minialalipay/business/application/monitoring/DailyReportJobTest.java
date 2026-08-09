package com.minialalipay.business.application.monitoring;

import com.minialalipay.business.application.port.DailyReportStore;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DailyReportJobTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Instant NOW = Instant.parse("2026-08-08T02:00:00Z");

    @Test
    void 前一业务日质量通过后发布日报() {
        RecordingStore store = new RecordingStore();
        DailyReportJob job = new DailyReportJob(store, Clock.fixed(NOW, ZONE), ZONE);

        job.runPreviousBusinessDay();

        assertEquals(LocalDate.of(2026, 8, 7), store.date);
        assertEquals(2, store.checks.size());
        assertEquals(1, store.publishedCount);
    }

    @Test
    void Inbox未完成时仍写质量结果但不发布指标() {
        RecordingStore store = new RecordingStore();
        store.incompleteInbox = 1;
        DailyReportJob job = new DailyReportJob(store, Clock.fixed(NOW, ZONE), ZONE);

        job.run(LocalDate.of(2026, 8, 7));

        assertEquals(2, store.checks.size());
        assertEquals(0, store.publishedCount);
    }

    @Test
    void 临时报表覆盖昨日零点至当前时刻且不发布正式日报() {
        RecordingStore store = new RecordingStore();
        DailyReportJob job = new DailyReportJob(store, Clock.fixed(NOW, ZONE), ZONE);

        DailyReportJob.DailyReportPreview preview = job.previewFromPreviousBusinessDay();

        assertEquals(Instant.parse("2026-08-06T16:00:00Z"), preview.windowStart());
        assertEquals(NOW, preview.windowEnd());
        assertEquals(true, preview.publishable());
        assertEquals(1, preview.metrics().size());
        assertEquals(0, store.publishedCount);
    }

    private static final class RecordingStore implements DailyReportStore {
        private LocalDate date;
        private List<QualityCheck> checks = List.of();
        private long incompleteInbox;
        private int publishedCount;
        @Override public List<DailyMetricValue> aggregate(LocalDate reportDate, String zoneId) {
            return List.of(new DailyMetricValue("transaction.accepted", new byte[32], "{}", 1, 1));
        }
        @Override public List<DailyMetricValue> aggregate(Instant from, Instant to) {
            return List.of(new DailyMetricValue("transaction.accepted", new byte[32], "{}", 1, 1));
        }
        @Override public long countIncompleteInbox(LocalDate reportDate, String zoneId) { return incompleteInbox; }
        @Override public long countQuarantined(LocalDate reportDate, String zoneId) { return 0; }
        @Override public long countIncompleteInbox(Instant from, Instant to) { return incompleteInbox; }
        @Override public long countQuarantined(Instant from, Instant to) { return 0; }
        @Override public void publish(LocalDate reportDate, List<DailyMetricValue> metrics, List<QualityCheck> checks, Instant now) {
            this.date = reportDate;
            this.checks = checks;
            if (checks.stream().allMatch(check -> "PASSED".equals(check.status()))) publishedCount++;
        }
    }
}
