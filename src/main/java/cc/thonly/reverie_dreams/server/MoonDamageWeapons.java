package cc.thonly.reverie_dreams.server;

public class MoonDamageWeapons {
    private static final Profile WOOD = new Profile(0.6f, false);
    private static final Profile STONE = new Profile(0.65f, false);
    private static final Profile IRON = new Profile(0.75f, false);
    private static final Profile DIAMOND = new Profile(0.9f, true);
    private static final Profile NETHERITE = new Profile(1, true);

    public static Profile profile(String itemId) {
        if ("illagerexp:platinum_infused_netherite_sword".equals(itemId) || "reverie_dreams:death_scythe".equals(itemId)) return NETHERITE;
        if (itemId != null && itemId.startsWith("minecraft:") && (itemId.endsWith("_sword") || itemId.endsWith("_axe")
                || itemId.endsWith("_pickaxe") || itemId.endsWith("_shovel") || itemId.endsWith("_hoe"))) {
            String path = itemId.substring(10);
            if (path.startsWith("wooden_") || path.startsWith("golden_")) return WOOD;
            if (path.startsWith("stone_")) return STONE;
            if (path.startsWith("iron_")) return IRON;
            if (path.startsWith("netherite_")) return NETHERITE;
        }
        return DIAMOND;
    }

    public record Profile(float weight, boolean trueEligible) {
    }
}
