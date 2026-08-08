package com.minialalipay.user.infrastructure.id;

import com.minialalipay.user.domain.user.UserIdGeneratorPort;
import com.minialalipay.user.infrastructure.persistence.mapper.UserIdSequenceMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 用户 ID 与注册幂等键成对生成器。
 *
 * <p>注册时调用一次 {@link #generatePair()}，返回共享同一日期序列号的 userId 和 registrationId。
 * 两者格式均为 26 位字符：
 * <ul>
 *   <li>{@code userId}：{@code USR} + 9 位随机大写字母 + {@code YYYYMMDD} + 6 位序列号</li>
 *   <li>{@code registrationId}：{@code REG} + 9 位随机大写字母 + {@code YYYYMMDD} + 6 位序列号</li>
 * </ul>
 * 序列号由 {@code user_id_sequence} 表按日期原子递增，保证同一天内不重复。</p>
 *
 * <p>并发安全：序列号递增通过数据库行锁串行化；随机段由 {@link SecureRandom} 生成，
 * 碰撞概率极低（26^9 约 5.4 × 10^12）。</p>
 */
@Component
public class UserIdGenerator implements UserIdGeneratorPort {

    /** userId 前缀，与 registrationId 视觉区分。 */
    private static final String USER_PREFIX = "USR";

    /** registrationId 前缀，与 userId 视觉区分。 */
    private static final String REG_PREFIX = "REG";

    /** 随机段长度：9 位大写字母。 */
    private static final int RANDOM_LENGTH = 9;

    /** 随机段字符集：26 个大写英文字母。 */
    private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    /** 日期格式：YYYYMMDD，共 8 位。 */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    /** 序列号格式：6 位零填充十进制。 */
    private static final String SEQ_FORMAT = "%06d";

    private final UserIdSequenceMapper sequenceMapper;
    private final SecureRandom secureRandom;

    public UserIdGenerator(UserIdSequenceMapper sequenceMapper) {
        this.sequenceMapper = sequenceMapper;
        this.secureRandom = new SecureRandom();
    }

    /**
     * 成对生成 userId 和 registrationId。
     *
     * <p>在同一次调用内完成以下操作：
     * <ol>
     *   <li>原子递增当日序列号</li>
     *   <li>生成两段独立的 9 位随机大写字母</li>
     *   <li>拼接为两个 26 位 ID，共享序列号和日期</li>
     * </ol>
     * 必须在事务内调用（由 {@code AuthService.register()} 的 {@code @Transactional} 保证），
     * 以确保序列号递增和 {@code LAST_INSERT_ID()} 读取使用同一数据库连接。</p>
     *
     * @return 成对的 userId 和 registrationId
     */
    @Transactional
    @Override
    public IdPair generatePair() {
        LocalDate today = LocalDate.now();
        String dateStr = today.format(DATE_FORMATTER);

        // 原子递增当日序列号，并通过 LAST_INSERT_ID() 读取新值。
        // 两步必须在同一连接（同一事务）内完成。
        sequenceMapper.incrementValue(today);
        int seq = sequenceMapper.lastInsertId();
        String seqStr = SEQ_FORMAT.formatted(seq);

        String userRandom = generateRandomPart();
        String regRandom = generateRandomPart();

        String userId = USER_PREFIX + userRandom + dateStr + seqStr;
        String registrationId = REG_PREFIX + regRandom + dateStr + seqStr;

        return new IdPair(userId, registrationId);
    }

    /**
     * 生成 9 位随机大写字母串。
     *
     * <p>使用 {@link SecureRandom} 保证密码学安全级别的随机性，
     * 碰撞空间为 26^9 ≈ 5.4 × 10^12。</p>
     *
     * @return 9 位大写字母组成的随机字符串
     */
    private String generateRandomPart() {
        StringBuilder sb = new StringBuilder(RANDOM_LENGTH);
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            sb.append(ALPHABET[secureRandom.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
