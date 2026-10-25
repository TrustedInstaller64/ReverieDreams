package cc.thonly.reverie_dreams.entity.villager;

import cc.thonly.reverie_dreams.registry.content.item.RDItems;
import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.Optional;

public class MoneyShopClerk {
    private static final float PRICE_MULTIPLIER = 0.15f;
    public static final int COPPER_PER_SILVER = 10;
    public static final int SILVER_PER_GOLD = 20;

    public static int toCopper(int gold, int silver, int copper) {
        return gold * SILVER_PER_GOLD * COPPER_PER_SILVER + silver * COPPER_PER_SILVER + copper;
    }
    public static void registers() {

        TradeOfferHelper.registerVillagerOffers(RDVillagerProfessions.MONEY_SHOP_CLERK, 1, factories -> {

            factories.add((entity, random) -> new MerchantOffer(
                    new ItemCost(RDItems.COPPER_COIN, COPPER_PER_SILVER),
                    Optional.empty(),
                    new ItemStack(RDItems.SILVER_COIN, 1),
                    16,
                    2,
                    PRICE_MULTIPLIER
            ));

            factories.add((entity, random) -> new MerchantOffer(
                    new ItemCost(RDItems.SILVER_COIN, 1),
                    Optional.empty(),
                    new ItemStack(RDItems.COPPER_COIN, COPPER_PER_SILVER),
                    16,
                    2,
                    PRICE_MULTIPLIER
            ));
        });


        TradeOfferHelper.registerVillagerOffers(RDVillagerProfessions.MONEY_SHOP_CLERK, 2, factories -> {

            factories.add((entity, random) -> new MerchantOffer(
                    new ItemCost(RDItems.SILVER_COIN, SILVER_PER_GOLD),
                    Optional.empty(),
                    new ItemStack(RDItems.GOLD_COIN, 1),
                    12,
                    5,
                    PRICE_MULTIPLIER
            ));

            factories.add((entity, random) -> new MerchantOffer(
                    new ItemCost(RDItems.GOLD_COIN, 1),
                    Optional.empty(),
                    new ItemStack(RDItems.SILVER_COIN, SILVER_PER_GOLD),
                    12,
                    5,
                    PRICE_MULTIPLIER
            ));
        });
    }
}
