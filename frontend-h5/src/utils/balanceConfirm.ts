import { getMyAccount, type AccountInfo } from '@/services/account';

/**
 * 余额变动确认工具（充值/提现后余额实时性保障）。
 *
 * 为什么轮询而非直接信任接口返回：充值/提现后端为"同步受理 + 异步 TCC 入账"，
 * 接口返回仅代表受理成功，余额事实以账本为准；前端必须轮询账户接口直到余额
 * 按预期方向变化，才能向用户展示"最新余额"。
 */

/** 余额确认结果：confirmed 为是否观察到预期方向的余额变化；newBalanceFen 为最近一次拉到的可用余额（分），拉取失败时为 null。 */
export interface BalanceConfirmResult {
  confirmed: boolean;
  newBalanceFen: number | null;
}

export interface BalanceConfirmOptions {
  /** 轮询间隔（毫秒），默认 800ms */
  intervalMs?: number;
  /** 最长等待时间（毫秒），默认 10s；超时不抛错，返回 confirmed=false */
  timeoutMs?: number;
}

/**
 * 单次拉取账户可用余额（分）；请求失败返回 null，由调用方决定是否重试。
 * 钱包页"余额可能滞后"场景的静默补拉也复用此函数。
 */
export const fetchAvailableFen = async (): Promise<number | null> => {
  try {
    // 请求拦截器已拆包 ApiResponse，运行时返回值为业务数据而非 AxiosResponse
    const account = (await getMyAccount()) as unknown as AccountInfo;
    return account?.availableFen ?? null;
  } catch {
    return null;
  }
};

/**
 * 轮询确认余额按预期方向变化。
 *
 * @param beforeFen 操作前的账户可用余额（分），作为比较基准
 * @param expectDirection 预期变动方向：IN 期望余额增加（充值），OUT 期望余额减少（提现）
 * @param opts 轮询间隔与超时配置
 * @returns 确认成功返回最新余额；超时返回 confirmed=false 与最近一次拉到的余额
 */
export const confirmBalanceChange = async (
  beforeFen: number,
  expectDirection: 'IN' | 'OUT',
  opts?: BalanceConfirmOptions,
): Promise<BalanceConfirmResult> => {
  const intervalMs = opts?.intervalMs ?? 800;
  const timeoutMs = opts?.timeoutMs ?? 10000;
  const startedAt = Date.now();
  let latestFen: number | null = null;

  // 先立即拉一次：后端若已同步入账可即时确认，避免用户多等一个间隔
  const first = await fetchAvailableFen();
  if (first !== null) {
    latestFen = first;
    const changed = expectDirection === 'IN' ? first > beforeFen : first < beforeFen;
    if (changed) return { confirmed: true, newBalanceFen: first };
  }

  // 间隔轮询直至超时；单次请求失败不中断轮询，网络抖动不应吞掉确认机会
  while (Date.now() - startedAt < timeoutMs) {
    await new Promise((resolve) => setTimeout(resolve, intervalMs));
    const balanceFen = await fetchAvailableFen();
    if (balanceFen === null) continue;
    latestFen = balanceFen;
    const changed = expectDirection === 'IN' ? balanceFen > beforeFen : balanceFen < beforeFen;
    if (changed) return { confirmed: true, newBalanceFen: balanceFen };
  }

  return { confirmed: false, newBalanceFen: latestFen };
};
