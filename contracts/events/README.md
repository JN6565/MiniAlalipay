# 事件契约说明

本目录用于存放版本化事件 Schema。事件只表达已经发生的事实，例如 `TransactionSucceeded`；消费者必须使用 Inbox 去重，且不得反向修改事件生产方的数据表。
