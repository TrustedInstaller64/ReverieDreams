package cc.thonly.reverie_dreams.server;

import cc.thonly.reverie_dreams.registry.content.RDEnchantments;
import cc.thonly.reverie_dreams.registry.content.component.RDDataComponents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MoonDamageCombat {
    private static final Map<MinecraftServer, ServerState> SERVERS = new HashMap<>();
    private static final ThreadLocal<HitContext> CURRENT = new ThreadLocal<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ServerState state = SERVERS.get(server);
            if (state == null) return;
            state.tick++;
            if (state.tick % 20 == 0) {
                state.tracker.expire(state.tick);
                state.identities.values().removeIf(identity -> state.tick - identity.lastTick > 60);
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof Player) clearTarget(entity);
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> clearTarget(oldPlayer));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> clearTarget(player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clearTarget(handler.player));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ServerState state = SERVERS.remove(server);
            if (state != null) state.tracker.clear();
        });
    }

    public static boolean hurt(LivingEntity attacker, Entity target, ItemStack weapon, DamageSource source, float damage, DamageCall original) {
        HitContext previous = CURRENT.get();
        if (previous != null) {
            boolean suspended = previous.suspended;
            previous.suspended = true;
            try {
                return original.hurt(damage);
            } finally {
                previous.suspended = suspended;
            }
        }
        LivingEntity livingTarget = target instanceof EnderDragonPart part ? part.parentMob : target instanceof LivingEntity living ? living : null;
        if (!(attacker.level() instanceof ServerLevel world) || livingTarget == null
                || weapon.isEmpty() || livingTarget.isDeadOrDying() || !Float.isFinite(damage) || damage <= 0) {
            return original.hurt(damage);
        }
        var enchantment = world.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(RDEnchantments.MOON_DAMAGE);
        int level = Math.min(3, EnchantmentHelper.getItemEnchantmentLevel(enchantment, weapon));
        if (level <= 0) return original.hurt(damage);
        MinecraftServer server = world.getServer();
        ServerState state = SERVERS.computeIfAbsent(server, key -> new ServerState());
        UUID weaponId = weaponId(state, attacker, weapon);
        String itemId = BuiltInRegistries.ITEM.getKey(weapon.getItem()).toString();
        MoonDamageWeapons.Profile profile = MoonDamageWeapons.profile(itemId);
        long time = Math.floorMod(server.overworld().getDayTime(), 24000L);
        MoonDamageTracker.PendingHit pending = state.tracker.prepare(weaponId, livingTarget.getUUID(), livingTarget instanceof Player,
                itemId, level, profile.weight(), profile.trueEligible(), state.tick, time >= 13000 && time < 23000);
        HitContext context = new HitContext(livingTarget, source, pending);
        float normal = pending == null ? 0 : pending.normalDamage();
        if (normal > 0 && hasPlatinumArmor(livingTarget)) normal *= 0.75f;
        CURRENT.set(context);
        try {
            boolean success = original.hurt(damage + normal);
            if (success && !context.fullyBlocked && pending != null) state.tracker.accept(pending, context.appliedTrueDamage > 0);
            return success;
        } finally {
            CURRENT.remove();
            for (UUID targetId : state.targetsToClear) state.tracker.clearTarget(targetId);
            state.targetsToClear.clear();
        }
    }

    public static boolean bypassesCooldown(LivingEntity target, DamageSource source) {
        return matches(CURRENT.get(), target, source);
    }

    public static void blocked(LivingEntity target, DamageSource source, float incoming, float blocked) {
        HitContext context = CURRENT.get();
        if (matches(context, target, source) && (incoming <= 0 || blocked >= incoming)) context.fullyBlocked = true;
    }

    public static float addTrueDamage(LivingEntity target, DamageSource source, float remaining) {
        HitContext context = CURRENT.get();
        if (!matches(context, target, source) || context.fullyBlocked || context.pending == null || context.appliedTrueDamage > 0) return remaining;
        context.appliedTrueDamage = context.pending.trueDamage();
        return remaining + context.appliedTrueDamage;
    }

    public static float restoreAbsorption(LivingEntity target, DamageSource source, float proposed) {
        HitContext context = CURRENT.get();
        return matches(context, target, source) ? proposed + context.appliedTrueDamage : proposed;
    }

    private static boolean matches(HitContext context, LivingEntity target, DamageSource source) {
        return context != null && !context.suspended && context.target == target && context.source == source;
    }

    private static boolean hasPlatinumArmor(LivingEntity target) {
        return isItem(target.getItemBySlot(EquipmentSlot.HEAD), "illagerexp:platinum_infused_netherite_helmet")
                && isItem(target.getItemBySlot(EquipmentSlot.CHEST), "illagerexp:platinum_infused_netherite_chestplate")
                && isItem(target.getItemBySlot(EquipmentSlot.LEGS), "illagerexp:platinum_infused_netherite_leggings")
                && isItem(target.getItemBySlot(EquipmentSlot.FEET), "illagerexp:platinum_infused_netherite_boots");
    }

    private static boolean isItem(ItemStack stack, String itemId) {
        return !stack.isEmpty() && itemId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    private static UUID weaponId(ServerState state, LivingEntity owner, ItemStack weapon) {
        UUID id = weapon.get(RDDataComponents.MOON_WEAPON_ID);
        WeaponIdentity identity = id == null ? null : state.identities.get(id);
        if (id == null || identity != null && identity.stack.get() != weapon && stillOwned(owner.getServer(), identity)) {
            id = UUID.randomUUID();
            weapon.set(RDDataComponents.MOON_WEAPON_ID, id);
        }
        state.identities.put(id, new WeaponIdentity(owner.getUUID(), owner.level().dimension(), new WeakReference<>(weapon), state.tick));
        return id;
    }

    private static boolean stillOwned(MinecraftServer server, WeaponIdentity identity) {
        ItemStack previous = identity.stack.get();
        if (previous == null || previous.isEmpty()) return false;
        ServerLevel world = server.getLevel(identity.dimension);
        Entity owner = world == null ? null : world.getEntity(identity.owner);
        if (!(owner instanceof LivingEntity living)) return false;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (living.getItemBySlot(slot) == previous) return true;
        }
        if (living instanceof Player player) {
            for (int index = 0; index < player.getInventory().getContainerSize(); index++) {
                if (player.getInventory().getItem(index) == previous) return true;
            }
        }
        return false;
    }

    private static void clearTarget(LivingEntity target) {
        MinecraftServer server = target.getServer();
        ServerState state = SERVERS.get(server);
        if (state == null) return;
        if (CURRENT.get() != null) state.targetsToClear.add(target.getUUID());
        else state.tracker.clearTarget(target.getUUID());
    }

    @FunctionalInterface
    public interface DamageCall {
        boolean hurt(float damage);
    }

    private static class ServerState {
        private final MoonDamageTracker tracker = new MoonDamageTracker();
        private final Map<UUID, WeaponIdentity> identities = new HashMap<>();
        private final Set<UUID> targetsToClear = new HashSet<>();
        private long tick;
    }

    private record WeaponIdentity(UUID owner, ResourceKey<Level> dimension, WeakReference<ItemStack> stack, long lastTick) {
    }

    private static class HitContext {
        private final LivingEntity target;
        private final DamageSource source;
        private final MoonDamageTracker.PendingHit pending;
        private boolean fullyBlocked;
        private boolean suspended;
        private float appliedTrueDamage;

        private HitContext(LivingEntity target, DamageSource source, MoonDamageTracker.PendingHit pending) {
            this.target = target;
            this.source = source;
            this.pending = pending;
        }
    }
}
