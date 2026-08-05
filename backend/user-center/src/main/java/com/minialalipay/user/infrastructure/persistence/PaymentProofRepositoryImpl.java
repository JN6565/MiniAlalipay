package com.minialalipay.user.infrastructure.persistence;

import com.minialalipay.user.domain.credential.PaymentProof;
import com.minialalipay.user.domain.credential.PaymentProofRepository;
import com.minialalipay.user.infrastructure.persistence.mapper.PaymentProofMapper;
import com.minialalipay.user.infrastructure.persistence.po.PaymentProofPO;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 支付密码证明仓储实现。
 *
 * <p>基于 MyBatis 的支付证明持久化实现，负责领域模型与数据库之间的转换。</p>
 *
 * <p>职责边界：
 * <ul>
 *   <li>实现 {@link PaymentProofRepository} 接口定义的持久化操作</li>
 *   <li>负责领域模型 {@link PaymentProof} 与持久化对象 {@link PaymentProofPO} 之间的转换</li>
 *   <li>不包含业务逻辑（由领域模型和应用服务负责）</li>
 * </ul>
 * </p>
 */
@Repository
public class PaymentProofRepositoryImpl implements PaymentProofRepository {

    private final PaymentProofMapper paymentProofMapper;

    public PaymentProofRepositoryImpl(PaymentProofMapper paymentProofMapper) {
        this.paymentProofMapper = paymentProofMapper;
    }

    @Override
    public void save(PaymentProof proof) {
        PaymentProofPO po = toPO(proof);
        paymentProofMapper.insert(po);
    }

    @Override
    public void update(PaymentProof proof) {
        PaymentProofPO po = toPO(proof);
        paymentProofMapper.update(po);
    }

    @Override
    public Optional<PaymentProof> findById(String proofId) {
        return paymentProofMapper.selectByProofId(proofId)
                .map(this::toDomain);
    }

    @Override
    public Optional<PaymentProof> findByTokenDigest(byte[] tokenDigest) {
        return paymentProofMapper.selectByTokenDigest(tokenDigest)
                .map(this::toDomain);
    }

    @Override
    public List<PaymentProof> findActiveByUserId(String userId) {
        return paymentProofMapper.selectActiveByUserId(userId).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public int revokeAllByUserId(String userId) {
        return paymentProofMapper.revokeAllByUserId(userId);
    }

    /**
     * 将领域模型转换为持久化对象。
     *
     * @param proof 领域模型
     * @return 持久化对象
     */
    private PaymentProofPO toPO(PaymentProof proof) {
        PaymentProofPO po = new PaymentProofPO();
        po.setProofId(proof.getProofId());
        po.setTokenDigest(proof.getTokenDigest());
        po.setUserId(proof.getUserId());
        po.setPurpose(proof.getPurpose());
        po.setPayPasswordVersion(proof.getPayPasswordVersion());
        po.setStatus(proof.getStatus().name());
        po.setExpiresAt(proof.getExpiresAt());
        po.setConsumedAt(proof.getConsumedAt());
        po.setCreatedAt(proof.getCreatedAt());
        return po;
    }

    /**
     * 将持久化对象转换为领域模型。
     *
     * @param po 持久化对象
     * @return 领域模型
     */
    private PaymentProof toDomain(PaymentProofPO po) {
        return new PaymentProof(
                po.getProofId(),
                po.getTokenDigest(),
                po.getUserId(),
                po.getPurpose(),
                po.getPayPasswordVersion(),
                PaymentProof.ProofStatus.valueOf(po.getStatus()),
                po.getExpiresAt(),
                po.getConsumedAt(),
                po.getCreatedAt()
        );
    }
}
