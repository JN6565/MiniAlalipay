package com.minialalipay.account.infrastructure.bankcard;

import com.minialalipay.account.domain.bankcard.BankCardType;
import com.minialalipay.account.domain.bankcard.RegisteredCard;
import com.minialalipay.account.domain.bankcard.RegisteredCardRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 使用 account_db.bank_card_registration 表实现银行卡注册仓储端口。
 *
 * <p>注册记录保存三要素哈希与完整卡号（模拟环境允许），
 * 卡号明文只在注册响应中返回一次，后续查询不再暴露。</p>
 */
@Repository
public class RegisteredCardRepositoryImpl implements RegisteredCardRepository {

    private final JdbcTemplate jdbcTemplate;

    public RegisteredCardRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(RegisteredCard card) {
        jdbcTemplate.update("INSERT INTO account_db.bank_card_registration "
                        + "(registration_id,user_id,bank_code,bank_name,card_type,card_number,"
                        + "card_bin,card_last4,holder_name,id_card_hash,phone_hash,"
                        + "status,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                card.getRegistrationId(), card.getUserId(), card.getBankCode(),
                card.getBankName(), card.getCardType().name(), card.getCardNumber(),
                card.getCardBin(), card.getCardLast4(), card.getHolderName(),
                card.getIdCardHash(), card.getPhoneHash(),
                card.getStatus(), toTimestamp(card.getCreatedAt()));
    }

    @Override
    public Optional<RegisteredCard> findById(String registrationId) {
        return jdbcTemplate.query("SELECT * FROM account_db.bank_card_registration "
                        + "WHERE registration_id = ?",
                        this::mapCard, registrationId)
                .stream().findFirst();
    }

    @Override
    public Optional<RegisteredCard> findByCardNumber(String cardNumber) {
        return jdbcTemplate.query("SELECT * FROM account_db.bank_card_registration "
                        + "WHERE card_number = ?",
                        this::mapCard, cardNumber)
                .stream().findFirst();
    }

    @Override
    public List<RegisteredCard> findRegisteredByUserId(String userId) {
        return jdbcTemplate.query("SELECT * FROM account_db.bank_card_registration "
                        + "WHERE user_id = ? AND status = 'REGISTERED' ORDER BY created_at DESC",
                this::mapCard, userId);
    }

    @Override
    public boolean updateStatus(RegisteredCard card) {
        int updated = jdbcTemplate.update("UPDATE account_db.bank_card_registration "
                        + "SET status = ? WHERE registration_id = ? AND status = 'REGISTERED'",
                card.getStatus(), card.getRegistrationId());
        return updated == 1;
    }

    private RegisteredCard mapCard(ResultSet rs, int rowNum) throws SQLException {
        return new RegisteredCard(
                rs.getString("registration_id"),
                rs.getString("user_id"),
                rs.getString("bank_code"),
                rs.getString("bank_name"),
                BankCardType.valueOf(rs.getString("card_type")),
                rs.getString("card_number"),
                rs.getString("card_bin"),
                rs.getString("card_last4"),
                rs.getString("holder_name"),
                rs.getBytes("id_card_hash"),
                rs.getBytes("phone_hash"),
                rs.getString("status"),
                instant(rs, "created_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column).toInstant();
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
