package cc.thonly.reverie_dreams.mixin.entity;

import cc.thonly.reverie_dreams.server.MoonDamageCombat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Player.class)
public abstract class MoonDamagePlayerMixin {
    @WrapOperation(
            method = "attack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
            )
    )
    private boolean reverie_dreams$moonDamage(Entity target, DamageSource source, float damage,
                                             Operation<Boolean> original, @Local(ordinal = 0) ItemStack weapon) {
        return MoonDamageCombat.hurt((Player) (Object) this, target, weapon, source, damage,
                amount -> original.call(target, source, amount));
    }

    @ModifyVariable(
            method = "actuallyHurt",
            at = @At(value = "LOAD", ordinal = 6),
            ordinal = 0,
            argsOnly = true
    )
    private float reverie_dreams$moonTrueDamage(float remaining, ServerLevel world, DamageSource source, float damage) {
        return MoonDamageCombat.addTrueDamage((Player) (Object) this, source, remaining);
    }
}
