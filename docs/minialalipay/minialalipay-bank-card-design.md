# 银行卡功能：支付宝式卡管理全流程设计

## 1. 需求概述

在 C 端 H5 中新增银行卡管理能力，参考支付宝银行卡模块的交互形态：

- 首页功能入口「帮助」替换为「银行卡」。
- 用户可添加银行卡（模拟四要素校验）、查看银行卡列表、查看卡片详情、设置默认卡、解除绑定。
- 打通 前端（frontend-h5）→ 网关（gateway）→ 账户中心（account-center）→ 数据库（account_db）全链路。

### 范围说明

- 本期只做卡管理，不改造充值、转账的资金来源（后续阶段再打通「选卡支付/提现」）。
- 绑卡采用「卡号自动识别银行与卡类型 + 表单校验」的模拟四要素流程，不发真实短信验证码。

### 敏感数据约束

- 完整卡号、身份证号、手机号只在绑卡请求中出现一次：前端不落地存储，后端只存掩码值，日志不落明文。
- 后端存储字段仅包含：卡号前 6 位 BIN、卡号后 4 位、持卡人姓名掩码、证件号掩码、手机号掩码。

## 2. 交互流程设计（参考支付宝）

### 2.1 首页入口

首页功能入口「帮助」替换为「银行卡」（图标 💳），点击进入 `/h5/bank-cards`。

### 2.2 卡列表页 `/h5/bank-cards`

- 卡片式展示每张已绑定银行卡：银行主题渐变背景 + 银行名称 + 卡号掩码 `**** **** **** 1234` + 卡类型（储蓄卡/信用卡）+ 默认卡角标。
- 页面底部提供「添加银行卡」入口。
- 空态展示引导文案与添加入口。
- 该页为二级页（非底部 Tab），每次进入正常加载数据。

### 2.3 卡详情页 `/h5/bank-cards/:id`

点击列表卡片进入，展示：

- 卡号掩码、卡类型、银行名称、持卡人掩码姓名、预留手机号掩码。
- 管理区：
  - 「设为默认卡」：非默认卡可设置，设置成功后原默认卡自动取消。
  - 「解除绑定」：需弹窗二次确认；解绑后卡片从列表消失。
- 解绑的是默认卡时，系统自动把该用户最早绑定的其余活动卡递补为默认卡；无其他卡则无默认卡。

### 2.4 添加银行卡页 `/h5/bank-cards/add`

1. 卡号输入按 4 位一组分隔展示，输入过程中实时用前端 BIN 字典识别银行与卡类型并展示（如「中国工商银行 · 储蓄卡」）。
2. 未识别的卡 BIN 提示「暂不支持该银行」。
3. 识别成功后展开表单：持卡人姓名、身份证号、银行预留手机号。
4. 前端格式校验：身份证 18 位、手机号 11 位。
5. 提交后后端做 Luhn 校验与模拟四要素校验，成功则 Toast 提示并返回卡列表页。

### 2.5 状态流转

```
绑定记录（bank_card）：
绑卡成功 → ACTIVE（已绑定，可设默认、可解绑）
解绑     → UNBOUND（绑定记录终态，不可重激活；重绑生成新绑定记录）

注册记录（bank_card_registration）：
注册成功     → REGISTERED（可绑定）
绑卡成功     → BOUND
解绑时释放   → REGISTERED（重新回到可绑定状态，支持重绑）
```

## 3. 数据库设计（account_db）

Flyway 迁移文件：`backend/account-center/src/main/resources/db/migration/V202608081000__create_bank_card_table.sql`

```sql
-- 银行卡绑定表：只存掩码与 BIN/尾号，禁止存完整卡号、证件号、手机号明文。
CREATE TABLE IF NOT EXISTS account_db.bank_card (
    card_id CHAR(26) NOT NULL COMMENT '银行卡 ID，26 位字符串，沿用账户中心既有 ID 约定',
    user_id CHAR(26) NOT NULL COMMENT '所属用户 ID',
    account_id CHAR(26) NOT NULL COMMENT '关联的个人账户 ID',
    bank_code VARCHAR(32) NOT NULL COMMENT '银行编码，如 ICBC、CMB',
    bank_name VARCHAR(64) NOT NULL COMMENT '银行名称',
    card_type VARCHAR(16) NOT NULL COMMENT '卡类型：DEBIT 借记卡，CREDIT 信用卡',
    card_bin CHAR(6) NOT NULL COMMENT '卡号前 6 位 BIN，用于银行识别',
    card_last4 CHAR(4) NOT NULL COMMENT '卡号后 4 位',
    holder_masked VARCHAR(64) NOT NULL COMMENT '持卡人姓名掩码，如 张*三',
    id_card_masked VARCHAR(32) NOT NULL COMMENT '身份证号掩码，如 3301**********1234',
    phone_masked VARCHAR(16) NOT NULL COMMENT '预留手机号掩码，如 138****5678',
    is_default TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否默认卡，同一用户至多一张',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE 已绑定，UNBOUND 已解绑（终态）',
    unbound_at DATETIME(3) NULL COMMENT '解绑时间',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (card_id),
    KEY idx_bank_card_user_status (user_id, status),
    CONSTRAINT ck_bank_card_type CHECK (card_type IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_bank_card_status CHECK (status IN ('ACTIVE', 'UNBOUND'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

### 约束说明

- **重复绑卡**：同一用户重复绑定同一张卡（BIN + 尾号相同且处于 ACTIVE）在应用层校验拒绝；因解绑后允许重绑，唯一索引无法用简单列组合表达。
- **默认卡唯一**：同一用户至多一张默认卡，由应用层在事务内「先清旧默认、再置新默认」的条件更新保证。
- **ID 规范**：`card_id` 沿用账户中心既有 26 位字符串 ID 约定（UUID 去横线截断，与 `CreditJobService`、`AccountApplicationService` 等一致），存储类型 CHAR(26)。

## 4. 接口契约

网关路径前缀 `/api/v1/bank-cards/**`，路由目标 account-center，用户维度限流。

| 方法 | 路径 | 能力 | 说明 |
|---|---|---|---|
| GET | `/api/v1/bank-cards` | 我的银行卡列表 | 仅返回 ACTIVE 卡，默认卡在前 |
| POST | `/api/v1/bank-cards` | 绑定银行卡 | 请求体含完整卡号、姓名、证件号、手机号；服务端 Luhn 校验、模拟四要素校验、重复绑卡校验；首张卡自动设为默认 |
| GET | `/api/v1/bank-cards/{cardId}` | 卡详情 | 全部字段为掩码 |
| POST | `/api/v1/bank-cards/{cardId}/full-card-number` | 查看完整卡号 | 请求体携 `paymentProof`（用途 `BANK_CARD_NUMBER_VIEW` 签发的一次性证明，原子消费不可重放）；证明不进 URL；从注册表取完整卡号明文，客户端仅内存展示 |
| PUT | `/api/v1/bank-cards/{cardId}/default` | 设为默认卡 | 事务内先清旧默认再置新 |
| DELETE | `/api/v1/bank-cards/{cardId}` | 解绑 | 软删：status=UNBOUND，记录 unbound_at |

### 通用约定

- 用户身份只取网关可信请求头 `X-User-Id`，不接受客户端提交的用户 ID 或卡归属。
- 响应统一 `ApiResponse` 包装，透传 `X-Request-Id` / `X-Trace-Id`。
- 请求使用 `application/json`。

### 绑卡请求/响应示例

```json
// POST /api/v1/bank-cards 请求
{
  "cardNumber": "6222020200112233445",
  "holderName": "张三",
  "idCard": "330106199001011234",
  "phone": "13800005678"
}

// 响应（data 部分，全部掩码）
{
  "cardId": "20260808000001",
  "bankCode": "ICBC",
  "bankName": "中国工商银行",
  "cardType": "DEBIT",
  "cardLast4": "3445",
  "holderMasked": "张*",
  "phoneMasked": "138****5678",
  "isDefault": true,
  "createdAt": "2026-08-08T10:00:00Z"
}
```

## 5. 后端设计（account-center，DDD 分层）

依赖方向遵循 `interfaces -> application -> domain <- infrastructure`：

| 层 | 包 | 内容 |
|---|---|---|
| 接口层 | `interfaces/bankcard` | `BankCardController` + API DTO（请求含完整卡号，响应只含掩码字段） |
| 应用层 | `application/bankcard` | `BankCardApplicationService`：查用户账户 → Luhn 与格式校验 → 重复绑卡检查 → 掩码化 → 落库；设默认与解绑的不变量（默认至多一张、解绑默认卡后递补最早活动卡）在此实现；事务边界在此层 |
| 领域层 | `domain/bankcard` | `BankCard` 聚合（掩码化静态工厂、状态流转 ACTIVE→UNBOUND，UNBOUND 为终态不可重激活）、`BankCardRepository` 仓储接口、银行卡识别值对象（BIN→银行编码/名称/卡类型，后端兜底字典）；不依赖 Spring MVC/MyBatis |
| 基础设施层 | `infrastructure/bankcard` | Mapper + PO，按 account-center 现有持久化方式实现 |

### 关键业务规则

1. **Luhn 校验**：卡号必须通过 Luhn 算法且长度 16-19 位，否则返回 `BANK_CARD_INVALID`。
2. **模拟四要素校验**：校验姓名非空、身份证 18 位格式、手机号 11 位格式；格式不通过返回 `BANK_CARD_HOLDER_INVALID`。
3. **重复绑卡**：同用户同 BIN+尾号已存在 ACTIVE 卡，返回 `BANK_CARD_ALREADY_BOUND`。
4. **绑卡上限**：每用户最多 10 张活动卡，超限返回 `BANK_CARD_LIMIT_EXCEEDED`。
5. **首张卡自动默认**：用户无任何 ACTIVE 卡时，新绑卡自动设为默认。
6. **设默认互斥**：事务内先把该用户其他卡 `is_default` 清零，再置新卡为默认。
7. **解绑递补**：解绑的是默认卡且用户还有其他 ACTIVE 卡时，最早绑定的一张递补为默认。
8. **终态保护**：UNBOUND 卡不可再设默认、不可再次解绑，返回 `BANK_CARD_ALREADY_UNBOUND`；卡不存在或不属于当前用户返回 `BANK_CARD_NOT_FOUND`。

## 6. 注册流程（三要素交叉校验）

银行卡注册（`POST /api/v1/bank-card-registrations`）不再是单纯的格式校验：须先绑定身份且三要素与已绑定身份一致，才能生成卡号。

### 校验顺序

```
1. 格式校验：姓名 2-32 位、身份证按项目统一口径、手机号 11 位
   不通过 → BANK_CARD_HOLDER_INVALID (422)
2. 三要素交叉比对（调用 user-center /internal/v1/identity/verify）：
   - 用户未绑定身份 → IDENTITY_NOT_BOUND (422)，前端引导跳转身份绑定页
   - 姓名/身份证哈希/手机号任一不符 → IDENTITY_MISMATCH (422)
   - 校验服务不可用 → 系统类异常 (503)，不落库
3. 校验通过 → 查 BIN → 生成卡号 → 落库 bank_card_registration → 返回完整卡号
```

### 统一身份证校验口径

全系统身份证号校验唯一标准（user-center `IdCardValidator`、account-center 同规则实现、前端 `validateIdCard` 三者同源）：

- 18 位格式：17 位数字 + 1 位数字或 X（末位不区分大小写）；
- 第 7-14 位出生日期必须真实存在且介于 1900-01-01 至今；
- 按产品决策不做 GB 11643 MOD 11-2 校验位验证（演示环境允许编造号码）；
- account-center 因仓库边界约束维护同规则独立实现，两侧口径变更时必须同步。

### 错误码（均已在 error-codes.yaml 定义，无需新增）

| code | httpStatus | 触发场景 |
|---|---|---|
| BANK_CARD_HOLDER_INVALID | 422 | 姓名/身份证/手机号格式不合规 |
| IDENTITY_NOT_BOUND | 422 | 用户尚未绑定身份信息 |
| IDENTITY_MISMATCH | 422 | 三要素与已绑定身份不一致（统一提示，不区分具体字段） |

### 前端交互

- `BankCardAdd` 页身份证校验复用 `validateIdCard`（与身份绑定页同函数）；
- 收到 `IDENTITY_NOT_BOUND` 时用 `Dialog.confirm` 引导跳转 `/h5/identity-bind`；
- 其他错误直接展示后端中文 message。

## 7. 解绑与重新绑定流程

解绑不是“删了就没了”：绑定记录软删的同时必须释放注册记录，否则该卡永远无法重绑（历史缺陷，V1.17 修复）。

### 解绑处理（DELETE /api/v1/bank-cards/{cardId}，同一事务）

```
1. bank_card CAS 置为 UNBOUND（终态，保留历史作审计）
2. 按 用户+BIN+尾号 定位 BOUND 状态的注册记录：
   - 找到 → 条件更新释放回 REGISTERED（仅 BOUND → REGISTERED，防并发重复释放）
   - 未找到 → 静默跳过（兼容无注册记录的旧绑定数据）
   - 释放失败 → 版本冲突整体回滚，禁止出现“卡已解绑但注册记录仍 BOUND”的中间态
3. 解绑的是默认卡 → 递补最早绑定的活动卡为默认
```

### 重新绑定

- 交互复用现有绑卡流程（`POST /api/v1/bank-cards`）：用户凭注册时获得的完整卡号与三要素重新提交，无一键重绑接口；
- 校验链路与新绑完全一致（注册记录存在且 REGISTERED、三要素哈希匹配、user-center 交叉比对）；
- 每次重绑生成新的 ACTIVE 绑定记录，历史 UNBOUND 记录保留；
- 绑卡上限、首张卡自动默认、重复绑卡拦截（仅查 ACTIVE）均不受历史解绑记录影响。

### 兼容性与边界

- `status` 列为 VARCHAR 无 CHECK 约束，取值复用 REGISTERED/BOUND，无需数据库迁移；
- 按 BIN+尾号定位注册记录存在理论碰撞可能，取第一条 BOUND 记录（模拟系统可接受）；
- 完整卡号仅在注册响应中返回一次，重绑同样需要用户自行持有完整卡号。

## 8. 错误码

新增错误码需三处同步（error-codes.yaml、account-center 错误码枚举、OpenAPI 错误响应）并补充契约一致性测试：

| code | httpStatus | message |
|---|---|---|
| BANK_CARD_INVALID | 422 | 银行卡号无效或暂不支持 |
| BANK_CARD_HOLDER_INVALID | 422 | 持卡人信息校验未通过 |
| BANK_CARD_ALREADY_BOUND | 409 | 该银行卡已绑定 |
| BANK_CARD_NOT_FOUND | 404 | 银行卡不存在 |
| BANK_CARD_ALREADY_UNBOUND | 409 | 银行卡已解绑 |
| BANK_CARD_LIMIT_EXCEEDED | 422 | 绑定银行卡数量已达上限 |

## 9. 前端设计（frontend-h5）

| 文件 | 说明 |
|---|---|
| `src/services/bankCard.ts` | 列表、绑卡、详情、设默认、解绑五个调用，类型与 OpenAPI 对齐 |
| `src/pages/h5/BankCards/index.tsx` + less | 卡列表页（支付宝卡样式：渐变卡面、掩码卡号、默认角标） |
| `src/pages/h5/BankCardAdd/index.tsx` + less | 绑卡页：内置 BIN 字典常量（工行、建行、农行、中行、招行、交行、邮储等常见前缀），4 位分组输入与实时识别 |
| `src/pages/h5/BankCardDetail/index.tsx` + less | 详情页：掩码信息展示 + 设默认/解绑管理 |
| `config/routes.ts` | 新增 `/h5/bank-cards`、`/h5/bank-cards/add`、`/h5/bank-cards/:id`（auth: true） |
| `src/layouts/H5Layout/index.tsx` | titleMap 增加「银行卡」「添加银行卡」「卡片详情」 |
| `src/pages/h5/Home/index.tsx` | 「帮助」入口替换为「银行卡」，跳转 `/h5/bank-cards` |

### 前端 BIN 字典（示例）

| BIN 前缀 | 银行 | 编码 |
|---|---|---|
| 622202、621226 | 中国工商银行 | ICBC |
| 621700、622700 | 中国建设银行 | CCB |
| 622848、625996 | 中国农业银行 | ABC |
| 621661、622768 | 中国银行 | BOC |
| 621483、622588 | 招商银行 | CMB |
| 622262、622260 | 交通银行 | BCOMM |
| 621098、622188 | 中国邮政储蓄银行 | PSBC |

卡类型判断：BIN 字典标注优先，未标注时按卡号长度启发式判断（16 位倾向信用卡，17-19 位倾向借记卡），最终以服务端识别结果为准。

## 10. 网关配置

`backend/gateway/src/main/resources/application.yml` 中 account-center 路由的 predicate 追加 `/api/v1/bank-cards/**`，沿用用户维度限流（userKeyResolver，60 令牌/桶）。

## 11. 测试要求

### 后端单测（account-center）

- Luhn 校验正例与反例。
- 掩码化规则（姓名、证件号、手机号）。
- 重复绑卡拒绝。
- 首张卡自动默认。
- 设默认互斥（旧默认卡被清零）。
- 解绑终态保护（不可再解绑、不可设默认）。
- 解绑默认卡后递补最早活动卡。

### 契约测试

- 错误码枚举与 `error-codes.yaml` 逐项一致性校验补充新增六个错误码。

### 前端

- `npm run type-check` 不新增类型错误。

## 12. 文档同步清单（AGENTS.md 强制项）

| 文档 | 更新内容 |
|---|---|
| `docs/minialalipay/minialalipay-system-analysis.md` | 新增银行卡能力说明（账户中心限界上下文、接口、状态流转 ACTIVE→UNBOUND） |
| `docs/minialalipay/minialalipay-database-design.md` | 新增 bank_card 表说明 |
| `contracts/openapi/minialalipay-api.yaml` | 新增 5 个操作、请求/响应 Schema 与错误响应 |
| `contracts/error-codes/error-codes.yaml` | 新增 6 个银行卡错误码 |
| `AGENTS.md` | 网关路由表追加 `/api/v1/bank-cards/**` → account-center |

## 13. 验证方式

1. `mvn -pl account-center test` 全部通过。
2. 启动服务后联调全链路：绑卡 → 列表 → 详情 → 设默认 → 解绑。
3. 前端手工验证：首页入口进入、卡号实时识别、绑卡成功、列表/详情掩码展示、解绑二次确认、默认卡递补。
