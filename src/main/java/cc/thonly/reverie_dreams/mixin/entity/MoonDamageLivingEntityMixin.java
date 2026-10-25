package cc.thonly.reverie_dreams.mixin.entity;

import cc.thonly.reverie_dreams.server.MoonDamageCombat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class MoonDamageLivingEntityMixin {
    @WrapOperation(
            method = "hurtServer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/damagesource/DamageSource;is(Lnet/minecraft/tags/TagKey;)Z"
            )
    )
    private boolean reverie_dreams$moonCooldown(DamageSource source, TagKey<DamageType> tag, Operation<Boolean> original) {
        return original.call(source, tag) || tag == DamageTypeTags.BYPASSES_COOLDOWN
                && MoonDamageCombat.bypassesCooldown((LivingEntity) (Object) this, source);
    }

    @WrapOperation(
            method = "hurtServer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;applyItemBlocking(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)F"
            )
    )
    private float reverie_dreams$moonBlocking(LivingEntity target, ServerLevel world, DamageSource source,
                                             float incoming, Operation<Float> original) {
        float blocked = original.call(target, world, source, incoming);
        MoonDamageCombat.blocked(target, source, incoming, blocked);
        return blocked;
    }

    @ModifyVariable(
            method = "actuallyHurt",
            at = @At(value = "LOAD", ordinal = 6),
            ordinal = 0,
            argsOnly = true
    )
    private float reverie_dreams$moonTrueDamage(float remaining, ServerLevel world, DamageSource source, float damage) {
        return MoonDamageCombat.addTrueDamage((LivingEntity) (Object) this, source, remaining);
    }

    @WrapOperation(
            method = "actuallyHurt",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;setAbsorptionAmount(F)V",
                    ordinal = 1
            )
    )
    private void reverie_dreams$moonAbsorption(LivingEntity target, float proposed, Operation<Void> original,
                                              @Local(argsOnly = true) DamageSource source) {
        original.call(target, MoonDamageCombat.restoreAbsorption(target, source, proposed));
    }
}
