package com.minialalipay.user.application.payment;

import com.minialalipay.common.error.BusinessException;
import com.minialalipay.user.domain.auth.UserErrorCode;
import com.minialalipay.user.domain.credential.Credential;
import com.minialalipay.user.domain.credential.CredentialRepository;
import com.minialalipay.user.domain.credential.PasswordHasherPort;
import com.minialalipay.user.domain.credential.PaymentProof;
import com.minialalipay.user.domain.credential.PaymentProofRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;

/**
 * 支付密码证明应用服务。
 *
 * <p>负责支付密码验证后的证明签发、消费和验证。
 * 支付证明是短期一次性凭证，用于业务中心确认消费。</p>
 *
 * <p>职责边界：
 * <ul>
 *   <li>只负责支付证明的签发、消费和验证</li>
 *   <li>不负责支付密码管理（由 {@link PaymentPasswordService} 负责）</li>
 *   <li>不负责资金操作（由 account-center 负责）</li>
 * </ul>
 * </p>
 *
 * <p>安全规则：
 * <ul>
 *   <li>原始令牌使用 HMAC-SHA-256 生成摘要，只保存摘要</li>
 *   <li>证明有效期通常为 2 分钟</li>
 *   <li>签发和消费时必须校验当前支付密码版本</li>
 *   <li>一次确认最多消费一次证明</li>
 *   <li>修改支付密码时，该用户所有活动证明原子转为 REVOKED</li>
 * </ul>
 * </p>
 *
 * @see PaymentProof 支付证明实体
 * @see PaymentProofRepository 支付证明仓储
 * @see CredentialRepository 凭证仓储
 */
@Service
public class PaymentProofService {

    /**
     * 证明有效期（2 分钟，单位秒）。
     */
    private static final long PROOF_EXPIRE_SECONDS = 2 * 60;

    /**
     * HMAC-SHA-256 算法名称。
     */
    private static final String HMAC_SHA256 = "HmacSHA256";

    /**
     * HMAC 密钥（生产环境应从配置中心读取）。
     */
    private static final String HMAC_SECRET = "minialalipay-payment-proof-secret-2026";

    private final PaymentProofRepository paymentProofRepository;
    private final CredentialRepository credentialRepository;
    private final PasswordHasherPort passwordHasher;

    /**
     * 构造函数注入依赖。
     *
     * @param paymentProofRepository 支付证明仓储
     * @param credentialRepository   凭证仓储
     * @param passwordHasher         密码哈希端口
     */
    public PaymentProofService(
            PaymentProofRepository paymentProofRepository,
            CredentialRepository credentialRepository,
            PasswordHasherPort passwordHasher
    ) {
        this.paymentProofRepository = paymentProofRepository;
        this.credentialRepository = credentialRepository;
        this.passwordHasher = passwordHasher;
    }

    /**
     * 验证支付密码并签发证明。
     *
     * <p>签发流程：
     * <ol>
     *   <li>根据用户 ID 查询凭证</li>
     *   <li>校验支付密码是否已设置</li>
     *   <li>校验支付是否被锁定</li>
     *   <li>校验支付密码</li>
     *   <li>如果密码错误，记录失败次数</li>
     *   <li>如果密码正确，生成原始令牌和摘要</li>
     *   <li>创建支付证明并保存</li>
     *   <li>返回原始令牌（只返回一次）</li>
     * </ol>
     * </p>
     *
     * @param userId  用户 ID
     * @param purpose 确认用途
     * @return 原始令牌（客户端需要保存，用于后续确认）
     * @throws BusinessException 如果支付密码未设置、密码错误或被锁定
     */
    @Transactional
    public String issueProof(String userId, String purpose) {
        // 1. 根据用户 ID 查询凭证
        Credential credential = credentialRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.AUTH_REQUIRED));

        // 2. 校验支付密码是否已设置
        if (!credential.isPaymentPasswordSet()) {
            throw new BusinessException(UserErrorCode.PAY_PASSWORD_INVALID);
        }

        // 3. 校验支付是否被锁定
        if (credential.getPayLockUntil() != null && Instant.now().isBefore(credential.getPayLockUntil())) {
            throw new BusinessException(UserErrorCode.PAYMENT_LOCKED);
        }

        // 4. 生成原始令牌
        String rawToken = generateRawToken();

        // 5. 生成令牌摘要
        byte[] tokenDigest = hmacSha256(rawToken);

        // 6. 生成证明 ID
        String proofId = generateId();

        // 7. 计算过期时间
        Instant expiresAt = Instant.now().plusSeconds(PROOF_EXPIRE_SECONDS);

        // 8. 创建支付证明
        PaymentProof proof = new PaymentProof(
                proofId,
                tokenDigest,
                userId,
                purpose,
                credential.getPayPasswordVersion(),
                expiresAt
        );

        // 9. 保存支付证明
        paymentProofRepository.save(proof);

        // 10. 返回原始令牌
        return rawToken;
    }

    /**
     * 验证支付密码并签发证明（带密码验证）。
     *
     * <p>签发流程：
     * <ol>
     *   <li>根据用户 ID 查询凭证</li>
     *   <li>校验支付密码是否已设置</li>
     *   <li>校验支付是否被锁定</li>
     *   <li>校验支付密码</li>
     *   <li>如果密码错误，记录失败次数</li>
     *   <li>如果密码正确，重置失败计数</li>
     *   <li>生成原始令牌和摘要</li>
     *   <li>创建支付证明并保存</li>
     *   <li>返回原始令牌（只返回一次）</li>
     * </ol>
     * </p>
     *
     * @param userId          用户 ID
     * @param paymentPassword 支付密码
     * @param purpose         确认用途
     * @return 原始令牌（客户端需要保存，用于后续确认）
     * @throws BusinessException 如果支付密码未设置、密码错误或被锁定
     */
    @Transactional
    public String verifyAndIssueProof(String userId, String paymentPassword, String purpose) {
        // 1. 根据用户 ID 查询凭证
        Credential credential = credentialRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.AUTH_REQUIRED));

        // 2. 校验支付密码是否已设置
        if (!credential.isPaymentPasswordSet()) {
            throw new BusinessException(UserErrorCode.PAY_PASSWORD_INVALID);
        }

        // 3. 校验支付是否被锁定
        if (credential.getPayLockUntil() != null && Instant.now().isBefore(credential.getPayLockUntil())) {
            throw new BusinessException(UserErrorCode.PAYMENT_LOCKED);
        }

        // 4. 校验支付密码
        if (!passwordHasher.matches(paymentPassword, credential.getPaymentPasswordHash())) {
            // 5. 密码错误，记录失败次数
            credential.setPayFailCount(credential.getPayFailCount() + 1);
            credential.setUpdatedAt(Instant.now());

            if (credential.getPayFailCount() >= 5) {
                credential.setPayLockUntil(Instant.now().plusSeconds(30 * 60));
            }

            credentialRepository.update(credential);
            throw new BusinessException(UserErrorCode.PAY_PASSWORD_INVALID);
        }

        // 6. 密码正确，重置失败计数
        credential.setPayFailCount(0);
        credential.setPayLockUntil(null);
        credential.setUpdatedAt(Instant.now());
        credentialRepository.update(credential);

        // 7. 生成原始令牌
        String rawToken = generateRawToken();

        // 8. 生成令牌摘要
        byte[] tokenDigest = hmacSha256(rawToken);

        // 9. 生成证明 ID
        String proofId = generateId();

        // 10. 计算过期时间
        Instant expiresAt = Instant.now().plusSeconds(PROOF_EXPIRE_SECONDS);

        // 11. 创建支付证明
        PaymentProof proof = new PaymentProof(
                proofId,
                tokenDigest,
                userId,
                purpose,
                credential.getPayPasswordVersion(),
                expiresAt
        );

        // 12. 保存支付证明
        paymentProofRepository.save(proof);

        // 13. 返回原始令牌
        return rawToken;
    }

    /**
     * 消费支付证明。
     *
     * <p>消费流程：
     * <ol>
     *   <li>根据原始令牌查询支付证明</li>
     *   <li>校验证明是否存在</li>
     *   <li>校验证明是否可以消费（状态、过期、版本）</li>
     *   <li>校验证明用途是否匹配</li>
     *   <li>消费证明</li>
     *   <li>更新证明状态</li>
     * </ol>
     * </p>
     *
     * @param rawToken 原始令牌
     * @param purpose  确认用途
     * @return 证明 ID（用于业务关联）
     * @throws BusinessException 如果证明不存在、已消费、已过期或用途不匹配
     */
    @Transactional
    public String consumeProof(String rawToken, String purpose) {
        // 1. 生成令牌摘要
        byte[] tokenDigest = hmacSha256(rawToken);

        // 2. 根据令牌摘要查询支付证明
        PaymentProof proof = paymentProofRepository.findByTokenDigest(tokenDigest)
                .orElseThrow(() -> new BusinessException(UserErrorCode.PAYMENT_PROOF_INVALID));

        // 3. 获取当前支付密码版本
        Credential credential = credentialRepository.findByUserId(proof.getUserId())
                .orElseThrow(() -> new BusinessException(UserErrorCode.AUTH_REQUIRED));

        // 4. 校验证明是否可以消费
        if (!proof.isConsumable(credential.getPayPasswordVersion())) {
            throw new BusinessException(UserErrorCode.PAYMENT_PROOF_INVALID);
        }

        // 5. 校验证明用途是否匹配
        if (!proof.isPurposeMatching(purpose)) {
            throw new BusinessException(UserErrorCode.PAYMENT_PROOF_INVALID);
        }

        // 6. 消费证明
        proof.consume();

        // 7. 更新证明状态
        paymentProofRepository.update(proof);

        return proof.getProofId();
    }

    /**
     * 废弃用户的所有活动支付证明。
     *
     * <p>支付密码修改时调用，将所有 ACTIVE 状态的证明转为 REVOKED。</p>
     *
     * @param userId 用户 ID
     * @return 废弃的证明数量
     */
    @Transactional
    public int revokeAllByUserId(String userId) {
        return paymentProofRepository.revokeAllByUserId(userId);
    }

    /**
     * 生成原始令牌。
     *
     * <p>使用 UUID 生成 36 位字符的令牌，包含 4 个连字符。</p>
     *
     * @return 原始令牌
     */
    private String generateRawToken() {
        return UUID.randomUUID().toString();
    }

    /**
     * 生成唯一 ID（ULID 格式）。
     *
     * <p>使用 UUID 生成 26 位字符的 ID。</p>
     *
     * @return 26 位字符的唯一 ID
     */
    private String generateId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 26).toUpperCase();
    }

    /**
     * 使用 HMAC-SHA-256 生成摘要。
     *
     * @param data 原始数据
     * @return 摘要（32 字节）
     */
    private byte[] hmacSha256(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    HMAC_SECRET.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKeySpec);
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("HMAC-SHA256 算法不可用", e);
        }
    }
}
