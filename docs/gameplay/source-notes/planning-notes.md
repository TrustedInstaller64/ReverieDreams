# 原源码策划与玩法注释归档

本次只迁出玩法规则、参数说明与设计意图注释，未更改这些文件中的可执行词法 token。普通技术注释、方法 API 文档、版权、分类标签、TODO 和历史注释代码保留。原文可能已过时，本文不替代当前策划或实际代码。

源行号为迁移前位置；空行予以保留，方便回溯。

## SpellCardFrameConfigs.java

源文件：[src/main/java/cc/thonly/reverie_dreams/data/danmaku/spellcard/SpellCardFrameConfigs.java](../../../src/main/java/cc/thonly/reverie_dreams/data/danmaku/spellcard/SpellCardFrameConfigs.java)

原第 61 行：

> ========== 第一波：初始环形星弹 ==========

原第 67 行：

> 几乎水平

原第 68 行：

> 全向环形

原第 74 行：

> ========== 第二波：交错火球层 ==========

原第 78 行：

> 延迟出现

原第 81 行：

> 水平带轻微扰动

原第 91 行：

> 稍微往上

原第 98 行：

> ========== 第三波：爆发式多层散射 ==========

## BulletTrajectory.java

源文件：[src/main/java/cc/thonly/reverie_dreams/data/danmaku/trajectory/BulletTrajectory.java](../../../src/main/java/cc/thonly/reverie_dreams/data/danmaku/trajectory/BulletTrajectory.java)

原第 17 行：

> 层级从 -2 到 0

原第 18 行：

> 层与层之间的距离（Z轴偏移）

原第 19 行：

> 子弹左右间距

原第 22 行：

> 用于控制左右子弹个数（从外往中心）

原第 25 行：

> 只在边缘发射，形成镂空结构（跳过中间）

## RingTrajectory.java

源文件：[src/main/java/cc/thonly/reverie_dreams/data/danmaku/trajectory/RingTrajectory.java](../../../src/main/java/cc/thonly/reverie_dreams/data/danmaku/trajectory/RingTrajectory.java)

原第 11 行：

> 子弹数量

原第 12 行：

> 半径（格）

原第 13 行：

> 起始角度偏移

原第 14 行：

> 是否朝外发射

## RoundTrajectory.java

源文件：[src/main/java/cc/thonly/reverie_dreams/data/danmaku/trajectory/RoundTrajectory.java](../../../src/main/java/cc/thonly/reverie_dreams/data/danmaku/trajectory/RoundTrajectory.java)

原第 14 行：

> 子弹数量

原第 15 行：

> 环形半径

原第 16 行：

> 起始角度偏移

## NPCOpenDoorGoal.java

源文件：[src/main/java/cc/thonly/reverie_dreams/entity/ai/goal/NPCOpenDoorGoal.java](../../../src/main/java/cc/thonly/reverie_dreams/entity/ai/goal/NPCOpenDoorGoal.java)

原第 81 行：

> 停止时如果门还开着就关门

## NPCOpenSilverChestGoal.java

源文件：[src/main/java/cc/thonly/reverie_dreams/entity/ai/goal/NPCOpenSilverChestGoal.java](../../../src/main/java/cc/thonly/reverie_dreams/entity/ai/goal/NPCOpenSilverChestGoal.java)

原第 150 行：

> 半径 3 格

## NPCChestClassificationGoal.java

源文件：[src/main/java/cc/thonly/reverie_dreams/entity/ai/goal/work/NPCChestClassificationGoal.java](../../../src/main/java/cc/thonly/reverie_dreams/entity/ai/goal/work/NPCChestClassificationGoal.java)

原第 248 行：

> 放一堆或全部（视需求可改为只放1）

## NPCCleanMonsterGoal.java

源文件：[src/main/java/cc/thonly/reverie_dreams/entity/ai/goal/work/NPCCleanMonsterGoal.java](../../../src/main/java/cc/thonly/reverie_dreams/entity/ai/goal/work/NPCCleanMonsterGoal.java)

原第 59 行：

> 处于工作原点水平方向拓展16格内的所有怪物

原第 61 行：

> 挑选离女仆最近的怪物

## NPCCloseToCropGoal.java

源文件：[src/main/java/cc/thonly/reverie_dreams/entity/ai/goal/work/NPCCloseToCropGoal.java](../../../src/main/java/cc/thonly/reverie_dreams/entity/ai/goal/work/NPCCloseToCropGoal.java)

原第 99 行：

> 寻路 农田按照平面考虑 应该不会有人在啥阴间的地方让女仆工作吧

## NPCFarmGoal.java

源文件：[src/main/java/cc/thonly/reverie_dreams/entity/ai/goal/work/NPCFarmGoal.java](../../../src/main/java/cc/thonly/reverie_dreams/entity/ai/goal/work/NPCFarmGoal.java)

原第 34 行：

> 计时器 避免种植太快显得不是人

## NPCSmeltGoal.java

源文件：[src/main/java/cc/thonly/reverie_dreams/entity/ai/goal/work/NPCSmeltGoal.java](../../../src/main/java/cc/thonly/reverie_dreams/entity/ai/goal/work/NPCSmeltGoal.java)

原第 210 行：

> 半径 3 格

## IceElementalEntity.java

源文件：[src/main/java/cc/thonly/reverie_dreams/entity/elemental/IceElementalEntity.java](../../../src/main/java/cc/thonly/reverie_dreams/entity/elemental/IceElementalEntity.java)

原第 135 行：

> 光照条件（火把等会提高亮度）

原第 138 行：

> 世界时间（0~23999，0~12000 白天，12000~23999 夜晚）

原第 140 行：

> 晚上时间段

## DanmakuEntity.java

源文件：[src/main/java/cc/thonly/reverie_dreams/entity/misc/DanmakuEntity.java](../../../src/main/java/cc/thonly/reverie_dreams/entity/misc/DanmakuEntity.java)

原第 406 行：

> ④ 给一个小容错（比如 +5°）

## BaseNPCLikeEntity.java

源文件：[src/main/java/cc/thonly/reverie_dreams/entity/npc/BaseNPCLikeEntity.java](../../../src/main/java/cc/thonly/reverie_dreams/entity/npc/BaseNPCLikeEntity.java)

原第 609 行：

> 无法检测具体行为 按0.015计算 略微提高消耗

## MoneyShopClerk.java

源文件：[src/main/java/cc/thonly/reverie_dreams/entity/villager/MoneyShopClerk.java](../../../src/main/java/cc/thonly/reverie_dreams/entity/villager/MoneyShopClerk.java)

原第 20 行：

> 等级 1：铜 <-> 银

原第 22 行：

> 铜 → 银

原第 27 行：

> 最大交易次数

原第 28 行：

> 村民经验

原第 31 行：

> 银 → 铜

原第 42 行：

> 等级 2：银 <-> 金

原第 44 行：

> 银 → 金

原第 53 行：

> 金 → 银

## MystiaModOffers.java

源文件：[src/main/java/cc/thonly/reverie_dreams/entity/villager/MystiaModOffers.java](../../../src/main/java/cc/thonly/reverie_dreams/entity/villager/MystiaModOffers.java)

原第 37 行：

> Lv.2：常见农作物，便宜交易

原第 55 行：

> Lv.3：中等水果

原第 68 行：

> Lv.4：稀有水果

## TreasureHuntingRod.java

源文件：[src/main/java/cc/thonly/reverie_dreams/item/weapon/TreasureHuntingRod.java](../../../src/main/java/cc/thonly/reverie_dreams/item/weapon/TreasureHuntingRod.java)

原第 118 行：

> 伤害和冷却

原第 123 行：

> 设置冷却

## BiomeModificationInit.java

源文件：[src/main/java/cc/thonly/reverie_dreams/world/BiomeModificationInit.java](../../../src/main/java/cc/thonly/reverie_dreams/world/BiomeModificationInit.java)

原第 143 行：

> 附近 16x8x16 范围内少于 3 个才允许生成

## FloatingIslandFeature.java

源文件：[src/main/java/cc/thonly/reverie_dreams/world/gen/feature/FloatingIslandFeature.java](../../../src/main/java/cc/thonly/reverie_dreams/world/gen/feature/FloatingIslandFeature.java)

原第 21 行：

> 顶面海拔：100~160

原第 24 行：

> 生成几个“半椭球”并叠加，顶面统一用同一平面（oy = 0 保证平顶）

原第 44 行：

> 20% 概率额外放草

原第 56 行：

> 每个岛 5~12 个装饰

原第 57 行：

> 限制树苗数量

原第 82 行：

> 小水塘（2×2 或 3×3）

原第 100 行：

> 以 topCenter 为顶面中心（y=0），向“下方”生成半椭球。
> 顶面整片为草；往下 2 层为泥土；更下为石头。

原第 123 行：

> 顶面：整片草；如果你想避免覆盖已有方块，可先判断 isAir

## 原注释与代码差异

`FloatingIslandFeature` 原第 44 行注释写“20%”，但保留的实际条件为 `random.nextFloat() < 0.1f`，即 10%。此次归档原文不代表调整生成概率，也未修正这一历史参数。
