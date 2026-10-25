package cc.thonly.reverie_dreams.server;

public class MoonDamageWeaponsTest {
    private static void check(String item, float weight, boolean trueEligible) {
        MoonDamageWeapons.Profile profile = MoonDamageWeapons.profile(item);
        if (Math.abs(profile.weight() - weight) > 0.0001f || profile.trueEligible() != trueEligible) {
            throw new AssertionError(item + " returned " + profile);
        }
    }

    public static void main(String[] args) {
        check("minecraft:wooden_sword", 0.6f, false);
        check("minecraft:golden_axe", 0.6f, false);
        check("minecraft:stone_axe", 0.65f, false);
        check("minecraft:iron_sword", 0.75f, false);
        check("minecraft:iron_axe", 0.75f, false);
        check("minecraft:diamond_sword", 0.9f, true);
        check("minecraft:netherite_axe", 1, true);
        check("illagerexp:platinum_infused_netherite_sword", 1, true);
        check("reverie_dreams:death_scythe", 1, true);
        check("reverie_dreams:gungnir", 0.9f, true);
        check("unknown:wooden_sword", 0.9f, true);
        check("minecraft:trident", 0.9f, true);
        check("minecraft:iron_ingot", 0.9f, true);
        System.out.println("PASS 13 weapon profiles, material boundaries and fallback rules");
    }
}
