package com.minialalipay.account.application.analytics;

import com.minialalipay.account.domain.ledger.LedgerDirection;
import com.minialalipay.account.domain.ledger.LedgerEntry;
import com.minialalipay.account.domain.ledger.LedgerRepository;
import com.minialalipay.account.domain.analytics.AnalyticsErrorCode;
import com.minialalipay.common.error.BusinessException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 按已过账账本分录生成个人收支分析，只读且不改变资金事实。 */
@Service
public class AccountAnalyticsApplicationService {
    private final LedgerRepository repository;
    private final Clock clock;

    public AccountAnalyticsApplicationService(LedgerRepository repository) {
        this(repository, Clock.systemUTC());
    }

    AccountAnalyticsApplicationService(LedgerRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public AccountAnalyticsDTO get(String userId, String range) {
        int days = switch (range == null ? "7d" : range) { case "7d" -> 7; case "30d" -> 30; case "month" -> 30; default -> throw new BusinessException(AnalyticsErrorCode.RANGE_NOT_SUPPORTED); };
        Instant now = clock.instant();
        Instant since = now.minus(days - 1L, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
        List<LedgerEntry> entries = repository.findPostedEntriesByUserId(userId, since, now);
        Map<LocalDate, long[]> daily = new LinkedHashMap<>();
        LocalDate first = since.atZone(ZoneOffset.UTC).toLocalDate();
        for (int i = 0; i < days; i++) daily.put(first.plusDays(i), new long[2]);
        long income = 0, expense = 0;
        for (LedgerEntry entry : entries) {
            LocalDate date = entry.createdAt().atZone(ZoneOffset.UTC).toLocalDate();
            long[] values = daily.get(date);
            if (values == null) continue;
            if (entry.direction() == LedgerDirection.CREDIT) { income += entry.amountFen(); values[0] += entry.amountFen(); }
            else { expense += entry.amountFen(); values[1] += entry.amountFen(); }
        }
        List<AccountAnalyticsDTO.TrendPoint> trend = new ArrayList<>();
        daily.forEach((date, values) -> trend.add(new AccountAnalyticsDTO.TrendPoint(date.toString(), values[0], values[1])));
        return new AccountAnalyticsDTO(range == null ? "7d" : range, "ledger-posted-v1", income, expense, trend, List.of(), income - expense, 0L);
    }
}
