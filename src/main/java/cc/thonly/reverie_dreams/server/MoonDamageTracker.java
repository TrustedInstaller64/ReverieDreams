package cc.thonly.reverie_dreams.server;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MoonDamageTracker {
    private static final long[] EMPTY = new long[0];
    private final Map<UUID, WeaponState> weapons = new HashMap<>();
    private long revision;
    private long epoch;

    public PendingHit prepare(UUID weaponId, UUID targetId, boolean player, String itemId, int level, float weight, boolean trueEligible, long tick, boolean night) {
        if (weaponId == null || targetId == null || itemId == null || itemId.isBlank() || level <= 0 || !Float.isFinite(weight) || weight <= 0 || tick < 0) return null;
        float damage = level * weight * (night ? 1 : 0.5f);
        if (!Float.isFinite(damage)) return null;
        WeaponState state = this.weapons.get(weaponId);
        if (state != null && (tick < state.lastTick || player && state.hasPlayerTick && tick - state.playerTick < 10 || !player && state.hasEntityTick && tick - state.entityTick < 1)) return null;
        Profile profile = new Profile(itemId, level, weight, trueEligible);
        long[] previous = state == null || !profile.equals(state.profile) ? EMPTY : player ? state.players.getOrDefault(targetId, EMPTY) : state.entities;
        int start = 0;
        while (start < previous.length && tick - previous[start] > 60) start++;
        long[] next = Arrays.copyOfRange(previous, start, previous.length + 1);
        next[next.length - 1] = tick;
        boolean fifth = next.length == 5;
        float normalDamage = 0;
        float trueDamage = 0;
        if (!player) {
            trueDamage = damage * (fifth ? 1.25f : 1);
        } else if (fifth && trueEligible && (state == null || !state.hasTrueTick || tick - state.trueTick >= 30)) {
            trueDamage = Math.min(2, damage * 0.75f);
        } else {
            normalDamage = damage;
        }
        if (!Float.isFinite(trueDamage)) return null;
        return new PendingHit(this, this.epoch, state == null ? 0 : state.revision, weaponId, targetId, player, tick,
                profile, normalDamage, trueDamage, fifth, fifth ? EMPTY : next);
    }

    public boolean accept(PendingHit hit, boolean trueApplied) {
        if (hit == null || hit.owner != this || hit.epoch != this.epoch) return false;
        WeaponState state = this.weapons.get(hit.weaponId);
        if ((state == null ? 0 : state.revision) != hit.revision) return false;
        if (state == null) {
            state = new WeaponState(hit.profile);
            this.weapons.put(hit.weaponId, state);
        } else if (!hit.profile.equals(state.profile)) {
            state.profile = hit.profile;
            state.players.clear();
            state.entities = EMPTY;
        }
        if (hit.player) {
            if (hit.next.length == 0) state.players.remove(hit.targetId);
            else state.players.put(hit.targetId, hit.next);
            state.playerTick = hit.tick;
            state.hasPlayerTick = true;
            if (hit.trueDamage > 0 && trueApplied) {
                state.trueTick = hit.tick;
                state.hasTrueTick = true;
            }
        } else {
            state.entities = hit.next;
            state.entityTick = hit.tick;
            state.hasEntityTick = true;
        }
        state.lastTick = hit.tick;
        state.revision = ++this.revision;
        return true;
    }

    public void clearTarget(UUID targetId) {
        if (targetId == null) return;
        for (WeaponState state : this.weapons.values()) {
            if (state.players.remove(targetId) != null) state.revision = ++this.revision;
        }
    }

    public void clear() {
        this.weapons.clear();
        this.epoch++;
    }

    public void expire(long tick) {
        this.weapons.entrySet().removeIf(entry -> {
            WeaponState state = entry.getValue();
            if (tick - state.lastTick > 60) return true;
            boolean changed = state.players.entrySet().removeIf(target -> expired(target.getValue(), tick));
            if (expired(state.entities, tick)) {
                state.entities = EMPTY;
                changed = true;
            }
            if (changed) state.revision = ++this.revision;
            return false;
        });
    }

    public int size() {
        return this.weapons.size();
    }

    private static boolean expired(long[] hits, long tick) {
        return hits.length > 0 && tick - hits[hits.length - 1] > 60;
    }

    private record Profile(String itemId, int level, float weight, boolean trueEligible) {
    }

    private static class WeaponState {
        private Profile profile;
        private final Map<UUID, long[]> players = new HashMap<>();
        private long[] entities = EMPTY;
        private long playerTick;
        private long entityTick;
        private long trueTick;
        private long lastTick;
        private long revision;
        private boolean hasPlayerTick;
        private boolean hasEntityTick;
        private boolean hasTrueTick;

        private WeaponState(Profile profile) {
            this.profile = profile;
        }
    }

    public static final class PendingHit {
        private final MoonDamageTracker owner;
        private final long epoch;
        private final long revision;
        private final UUID weaponId;
        private final UUID targetId;
        private final boolean player;
        private final long tick;
        private final Profile profile;
        private final float normalDamage;
        private final float trueDamage;
        private final boolean fifth;
        private final long[] next;

        private PendingHit(MoonDamageTracker owner, long epoch, long revision, UUID weaponId, UUID targetId, boolean player, long tick, Profile profile, float normalDamage, float trueDamage, boolean fifth, long[] next) {
            this.owner = owner;
            this.epoch = epoch;
            this.revision = revision;
            this.weaponId = weaponId;
            this.targetId = targetId;
            this.player = player;
            this.tick = tick;
            this.profile = profile;
            this.normalDamage = normalDamage;
            this.trueDamage = trueDamage;
            this.fifth = fifth;
            this.next = next;
        }

        public UUID weaponId() {
            return this.weaponId;
        }

        public UUID targetId() {
            return this.targetId;
        }

        public boolean player() {
            return this.player;
        }

        public long tick() {
            return this.tick;
        }

        public float normalDamage() {
            return this.normalDamage;
        }

        public float trueDamage() {
            return this.trueDamage;
        }

        public boolean fifth() {
            return this.fifth;
        }
    }
}
