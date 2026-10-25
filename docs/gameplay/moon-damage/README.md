# 月伤文档

先阅读 [当前实施规则](current-rules.md) 与 [实施计划](implementation-plan.md)。此前讨论、源码说明和静态算例作为历史记录保留，不能用旧文档的“未实现”或“待定”描述覆盖当前规则或实际交付状态。

| 文档 | 用途 |
| --- | --- |
| [current-rules.md](current-rules.md) | 当前规则与本轮实施裁定 |
| [implementation-plan.md](implementation-plan.md) | 实施步骤与验证重点 |
| [moon-damage-lts.md](moon-damage-lts.md) | 原 LTS 行为源码说明 |
| [moon-damage-design-discussion.md](moon-damage-design-discussion.md) | 历次讨论记录，含已经放弃的方案 |
| [moon-damage-weapon-statistics.md](moon-damage-weapon-statistics.md) | 原版武器及锋利基准静态算例 |
| [moon-damage-proposal-v4.md](moon-damage-proposal-v4.md) | 方案演进与全场景伤害算例 |
| [moon-damage-mechanism-audit.md](moon-damage-mechanism-audit.md) | 机制边界、源码证据与验收关注点 |
| [moon-damage-server-load-estimate.md](moon-damage-server-load-estimate.md) | 按武器数量和有效输入估算的理论压力 |

[源码策划注释归档](../source-notes/README.md) 与源码分开存放。静态算例、纯 Java 状态测试、编译、Mixin 实际接入和真实战斗验收分别记录，不相互替代。
