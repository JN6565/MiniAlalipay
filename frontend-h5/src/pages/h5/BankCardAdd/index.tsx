import { history } from 'umi';
import { Button, Input, Toast, Picker, Dialog } from 'antd-mobile';
import { useState } from 'react';
import { registerBankCard, BIN_TABLE } from '@/services/bankCard';
import { validateIdCard } from '@/services/utils';
import { ApiError } from '@/services/request';
import './index.less';

/** 中国大陆手机号：1 开头 11 位数字。 */
const PHONE_PATTERN = /^1\d{10}$/;

/** 银行选择列表：从 BIN 字典去重得到唯一的银行编码+名称。 */
const BANK_OPTIONS = Array.from(
  new Map(BIN_TABLE.map((item) => [item.bankCode, { label: item.bankName, value: item.bankCode }])).values()
);

/**
 * 银行卡注册页：选择银行 + 填写三要素 → 自动生成卡号。
 * 注册成功后展示生成的卡号，提示用户记住卡号后去绑定。
 */
const BankCardAddPage = () => {
  const [bankCode, setBankCode] = useState('');
  const [bankName, setBankName] = useState('');
  const [holderName, setHolderName] = useState('');
  const [idCard, setIdCard] = useState('');
  const [phone, setPhone] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [pickerVisible, setPickerVisible] = useState(false);
  const [registeredCard, setRegisteredCard] = useState<{
    cardNumber: string;
    bankName: string;
    cardLast4: string;
  } | null>(null);

  const handleBankSelect = (value: (string | number | null)[]) => {
    const code = value[0];
    if (code != null) {
      setBankCode(String(code));
      const option = BANK_OPTIONS.find((opt) => opt.value === String(code));
      if (option) setBankName(option.label);
    }
  };

  const handleSubmit = async () => {
    if (!bankCode) {
      Toast.show({ icon: 'fail', content: '请选择银行' });
      return;
    }
    if (!holderName.trim()) {
      Toast.show({ icon: 'fail', content: '请输入持卡人姓名' });
      return;
    }
    // 姓名规则与绑定身份保持一致（2-32 位）
    if (holderName.trim().length < 2 || holderName.trim().length > 32) {
      Toast.show({ icon: 'fail', content: '持卡人姓名长度必须为2-32位' });
      return;
    }
    // 身份证复用绑定身份页的统一校验（格式 + 出生日期）
    const idCardError = validateIdCard(idCard);
    if (idCardError) {
      Toast.show({ icon: 'fail', content: idCardError });
      return;
    }
    if (!PHONE_PATTERN.test(phone.trim())) {
      Toast.show({ icon: 'fail', content: '预留手机号格式不正确' });
      return;
    }

    setSubmitting(true);
    try {
      const result = await registerBankCard({
        bankCode,
        holderName: holderName.trim(),
        idCard: idCard.trim().toUpperCase(),
        phone: phone.trim(),
      });
      setRegisteredCard({
        cardNumber: result.cardNumber || '',
        bankName: result.bankName,
        cardLast4: result.cardLast4,
      });
      Toast.show({ icon: 'success', content: '注册成功，请牢记卡号' });
    } catch (error: any) {
      const code = error instanceof ApiError ? error.code : 'UNKNOWN';
      if (code === 'IDENTITY_NOT_BOUND') {
        // 未绑定身份：引导先去绑定身份信息，再回来注册银行卡
        Dialog.confirm({
          content: '请先绑定身份信息后再注册银行卡',
          confirmText: '去绑定身份',
          cancelText: '稍后再说',
          onConfirm: () => history.push('/h5/identity-bind'),
        });
      } else {
        Toast.show({ icon: 'fail', content: error?.message || '注册失败，请稍后重试' });
      }
    } finally {
      setSubmitting(false);
    }
  };

  // 注册成功后展示卡号
  if (registeredCard) {
    const formatted = registeredCard.cardNumber.replace(/(\d{4})(?=\d)/g, '$1 ');
    return (
      <div className="bank-card-add-page">
        <div className="add-section">
          <div className="section-label">注册成功！请牢记您的卡号</div>
          <div className="registered-card-number">{formatted}</div>
          <div className="registered-card-info">
            银行：{registeredCard.bankName}，尾号：{registeredCard.cardLast4}
          </div>
        </div>
        <div className="add-tip">
          请复制并记住卡号，然后前往「绑定银行卡」页面完成绑定。
        </div>
        <Button block color="primary" size="large" onClick={() => history.push('/h5/bank-card-bind')}>
          去绑定银行卡
        </Button>
        <Button block size="large" style={{ marginTop: 12 }} onClick={() => history.push('/h5/bank-cards')}>
          稍后再说
        </Button>
      </div>
    );
  }

  return (
    <div className="bank-card-add-page">
      <div className="add-section">
        <div className="section-label">选择银行</div>
        <Picker
          columns={[BANK_OPTIONS]}
          visible={pickerVisible}
          onConfirm={(value) => {
            handleBankSelect(value);
            setPickerVisible(false);
          }}
          onCancel={() => setPickerVisible(false)}
        >
          {(items) => (
            <Button block size="large" style={{ textAlign: 'left' }} onClick={() => setPickerVisible(true)}>
              {bankCode ? bankName : '请选择银行'}
            </Button>
          )}
        </Picker>
      </div>

      {bankCode && (
        <div className="add-section">
          <div className="section-label">持卡人姓名</div>
          <Input placeholder="请输入银行预留姓名" value={holderName} onChange={setHolderName} maxLength={32} clearable />

          <div className="section-label">身份证号</div>
          <Input placeholder="请输入 18 位身份证号" value={idCard} onChange={setIdCard} maxLength={18} clearable />

          <div className="section-label">预留手机号</div>
          <Input
            placeholder="请输入银行预留手机号"
            value={phone}
            onChange={setPhone}
            inputMode="numeric"
            maxLength={11}
            clearable
          />
        </div>
      )}

      <div className="add-tip">
        注册后系统将自动生成卡号，请牢记卡号后前往绑定页面完成绑卡。三要素信息仅用于校验，平台只保存哈希值。
      </div>

      <Button
        block
        color="primary"
        size="large"
        loading={submitting}
        disabled={!bankCode}
        onClick={handleSubmit}
      >
        注册银行卡
      </Button>
    </div>
  );
};

export default BankCardAddPage;
