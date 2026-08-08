package com.minialalipay.account.infrastructure.bankcard;

import com.minialalipay.account.domain.bankcard.BankCard;
import com.minialalipay.account.domain.bankcard.BankCardRepository;
import com.minialalipay.account.domain.bankcard.BankCardStatus;
import com.minialalipay.account.domain.bankcard.BankCardType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 使用 account_db.bank_card 表实现银行卡仓储端口。
 *
 * <p>更新一律走乐观锁 CAS（version 条件），保护默认卡互斥与解绑终态
 * 在并发下不被破坏；查询只返回掩码字段，表中不存完整卡号明文。</p>
 */
@Repository
public class BankCardRepositoryImpl implements BankCardRepository {

    private final JdbcTemplate jdbcTemplate;

    public BankCardRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<BankCard> findActiveByUserId(String userId) {
        return jdbcTemplate.query("SELECT * FROM account_db.bank_card "
                        + "WHERE user_id = ? AND status = 'ACTIVE' ORDER BY created_at ASC",
                this::mapCard, userId);
    }

    @Override
    public Optional<BankCard> findById(String cardId) {
        return jdbcTemplate.query("SELECT * FROM account_db.bank_card WHERE card_id = ?",
                        this::mapCard, cardId)
                .stream().findFirst();
    }

    @Override
    public long countActiveByUserId(String userId) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM account_db.bank_card "
                + "WHERE user_id = ? AND status = 'ACTIVE'", Long.class, userId);
        return count == null ? 0L : count;
    }

    @Override
    public boolean existsActiveByUserAndCard(String userId, String cardBin, String cardLast4) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM account_db.bank_card "
                        + "WHERE user_id = ? AND card_bin = ? AND card_last4 = ? AND status = 'ACTIVE'",
                Long.class, userId, cardBin, cardLast4);
        return count != null && count > 0;
    }

    @Override
    public void save(BankCard card) {
        jdbcTemplate.update("INSERT INTO account_db.bank_card "
                        + "(card_id,user_id,account_id,bank_code,bank_name,card_type,card_bin,card_last4,"
                        + "holder_masked,id_card_masked,phone_masked,is_default,status,unbound_at,"
                        + "version,created_at,updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                card.getCardId(), card.getUserId(), card.getAccountId(), card.getBankCode(),
                card.getBankName(), card.getCardType().name(), card.getCardBin(), card.getCardLast4(),
                card.getHolderMasked(), card.getIdCardMasked(), card.getPhoneMasked(),
                card.isDefault(), card.getStatus().name(), toTimestamp(card.getUnboundAt()),
                card.getVersion(), toTimestamp(card.getCreatedAt()), toTimestamp(card.getUpdatedAt()));
    }

    @Override
    public boolean updateByCas(BankCard card, long expectedVersion) {
        int updated = jdbcTemplate.update("UPDATE account_db.bank_card SET "
                        + "is_default = ?, status = ?, unbound_at = ?, version = version + 1, updated_at = ? "
                        + "WHERE card_id = ? AND version = ?",
                card.isDefault(), card.getStatus().name(), toTimestamp(card.getUnboundAt()),
                toTimestamp(card.getUpdatedAt()), card.getCardId(), expectedVersion);
        if (updated == 1) {
            card.updateVersion(expectedVersion + 1);
        }
        return updated == 1;
    }

    private BankCard mapCard(ResultSet rs, int rowNum) throws SQLException {
        return new BankCard(rs.getString("card_id"), rs.getString("user_id"),
                rs.getString("account_id"), rs.getString("bank_code"), rs.getString("bank_name"),
                BankCardType.valueOf(rs.getString("card_type")), rs.getString("card_bin"),
                rs.getString("card_last4"), rs.getString("holder_masked"),
                rs.getString("id_card_masked"), rs.getString("phone_masked"),
                rs.getBoolean("is_default"), BankCardStatus.valueOf(rs.getString("status")),
                instantOrNull(rs, "unbound_at"), rs.getLong("version"),
                instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column).toInstant();
    }

    private static Instant instantOrNull(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
