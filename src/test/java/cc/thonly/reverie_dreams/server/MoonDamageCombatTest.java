package cc.thonly.reverie_dreams.server;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.UUID;

public class MoonDamageCombatTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        DamageSource source = new DamageSource(Holder.direct(new DamageType("moon_test", 0)));
        DamageSource other = new DamageSource(Holder.direct(new DamageType("moon_other", 0)));
        Field currentField = MoonDamageCombat.class.getDeclaredField("CURRENT");
        currentField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ThreadLocal<Object> current = (ThreadLocal<Object>) currentField.get(null);
        Class<?> contextClass = Class.forName(MoonDamageCombat.class.getName() + "$HitContext");
        Constructor<?> constructor = contextClass.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Field suspended = contextClass.getDeclaredField("suspended");
        suspended.setAccessible(true);
        MoonDamageTracker tracker = new MoonDamageTracker();
        MoonDamageTracker.PendingHit pending = tracker.prepare(UUID.randomUUID(), UUID.randomUUID(), false,
                "minecraft:netherite_sword", 3, 1, true, 0, true);
        try {
            check(!MoonDamageCombat.bypassesCooldown(null, source), "cooldown bypass leaked without context");
            current.set(constructor.newInstance(null, source, pending));
            check(MoonDamageCombat.bypassesCooldown(null, source), "active source was not scoped");
            check(!MoonDamageCombat.bypassesCooldown(null, other), "unrelated source received cooldown bypass");
            check(MoonDamageCombat.addTrueDamage(null, other, 0.5f) == 0.5f, "unrelated damage received true damage");
            check(MoonDamageCombat.addTrueDamage(null, source, 0.5f) == 3.5f, "post-absorption true damage missing");
            check(MoonDamageCombat.addTrueDamage(null, source, 0.5f) == 0.5f, "true damage applied twice");
            check(MoonDamageCombat.restoreAbsorption(null, source, -2.5f) == 0.5f, "true damage consumed absorption");
            System.out.println("PASS scoped true damage, one application and absorption compensation");

            current.set(constructor.newInstance(null, source, pending));
            MoonDamageCombat.blocked(null, source, 10, 10);
            check(MoonDamageCombat.addTrueDamage(null, source, 0) == 0, "full shield received true damage");
            check(MoonDamageCombat.restoreAbsorption(null, source, 4) == 4, "blocked damage altered absorption");
            System.out.println("PASS fully blocked hit cannot receive true damage");

            current.set(constructor.newInstance(null, source, pending));
            MoonDamageCombat.blocked(null, source, 10, 5);
            check(MoonDamageCombat.addTrueDamage(null, source, 0) == 3, "partial blocking removed valid true damage");
            System.out.println("PASS partial blocking preserves the accepted-hit true portion");

            Object context = constructor.newInstance(null, source, pending);
            current.set(context);
            suspended.setBoolean(context, true);
            check(!MoonDamageCombat.bypassesCooldown(null, source), "suspended source received bypass");
            check(MoonDamageCombat.addTrueDamage(null, source, 1) == 1, "suspended hit received true damage");
            suspended.setBoolean(context, false);
            boolean success = MoonDamageCombat.hurt(null, null, null, source, 2, amount -> {
                check(amount == 2, "nested damage received an extra normal portion");
                check(!MoonDamageCombat.bypassesCooldown(null, source), "nested damage inherited outer scope");
                check(current.get() == context, "nested scope discarded deferred cleanup context");
                return true;
            });
            check(success && MoonDamageCombat.bypassesCooldown(null, source), "outer context was not restored");
            System.out.println("PASS nested damage suspends effects while preserving cleanup context");

            try {
                MoonDamageCombat.hurt(null, null, null, source, 2, amount -> { throw new IllegalStateException("expected"); });
                throw new AssertionError("nested exception was swallowed");
            } catch (IllegalStateException expected) {
                check(MoonDamageCombat.bypassesCooldown(null, source), "exception left outer context suspended");
            }
            System.out.println("PASS nested exception restores outer damage scope");
        } finally {
            current.remove();
        }
        check(!MoonDamageCombat.bypassesCooldown(null, source), "test cleanup retained damage scope");
    }
}
