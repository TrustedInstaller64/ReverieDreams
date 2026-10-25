package cc.thonly.reverie_dreams.command;

import cc.thonly.reverie_dreams.dialog.MusicSelectDialog;
import cc.thonly.reverie_dreams.item.prop.MusicalInstrumentItem;
import cc.thonly.reverie_dreams.registry.content.component.RDDataComponents;
import cc.thonly.reverie_dreams.registry.content.item.RDItems;
import cc.thonly.reverie_dreams.util.TouhouNotaUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class MusicCommand implements CommandInit.CommandRegistration {
    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext access, Commands.CommandSelection environment) {
        var music = Commands.literal("music")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    InteractionHand hand = player.getMainHandItem().is(RDItems.KEYBOARD) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                    MusicSelectDialog.open(player, hand, 0);
                    return 1;
                })
                .then(Commands.argument("hand", StringArgumentType.word())
                        .suggests((context, builder) -> builder.suggest("main").suggest("off").buildFuture())
                        .executes(context -> this.open(context, 0))
                        .then(Commands.literal("page")
                                .then(Commands.argument("page", IntegerArgumentType.integer(0))
                                        .executes(context -> this.open(context, IntegerArgumentType.getInteger(context, "page")))))
                        .then(Commands.literal("select")
                                .then(Commands.argument("file", StringArgumentType.string())
                                        .executes(this::select)))
                        .then(Commands.literal("play").executes(this::play))
                        .then(Commands.literal("stop").executes(this::stop)));
        dispatcher.register(Commands.literal("touhou").then(music));
    }

    private InteractionHand getHand(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "hand");
        InteractionHand hand = switch (name) {
            case "main" -> InteractionHand.MAIN_HAND;
            case "off" -> InteractionHand.OFF_HAND;
            default -> null;
        };
        if (hand == null || !context.getSource().getPlayerOrException().getItemInHand(hand).is(RDItems.KEYBOARD)) {
            context.getSource().sendFailure(Component.translatable("dialog.music.hold_keyboard"));
            return null;
        }
        return hand;
    }

    private int open(CommandContext<CommandSourceStack> context, int page) throws CommandSyntaxException {
        InteractionHand hand = this.getHand(context);
        if (hand == null) return 0;
        MusicSelectDialog.open(context.getSource().getPlayerOrException(), hand, page);
        return 1;
    }

    private int select(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        InteractionHand hand = this.getHand(context);
        if (hand == null) return 0;
        ServerPlayer player = context.getSource().getPlayerOrException();
        String filename = StringArgumentType.getString(context, "file");
        List<String> files = TouhouNotaUtils.getFileNames();
        int index = files.indexOf(filename);
        if (index == -1) {
            context.getSource().sendFailure(Component.translatable("dialog.music.not_found"));
            MusicSelectDialog.open(player, hand, 0);
            return 0;
        }
        TouhouNotaUtils.stop(player);
        player.getItemInHand(hand).set(RDDataComponents.PLAYING_MUSIC, filename);
        MusicSelectDialog.open(player, hand, index / MusicSelectDialog.PER_PAGE_SIZE);
        return 1;
    }

    private int play(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        InteractionHand hand = this.getHand(context);
        if (hand == null) return 0;
        ServerPlayer player = context.getSource().getPlayerOrException();
        ItemStack stack = player.getItemInHand(hand);
        String filename = stack.get(RDDataComponents.PLAYING_MUSIC);
        if (filename == null || !TouhouNotaUtils.getFileNames().contains(filename)) {
            context.getSource().sendFailure(Component.translatable("dialog.music.not_found"));
            MusicSelectDialog.open(player, hand, 0);
            return 0;
        }
        ((MusicalInstrumentItem) stack.getItem()).playSelectedMusic(player.level(), player, hand);
        player.swing(hand);
        return 1;
    }

    private int stop(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        InteractionHand hand = this.getHand(context);
        if (hand == null) return 0;
        ServerPlayer player = context.getSource().getPlayerOrException();
        TouhouNotaUtils.stop(player);
        MusicSelectDialog.open(player, hand, 0);
        return 1;
    }
}
