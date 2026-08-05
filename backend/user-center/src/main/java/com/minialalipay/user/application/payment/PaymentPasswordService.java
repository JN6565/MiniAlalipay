package com.minialalipay.user.application.payment;

import com.minialalipay.common.error.BusinessException;
import com.minialalipay.user.domain.auth.UserErrorCode;
import com.minialalipay.user.domain.credential.Credential;
import com.minialalipay.user.domain.credential.CredentialRepository;
import com.minialalipay.user.domain.credential.PasswordHasherPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 支付密码应用服务。
 *
 * <p>负责支付密码的设置、修改和验证，是用户中心的安全核心服务。
 * 支付密码与登录密码独立，用于资金操作的身份验证。</p>
 *
 * <p>职责边界：
 * <ul>
 *   <li>只负责支付密码的管理（设置、修改、验证）</li>
 *   <li>不负责登录密码管理（由 {@link com.minialalipay.user.application.auth.AuthService} 负责）</li>
 *   <li>不负责支付证明签发（由 {@link PaymentProofService} 负责）</li>
 *   <li>不负责资金操作（由 account-center 负责）</li>
 * </ul>
 * </p>
 *
 * <p>安全规则：
 * <ul>
 *   <li>支付密码为独立的 6 位数字密码，与登录密码无关</li>
 *   <li>支付密码使用 BCrypt 强哈希，不存储明文</li>
 *   <li>连续失败 5 次后锁定 30 分钟</li>
 *   <li>修改支付密码后立即废弃所有已签发的支付证明</li>
 *   <li>支付密码版本号用于废弃旧授权</li>
 * </ul>
 * </p>
 *
 * @see CredentialRepository 凭证仓储
 * @see PasswordHasher 密码哈希工具
 */
@Service
public class PaymentPasswordService {

    /**
     * 支付密码最大失败次数。
     */
    private static final int MAX_PAY_FAIL_COUNT = 5;

    /**
     * 支付密码锁定时间（30 分钟，单位秒）。
     */
    private static final long PAY_LOCK_SECONDS = 30 * 60;

    private final CredentialRepository credentialRepository;
    private final PasswordHasherPort passwordHasher;
    private final PaymentProofService paymentProofService;

    /**
     * 构造函数注入依赖。
     *
     * @param credentialRepository 凭证仓储
     * @param passwordHasher       密码哈希端口
     * @param paymentProofService  支付证明服务
     */
    public PaymentPasswordService(
            CredentialRepository credentialRepository,
            PasswordHasherPort passwordHasher,
            PaymentProofService paymentProofService
    ) {
        this.credentialRepository = credentialRepository;
        this.passwordHasher = passwordHasher;
        this.paymentProofService = paymentProofService;
    }

    /**
     * 设置支付密码。
     *
     * <p>设置流程：
     * <ol>
     *   <li>根据用户 ID 查询凭证</li>
     *   <li>校验支付密码是否已设置</li>
     *   <li>校验支付密码格式（6 位数字）</li>
     *   <li>对支付密码进行 BCrypt 哈希</li>
     *   <li>更新凭证（设置支付密码哈希，版本号递增）</li>
     * </ol>
     * </p>
     *
     * <p>业务规则：
     * <ul>
     *   <li>首次设置只允许 {@code payment_password_hash IS NULL}</li>
     *   <li>设置后版本号递增为 1</li>
     *   <li>设置成功后可以签发支付证明</li>
     * </ul>
     * </p>
     *
     * @param userId            用户 ID
     * @param paymentPassword   支付密码（6 位数字）
     * @throws BusinessException 如果支付密码已设置或格式不合法
     */
    @Transactional
    public void setPaymentPassword(String userId, String paymentPassword) {
        // 1. 根据用户 ID 查询凭证
        Credential credential = credentialRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.AUTH_REQUIRED));

        // 2. 校验支付密码是否已设置
        if (credential.isPaymentPasswordSet()) {
            throw new BusinessException(UserErrorCode.PAYMENT_PASSWORD_ALREADY_SET);
        }

        // 3. 校验支付密码格式（6 位数字）
        validatePaymentPassword(paymentPassword);

        // 4. 对支付密码进行 BCrypt 哈希
        String hashedPassword = passwordHasher.hashPassword(paymentPassword);

        // 5. 更新凭证
        credential.setPaymentPasswordHash(hashedPassword);
        credential.setPayPasswordVersion(1);
        credential.setUpdatedAt(Instant.now());
        credentialRepository.update(credential);
    }

    /**
     * 修改支付密码。
     *
     * <p>修改流程：
     * <ol>
     *   <li>根据用户 ID 查询凭证</li>
     *   <li>校验支付密码是否已设置</li>
     *   <li>校验当前支付密码</li>
     *   <li>校验新支付密码格式（6 位数字）</li>
     *   <li>对新支付密码进行 BCrypt 哈希</li>
     *   <li>更新凭证（更新支付密码哈希，版本号递增）</li>
     *   <li>废弃所有已签发的支付证明</li>
     * </ol>
     * </p>
     *
     * <p>业务规则：
     * <ul>
     *   <li>修改前必须验证当前支付密码</li>
     *   <li>修改后版本号递增</li>
     *   <li>修改后立即废弃所有已签发的支付证明</li>
     * </ul>
     * </p>
     *
     * @param userId                用户 ID
     * @param currentPassword       当前支付密码
     * @param newPassword           新支付密码
     * @throws BusinessException 如果支付密码未设置、当前密码错误或格式不合法
     */
    @Transactional
    public void changePaymentPassword(String userId, String currentPassword, String newPassword) {
        // 1. 根据用户 ID 查询凭证
        Credential credential = credentialRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.AUTH_REQUIRED));

        // 2. 校验支付密码是否已设置
        if (!credential.isPaymentPasswordSet()) {
            throw new BusinessException(UserErrorCode.PAY_PASSWORD_INVALID);
        }

        // 3. 校验当前支付密码
        if (!passwordHasher.matches(currentPassword, credential.getPaymentPasswordHash())) {
            throw new BusinessException(UserErrorCode.PAY_PASSWORD_INVALID);
        }

        // 4. 校验新支付密码格式（6 位数字）
        validatePaymentPassword(newPassword);

        // 5. 对新支付密码进行 BCrypt 哈希
        String hashedPassword = passwordHasher.hashPassword(newPassword);

        // 6. 更新凭证
        credential.setPaymentPasswordHash(hashedPassword);
        credential.setPayPasswordVersion(credential.getPayPasswordVersion() + 1);
        credential.setUpdatedAt(Instant.now());
        credentialRepository.update(credential);

        // 7. 废弃所有已签发的支付证明
        paymentProofService.revokeAllByUserId(userId);
    }

    /**
     * 验证支付密码。
     *
     * <p>验证流程：
     * <ol>
     *   <li>根据用户 ID 查询凭证</li>
     *   <li>校验支付密码是否已设置</li>
     *   <li>校验支付是否被锁定</li>
     *   <li>校验支付密码</li>
     *   <li>如果密码错误，记录失败次数</li>
     *   <li>如果密码正确，重置失败计数</li>
     * </ol>
     * </p>
     *
     * <p>业务规则：
     * <ul>
     *   <li>连续失败 5 次后锁定 30 分钟</li>
     *   <li>锁定期间的验证请求直接返回 {@link UserErrorCode#PAYMENT_LOCKED}</li>
     *   <li>验证成功后重置失败计数</li>
     * </ul>
     * </p>
     *
     * @param userId          用户 ID
     * @param paymentPassword 支付密码
     * @return 如果验证成功返回 true
     * @throws BusinessException 如果支付密码未设置、密码错误或被锁定
     */
    @Transactional
    public boolean verifyPaymentPassword(String userId, String paymentPassword) {
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

            if (credential.getPayFailCount() >= MAX_PAY_FAIL_COUNT) {
                credential.setPayLockUntil(Instant.now().plusSeconds(PAY_LOCK_SECONDS));
            }

            credentialRepository.update(credential);
            throw new BusinessException(UserErrorCode.PAY_PASSWORD_INVALID);
        }

        // 6. 密码正确，重置失败计数
        credential.setPayFailCount(0);
        credential.setPayLockUntil(null);
        credential.setUpdatedAt(Instant.now());
        credentialRepository.update(credential);

        return true;
    }

    /**
     * 校验支付密码格式。
     *
     * <p>支付密码必须为 6 位数字。</p>
     *
     * @param paymentPassword 支付密码
     * @throws BusinessException 如果格式不合法
     */
    private void validatePaymentPassword(String paymentPassword) {
        if (paymentPassword == null || !paymentPassword.matches("^\\d{6}$")) {
            throw new BusinessException(UserErrorCode.PASSWORD_POLICY_VIOLATION);
        }
    }
}
