# 事件契约

所有跨上下文 Outbox 事件必须符合 `event-envelope.schema.json`，事件类型和当前版本必须登记在 `event-types.yaml`。事件表示已经发生的事实；生产者不得重写已发布事件，消费者必须使用 Inbox 按 `eventId` 去重，并按 `eventType + eventVersion` 选择处理器。

不兼容变更必须提升主版本并保留旧消费者迁移期；新增可选字段提升次版本。事件不得携带密码、支付证明、确认令牌、二维码原始令牌、完整账号或模型内部推理。
