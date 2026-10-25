package cc.thonly.reverie_dreams.server;

import java.util.UUID;

public class MoonDamageTrackerTest {
    private static final UUID WEAPON = new UUID(0, 1);
    private static final UUID OTHER_WEAPON = new UUID(0, 2);
    private static final UUID TARGET = new UUID(0, 3);
    private static final UUID OTHER_TARGET = new UUID(0, 4);
    private static int passed;

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void near(float actual, float expected) {
        check(Math.abs(actual - expected) < 0.00001f, actual + " != " + expected);
    }

    private static MoonDamageTracker.PendingHit hit(MoonDamageTracker tracker, UUID weapon, UUID target, boolean player, long tick) {
        return tracker.prepare(weapon, target, player, "minecraft:netherite_sword", 3, 1, true, tick, true);
    }

    private static MoonDamageTracker.PendingHit accept(MoonDamageTracker tracker, UUID weapon, UUID target, boolean player, long tick) {
        MoonDamageTracker.PendingHit hit = hit(tracker, weapon, target, player, tick);
        check(hit != null, "hit rejected at " + tick);
        check(tracker.accept(hit, hit.trueDamage() > 0), "hit not accepted at " + tick);
        return hit;
    }

    private static void run(String name, Runnable test) {
        test.run();
        passed++;
        System.out.println("PASS " + name);
    }

    private static MoonDamageTracker seededTargets() {
        return seededTargets(true);
    }

    private static MoonDamageTracker seededTargets(boolean trueApplied) {
        MoonDamageTracker tracker = new MoonDamageTracker();
        for (int n = 0; n < 4; n++) accept(tracker, WEAPON, TARGET, true, n * 10);
        accept(tracker, WEAPON, OTHER_TARGET, true, 40);
        accept(tracker, WEAPON, OTHER_TARGET, true, 50);
        MoonDamageTracker.PendingHit fifth = hit(tracker, WEAPON, TARGET, true, 60);
        near(fifth.trueDamage(), 2);
        check(tracker.accept(fifth, trueApplied), "seed fifth rejected");
        accept(tracker, WEAPON, OTHER_TARGET, true, 70);
        accept(tracker, WEAPON, OTHER_TARGET, true, 80);
        return tracker;
    }

    private static long trueTick(MoonDamageTracker tracker) {
        try {
            var weapons = MoonDamageTracker.class.getDeclaredField("weapons");
            weapons.setAccessible(true);
            Object state = ((java.util.Map<?, ?>) weapons.get(tracker)).get(WEAPON);
            var hasTick = state.getClass().getDeclaredField("hasTrueTick");
            var tick = state.getClass().getDeclaredField("trueTick");
            hasTick.setAccessible(true);
            tick.setAccessible(true);
            return hasTick.getBoolean(state) ? tick.getLong(state) : -1;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    public static void main(String[] args) {
        run("first hit at tick zero is eligible", () -> {
            MoonDamageTracker tracker = new MoonDamageTracker();
            MoonDamageTracker.PendingHit hit = hit(tracker, WEAPON, TARGET, true, 0);
            check(hit != null, "initial hit has a synthetic cooldown");
            near(hit.normalDamage(), 3);
            near(hit.trueDamage(), 0);
        });
        run("failed hits do not reserve state or count", () -> {
            MoonDamageTracker tracker = new MoonDamageTracker();
            for (int n = 0; n < 5; n++) {
                MoonDamageTracker.PendingHit hit = hit(tracker, WEAPON, TARGET, true, 0);
                check(hit != null && !hit.fifth(), "prepare advanced state");
            }
            check(tracker.size() == 0, "prepare allocated retained state");
            check(!accept(tracker, WEAPON, TARGET, true, 0).fifth(), "failed hits counted");
        });
        run("player ten tick boundary", () -> {
            MoonDamageTracker tracker = new MoonDamageTracker();
            accept(tracker, WEAPON, TARGET, true, 0);
            check(hit(tracker, WEAPON, OTHER_TARGET, true, 9) == null, "target switch reset interval");
            check(hit(tracker, WEAPON, TARGET, true, 10) != null, "tenth tick rejected");
        });
        run("nonplayer one tick boundary", () -> {
            MoonDamageTracker tracker = new MoonDamageTracker();
            accept(tracker, WEAPON, TARGET, false, 0);
            check(hit(tracker, WEAPON, OTHER_TARGET, false, 0) == null, "same tick crossed targets");
            check(hit(tracker, WEAPON, OTHER_TARGET, false, 1) != null, "next tick rejected");
        });
        run("weapon cooldowns and ownership are independent", () -> {
            MoonDamageTracker tracker = new MoonDamageTracker();
            accept(tracker, WEAPON, TARGET, true, 0);
            check(hit(tracker, OTHER_WEAPON, TARGET, true, 0) != null, "separate weapon shared interval");
            check(hit(tracker, WEAPON, TARGET, true, 1) == null, "weapon lost interval on reuse");
        });
        run("pvp fifth substitutes capped true damage", () -> {
            MoonDamageTracker tracker = new MoonDamageTracker();
            for (int n = 0; n < 4; n++) near(accept(tracker, WEAPON, TARGET, true, n * 10).normalDamage(), 3);
            MoonDamageTracker.PendingHit fifth = accept(tracker, WEAPON, TARGET, true, 40);
            check(fifth.fifth(), "fifth not recognized");
            near(fifth.normalDamage(), 0);
            near(fifth.trueDamage(), 2);
            check(!accept(tracker, WEAPON, TARGET, true, 50).fifth(), "fifth reused old layers");
        });
        run("pve fifth crosses targets", () -> {
            MoonDamageTracker tracker = new MoonDamageTracker();
            for (int n = 0; n < 4; n++) near(accept(tracker, WEAPON, new UUID(2, n), false, n).trueDamage(), 3);
            MoonDamageTracker.PendingHit fifth = accept(tracker, WEAPON, OTHER_TARGET, false, 4);
            check(fifth.fifth(), "cross target fifth missing");
            near(fifth.trueDamage(), 3.75f);
            near(accept(tracker, WEAPON, TARGET, false, 5).trueDamage(), 3);
        });
        run("pvp target layers remain separate", () -> {
            MoonDamageTracker tracker = new MoonDamageTracker();
            for (int n = 0; n < 4; n++) accept(tracker, WEAPON, TARGET, true, n * 10);
            check(!accept(tracker, WEAPON, OTHER_TARGET, true, 40).fifth(), "different target inherited layers");
            check(accept(tracker, WEAPON, TARGET, true, 50).fifth(), "original target lost layers");
        });
        run("pvp and pve intervals and counters stay separate", () -> {
            MoonDamageTracker tracker = new MoonDamageTracker();
            for (int n = 0; n < 4; n++) accept(tracker, WEAPON, TARGET, false, n);
            check(!accept(tracker, WEAPON, TARGET, true, 3).fifth(), "pve fed pvp counter");
            near(accept(tracker, WEAPON, OTHER_TARGET, false, 4).trueDamage(), 3.75f);
        });
        run("low tiers cannot convert player fifth to true", () -> {
            MoonDamageTracker tracker = new MoonDamageTracker();
            for (int n = 0; n < 5; n++) {
                MoonDamageTracker.PendingHit hit = tracker.prepare(WEAPON, TARGET, true, "minecraft:iron_sword", 3, 0.75f, false, n * 10, true);
                check(hit != null, "low tier rejected");
                near(hit.normalDamage(), 2.25f);
                near(hit.trueDamage(), 0);
                check(tracker.accept(hit, false), "low tier not committed");
            }
        });
        run("day factor applies before final cap", () -> {
            MoonDamageTracker tracker = new MoonDamageTracker();
            MoonDamageTracker.PendingHit fifth = null;
            for (int n = 0; n < 5; n++) {
                fifth = tracker.prepare(WEAPON, TARGET, true, "minecraft:netherite_sword", 3, 1, true, n * 10, false);
                check(tracker.accept(fifth, fifth.trueDamage() > 0), "day hit not committed");
            }
            near(fifth.trueDamage(), 1.125f);
        });
        run("rolling window includes exactly sixty ticks", () -> {
            MoonDamageTracker tracker = new MoonDamageTracker();
            for (long tick : new long[]{0, 15, 30, 45}) accept(tracker, WEAPON, TARGET, true, tick);
            check(accept(tracker, WEAPON, TARGET, true, 60).fifth(), "inclusive boundary discarded");
        });
        run("expired layers cannot be kept alive indefinitely", () -> {
            MoonDamageTracker tracker = new MoonDamageTracker();
            for (long tick : new long[]{0, 40, 80, 120, 160}) {
                check(!accept(tracker, WEAPON, TARGET, true, tick).fifth(), "rolling window extended stale layers");
            }
        });
        run("rolling window retains recent hits after oldest expires", () -> {
            MoonDamageTracker tracker = new MoonDamageTracker();
            for (long tick : new long[]{0, 30, 40, 50, 61}) check(!accept(tracker, WEAPON, TARGET, true, tick).fifth(), "expired first hit counted");
            check(accept(tracker, WEAPON, TARGET, true, 71).fifth(), "recent hits erased with oldest");
        });
        run("target switch cannot bypass interval before the true cooldown boundary", () -> {
            MoonDamageTracker tracker = seededTargets();
            check(trueTick(tracker) == 60, "true cooldown did not follow the weapon");
            check(hit(tracker, WEAPON, OTHER_TARGET, true, 89) == null, "target switch bypassed ten tick interval");
            check(accept(tracker, WEAPON, OTHER_TARGET, true, 90).fifth(), "rejected early hit consumed layers");
        });
        run("true cooldown permits exactly thirty ticks", () -> {
            MoonDamageTracker tracker = seededTargets();
            near(accept(tracker, WEAPON, OTHER_TARGET, true, 90).trueDamage(), 2);
        });
        run("unapplied true damage does not start true cooldown", () -> {
            MoonDamageTracker tracker = seededTargets(false);
            check(trueTick(tracker) == -1, "unapplied true damage started cooldown");
            near(accept(tracker, WEAPON, OTHER_TARGET, true, 90).trueDamage(), 2);
        });
        run("duplicate or stale pending hits cannot commit twice", () -> {
            MoonDamageTracker tracker = new MoonDamageTracker();
            MoonDamageTracker.PendingHit a = hit(tracker, WEAPON, TARGET, false, 0);
            MoonDamageTracker.PendingHit b = hit(tracker, WEAPON, OTHER_TARGET, false, 0);
            check(tracker.accept(a, true), "initial commit rejected");
            check(!tracker.accept(a, true), "duplicate committed");
            check(!tracker.accept(b, true), "stale pending hit committed");
        });
        run("profile change clears layers but preserves interval", () -> {
            MoonDamageTracker tracker = new MoonDamageTracker();
            for (int n = 0; n < 4; n++) accept(tracker, WEAPON, TARGET, true, n * 10);
            check(tracker.prepare(WEAPON, TARGET, true, "other:sword", 3, 0.9f, true, 39, true) == null, "new profile refreshed interval");
            MoonDamageTracker.PendingHit changed = tracker.prepare(WEAPON, TARGET, true, "other:sword", 3, 0.9f, true, 40, true);
            check(changed != null && !changed.fifth(), "item profile inherited four hits");
            check(tracker.accept(changed, false), "new profile rejected");
            MoonDamageTracker.PendingHit level = tracker.prepare(WEAPON, TARGET, true, "other:sword", 2, 0.9f, true, 50, true);
            check(level != null && !level.fifth(), "level change inherited stack");
            near(level.normalDamage(), 1.8f);
        });
        run("profile change preserves player true cooldown", () -> {
            MoonDamageTracker tracker = seededTargets();
            for (int n = 0; n < 5; n++) {
                MoonDamageTracker.PendingHit changed = tracker.prepare(WEAPON, OTHER_TARGET, true, "other:sword", 3, 0.9f, true, 90 + n * 10, true);
                check(tracker.accept(changed, changed.trueDamage() > 0), "changed profile commit failed");
                if (n == 0) check(trueTick(tracker) == 60, "profile change reset true cooldown");
                if (n == 4) near(changed.trueDamage(), 2);
            }
        });
        run("target lifecycle cleanup removes layers but not weapon cooldown", () -> {
            MoonDamageTracker tracker = new MoonDamageTracker();
            for (int n = 0; n < 4; n++) accept(tracker, WEAPON, TARGET, true, n * 10);
            tracker.clearTarget(TARGET);
            check(hit(tracker, WEAPON, TARGET, true, 31) == null, "target cleanup reset interval");
            check(!accept(tracker, WEAPON, TARGET, true, 40).fifth(), "target cleanup kept layers");
        });
        run("nonplayer fifth is unaffected by player true cooldown", () -> {
            MoonDamageTracker tracker = seededTargets();
            for (int n = 0; n < 5; n++) {
                MoonDamageTracker.PendingHit hit = accept(tracker, WEAPON, new UUID(5, n), false, 80 + n);
                near(hit.trueDamage(), n == 4 ? 3.75f : 3);
            }
        });
        run("expiry releases all state beyond window", () -> {
            MoonDamageTracker tracker = new MoonDamageTracker();
            accept(tracker, WEAPON, TARGET, true, 0);
            tracker.expire(60);
            check(tracker.size() == 1, "inclusive active state expired");
            tracker.expire(61);
            check(tracker.size() == 0, "expired weapon retained");
        });
        run("clear rejects previously prepared work", () -> {
            MoonDamageTracker tracker = new MoonDamageTracker();
            MoonDamageTracker.PendingHit hit = hit(tracker, WEAPON, TARGET, true, 0);
            tracker.clear();
            check(!tracker.accept(hit, false), "pre-clear pending hit reintroduced state");
            check(tracker.size() == 0, "clear retained state");
        });
        run("invalid damage profiles do not enter state", () -> {
            MoonDamageTracker tracker = new MoonDamageTracker();
            check(tracker.prepare(WEAPON, TARGET, true, "x", 0, 1, true, 0, true) == null, "zero enchant accepted");
            check(tracker.prepare(WEAPON, TARGET, true, "x", 3, Float.NaN, true, 0, true) == null, "NaN accepted");
            check(tracker.prepare(WEAPON, TARGET, true, "x", 3, -1, true, 0, true) == null, "negative weight accepted");
            check(tracker.size() == 0, "invalid profile retained state");
        });
        System.out.println("PASS " + passed + " moon tracker cases");
    }
}
