/** @jest-environment jsdom */
import React, { useState } from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { AmountInput } from '../../src/components/h5/AmountInput';

const ControlledAmountInput = () => {
  const [amount, setAmount] = useState(0);
  return (
    <>
      <AmountInput value={amount} onChange={setAmount} placeholder="请输入转账金额" />
      <button type="button" onClick={() => setAmount(0)}>重置金额</button>
    </>
  );
};

describe('AmountInput 组件', () => {
  test('保留小数点输入态并限制最多两位小数', () => {
    render(<ControlledAmountInput />);
    const input = screen.getByPlaceholderText('请输入转账金额') as HTMLInputElement;

    fireEvent.change(input, { target: { value: '12.' } });
    expect(input.value).toBe('12.');

    fireEvent.change(input, { target: { value: '12.34' } });
    expect(input.value).toBe('12.34');

    fireEvent.change(input, { target: { value: '12.345' } });
    expect(input.value).toBe('12.34');
  });

  test('外部金额重置后同步清空输入框', () => {
    render(<ControlledAmountInput />);
    const input = screen.getByPlaceholderText('请输入转账金额') as HTMLInputElement;

    fireEvent.change(input, { target: { value: '12.34' } });
    fireEvent.click(screen.getByRole('button', { name: '重置金额' }));

    expect(input.value).toBe('');
  });

  test('用户删除全部金额后保持输入框为空', () => {
    render(<ControlledAmountInput />);
    const input = screen.getByPlaceholderText('请输入转账金额') as HTMLInputElement;

    fireEvent.change(input, { target: { value: '12.34' } });
    fireEvent.change(input, { target: { value: '' } });

    expect(input.value).toBe('');
  });
});
