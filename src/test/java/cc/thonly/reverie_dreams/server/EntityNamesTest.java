package cc.thonly.reverie_dreams.server;

import cc.thonly.reverie_dreams.entity.misc.MagicBroomEntity;
import cc.thonly.reverie_dreams.recipe.ItemStackWrapper;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.Items;

import java.lang.reflect.Field;
import java.util.Optional;

public class EntityNamesTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void field(Object entity, String name, Object value) throws Exception {
        Field field = Entity.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(entity, value);
    }

    @SuppressWarnings("unchecked")
    private static void define(SynchedEntityData.Builder builder, String name, Object value) throws Exception {
        Field field = Entity.class.getDeclaredField(name);
        field.setAccessible(true);
        builder.define((EntityDataAccessor<Object>) field.get(null), value);
    }

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        MagicBroomEntity broom = (MagicBroomEntity) unsafeClass.getMethod("allocateInstance", Class.class)
                .invoke(unsafeField.get(null), MagicBroomEntity.class);
        Field nameField = Entity.class.getDeclaredField("DATA_CUSTOM_NAME");
        nameField.setAccessible(true);
        @SuppressWarnings("unchecked")
        EntityDataAccessor<Optional<Component>> name = (EntityDataAccessor<Optional<Component>>) nameField.get(null);
        SynchedEntityData.Builder builder = new SynchedEntityData.Builder(broom);
        define(builder, "DATA_SHARED_FLAGS_ID", (byte) 0);
        define(builder, "DATA_AIR_SUPPLY_ID", 300);
        define(builder, "DATA_CUSTOM_NAME_VISIBLE", false);
        define(builder, "DATA_CUSTOM_NAME", Optional.empty());
        define(builder, "DATA_SILENT", false);
        define(builder, "DATA_NO_GRAVITY", false);
        define(builder, "DATA_POSE", Pose.STANDING);
        define(builder, "DATA_TICKS_FROZEN", 0);
        var define = MagicBroomEntity.class.getDeclaredMethod("defineSynchedData", SynchedEntityData.Builder.class);
        define.setAccessible(true);
        define.invoke(broom, builder);
        field(broom, "entityData", builder.build());
        field(broom, "type", EntityType.PIG);
        broom.itemWrapper = ItemStackWrapper.EMPTY;
        check(broom.getName().equals(EntityType.PIG.getDescription()), "Empty broom lost its entity name");
        check(broom.getCustomName() == null, "Unnamed broom invented a custom name");
        check(broom.getDisplayName().equals(broom.getName()), "Empty broom display name differs");
        System.out.println("PASS unbound broom name and display name do not recurse");

        Component customName = Component.literal("Broom fixture");
        broom.getEntityData().set(name, Optional.of(customName));
        check(broom.getName().equals(customName), "Empty broom lost its saved custom name");
        System.out.println("PASS unbound broom preserves a saved custom name");

        broom.itemWrapper = ItemStackWrapper.of(Items.STICK);
        check(broom.getName().equals(Items.STICK.getDefaultInstance().getHoverName()), "Bound broom lost its item name");
        check(broom.getCustomName().equals(customName), "Bound item overwrote saved entity name");
        System.out.println("PASS bound broom preserves item display and native custom name");
    }
}
