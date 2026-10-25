package cc.thonly.reverie_dreams.mixin.entity;

import cc.thonly.reverie_dreams.server.MoonDamageCombat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Mob.class)
public abstract class MoonDamageMobMixin {
    @WrapOperation(
            method = "doHurtTarget",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"
            )
    )
    private boolean reverie_dreams$moonDamage(Entity target, ServerLevel world, DamageSource source, float damage,
                                             Operation<Boolean> original, @Local(ordinal = 0) ItemStack weapon) {
        return MoonDamageCombat.hurt((Mob) (Object) this, target, weapon, source, damage,
                amount -> original.call(target, world, source, amount));
    }
}
