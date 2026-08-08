package com.minialalipay.account.application.ledger;

import com.minialalipay.account.domain.ledger.LedgerEntry;
import com.minialalipay.account.domain.ledger.LedgerRepository;
import com.minialalipay.account.domain.ledger.LedgerVoucher;
import com.minialalipay.account.domain.ledger.LedgerErrorCode;
import com.minialalipay.account.domain.ledger.LedgerVoucherStatus;
import com.minialalipay.account.domain.account.AccountErrorCode;
import com.minialalipay.account.application.ledger.dto.LedgerEntryDTO;
import com.minialalipay.account.application.ledger.dto.LedgerEntryPageDTO;
import com.minialalipay.account.application.port.UserInfoPort;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.error.CommonErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 账本凭证过账和本人分录查询应用服务。
 *
 * <p>同一交易、类型和冲正序号只产生一张凭证；过账校验与凭证/分录插入处于同一
 * ledger_db 本地事务。分录查询只接受可信会话用户 ID。</p>
 */
@Service
public class LedgerApplicationService {

    private final LedgerRepository ledgerRepository;
    private final UserInfoPort userInfoPort;

    public LedgerApplicationService(LedgerRepository ledgerRepository, UserInfoPort userInfoPort) {
        this.ledgerRepository = ledgerRepository;
        this.userInfoPort = userInfoPort;
    }

    /**
     * 幂等过账一张已准备且借贷平衡的凭证。
     *
     * <p>事务先持久化 PREPARED 凭证和分录，再锁定数据库事实、汇总验平并原子写 POSTED 与 Outbox。
     * 调用方必须透传全链路 traceId，并提供本次事件的全局唯一 eventId。</p>
     *
     * @param voucher 待过账凭证及完整不可变分录
     * @param eventId `ledger.voucher.posted` 事件 ID
     * @param traceId 32 位全链路追踪 ID
     * @param now 过账时间
     * @return 已过账凭证；重复同参调用返回既有事实
     */
    @Transactional
    public LedgerVoucher post(LedgerVoucher voucher, String eventId, String traceId, Instant now) {
        requireTechnicalContext(eventId, traceId);
        LedgerVoucher existing = ledgerRepository.find(voucher.getTransactionId(), voucher.getVoucherType(),
                voucher.getReversalNo()).orElse(null);
        if (existing == null) {
            try {
                ledgerRepository.savePrepared(voucher);
                existing = voucher;
            } catch (DataIntegrityViolationException conflict) {
                existing = ledgerRepository.find(voucher.getTransactionId(), voucher.getVoucherType(),
                        voucher.getReversalNo()).orElseThrow(() -> conflict);
            }
        }
        validateRepeatedVoucher(existing, voucher);
        LedgerVoucher locked = ledgerRepository.findByIdForUpdate(existing.getVoucherId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.INTERNAL_ERROR));
        if (locked.getStatus() == LedgerVoucherStatus.POSTED) return locked;
        LedgerRepository.LedgerTotals totals = ledgerRepository.summarizeEntries(locked.getVoucherId());
        if (totals.debitFen() != locked.getTotalDebitFen()
                || totals.creditFen() != locked.getTotalCreditFen()
                || totals.debitFen() != totals.creditFen()) {
            throw new IllegalStateException("数据库账本分录借贷不平");
        }
        locked.post(now);
        if (!ledgerRepository.postAndAppendOutbox(locked, eventId, traceId, now)) {
            throw new BusinessException(AccountErrorCode.VERSION_CONFLICT);
        }
        return locked;
    }

    /** 查询本人不可变账本分录（含交易对方名称），单页最多 100 条。 */
    @Transactional(readOnly = true)
    public LedgerEntryPageDTO listMyEntries(String userId, String cursor, int limit) {
        if (limit < 1 || limit > 100) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        Cursor decoded = decodeCursor(cursor);
        List<LedgerEntry.WithCounterparty> fetched = ledgerRepository.findEntriesWithCounterparty(userId,
                decoded.createdAt(), decoded.entryId(), limit + 1);
        boolean hasMore = fetched.size() > limit;
        List<LedgerEntry.WithCounterparty> page = hasMore ? fetched.subList(0, limit) : fetched;

        // 批量查询交易对方用户信息
        Map<String, UserInfoPort.UserInfo> counterpartyInfoMap = batchResolveCounterparties(page);

        List<LedgerEntryDTO> items = page.stream()
                .map(wc -> toDto(wc, counterpartyInfoMap))
                .toList();
        String nextCursor = hasMore ? encodeCursor(page.getLast().entry()) : null;
        return new LedgerEntryPageDTO(items, nextCursor);
    }

    /** 批量解析交易对方用户信息，失败时降级为空名称。 */
    private Map<String, UserInfoPort.UserInfo> batchResolveCounterparties(
            List<LedgerEntry.WithCounterparty> entries) {
        List<String> userIds = entries.stream()
                .map(LedgerEntry.WithCounterparty::counterpartyUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (userIds.isEmpty()) return Map.of();

        return userIds.stream()
                .collect(Collectors.toMap(
                        uid -> uid,
                        uid -> userInfoPort.findUserInfo(uid),
                        (a, b) -> a
                ));
    }

    private LedgerEntryDTO toDto(LedgerEntry.WithCounterparty wc,
                                  Map<String, UserInfoPort.UserInfo> counterpartyInfoMap) {
        String counterpartyName = "";
        if (wc.counterpartyUserId() != null) {
            UserInfoPort.UserInfo info = counterpartyInfoMap.get(wc.counterpartyUserId());
            if (info != null) {
                counterpartyName = info.displayName();
            }
        }
        LedgerEntry entry = wc.entry();
        return new LedgerEntryDTO(entry.entryId(), entry.transactionId(), entry.direction().name(),
                entry.amountFen(), entry.memo(), counterpartyName, entry.createdAt());
    }

    private Cursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return new Cursor(null, 0L);
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separator = decoded.lastIndexOf(':');
            Instant createdAt = Instant.parse(decoded.substring(0, separator));
            long entryId = Long.parseLong(decoded.substring(separator + 1));
            if (entryId <= 0) throw new IllegalArgumentException();
            return new Cursor(createdAt, entryId);
        } catch (RuntimeException invalidCursor) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    private String encodeCursor(LedgerEntry entry) {
        String raw = entry.createdAt() + ":" + entry.entryId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private void validateRepeatedVoucher(LedgerVoucher existing, LedgerVoucher requested) {
        boolean sameHeader = existing.getTransactionId().equals(requested.getTransactionId())
                && existing.getVoucherType().equals(requested.getVoucherType())
                && existing.getReversalNo() == requested.getReversalNo()
                && Objects.equals(existing.getOriginalVoucherId(), requested.getOriginalVoucherId())
                && existing.getReversalReason() == requested.getReversalReason()
                && existing.getTotalDebitFen() == requested.getTotalDebitFen()
                && existing.getTotalCreditFen() == requested.getTotalCreditFen();
        boolean sameEntries = existing.getEntries().size() == requested.getEntries().size();
        if (sameEntries) {
            for (int index = 0; index < existing.getEntries().size(); index++) {
                LedgerEntry left = existing.getEntries().get(index);
                LedgerEntry right = requested.getEntries().get(index);
                if (!left.ledgerAccountId().equals(right.ledgerAccountId())
                        || left.direction() != right.direction()
                        || left.amountFen() != right.amountFen()
                        || left.sequenceNo() != right.sequenceNo()
                        || !Objects.equals(left.memo(), right.memo())) {
                    sameEntries = false;
                    break;
                }
            }
        }
        if (!sameHeader || !sameEntries) {
            throw new BusinessException(LedgerErrorCode.IDEMPOTENCY_CONFLICT);
        }
    }

    private void requireTechnicalContext(String eventId, String traceId) {
        if (eventId == null || eventId.length() != 26 || traceId == null || traceId.length() != 32) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    private record Cursor(Instant createdAt, long entryId) {
    }
}
