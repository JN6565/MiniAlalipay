package com.minialalipay.user.application.identity;

import com.minialalipay.user.domain.user.User;
import com.minialalipay.user.domain.user.UserRepository;
import com.minialalipay.user.interfaces.dto.identity.IdentityDTO;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.error.CommonErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 身份绑定应用服务：编排绑定身份和查询身份状态用例。
 *
 * <p>绑定身份时计算身份证号 SHA-256 哈希并保存掩码，
 * 供 account-center 绑卡时通过内部接口做三要素交叉比对。</p>
 */
@Service
public class IdentityApplicationService {

    private final UserRepository userRepository;

    public IdentityApplicationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 绑定身份信息：设置真实姓名和身份证号，计算哈希，更新身份状态为 VERIFIED。
     *
     * @param userId 用户 ID
     * @param realName 真实姓名
     * @param idCard 身份证号明文
     * @return 身份信息响应
     */
    @Transactional
    public IdentityDTO bindIdentity(String userId, String realName, String idCard) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

        byte[] hash = sha256(idCard.trim());
        String masked = maskIdCard(idCard.trim());

        user.bindIdentity(realName.trim(), hash, masked);
        userRepository.update(user);

        return new IdentityDTO(user.getRealName(), user.getIdCard(), user.getIdentityStatus());
    }

    /**
     * 查询用户身份绑定状态。
     *
     * @param userId 用户 ID
     * @return 身份信息响应
     */
    @Transactional(readOnly = true)
    public IdentityDTO getIdentity(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        return new IdentityDTO(user.getRealName(), user.getIdCard(), user.getIdentityStatus());
    }

    /**
     * 三要素交叉校验：比对持卡人姓名、身份证号、手机号与用户存储信息是否完全一致。
     *
     * @param userId 用户 ID
     * @param holderName 持卡人姓名
     * @param idCard 身份证号明文
     * @param phone 手机号明文
     * @return 是否全部匹配
     */
    @Transactional(readOnly = true)
    public boolean verifyThreeElements(String userId, String holderName, String idCard, String phone) {
        return userRepository.findById(userId)
                .map(user -> {
                    boolean nameMatch = user.getRealName() != null
                            && user.getRealName().equals(holderName.trim());
                    boolean idCardMatch = user.getIdCardHash() != null
                            && MessageDigest.isEqual(user.getIdCardHash(), sha256(idCard.trim()));
                    boolean phoneMatch = user.getPhoneNumber() != null
                            && user.getPhoneNumber().equals(phone.trim());
                    return nameMatch && idCardMatch && phoneMatch;
                })
                .orElse(false);
    }

    /** 计算 SHA-256 哈希。 */
    private static byte[] sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }

    /** 身份证号掩码：保留前 4 位和后 4 位，中间用 * 替代。 */
    private static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 8) {
            return "****";
        }
        return idCard.substring(0, 4) + "*".repeat(idCard.length() - 8) + idCard.substring(idCard.length() - 4);
    }
}
