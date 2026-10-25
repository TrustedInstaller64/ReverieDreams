package cc.thonly.reverie_dreams.dialog;

import cc.thonly.reverie_dreams.registry.content.component.RDDataComponents;
import cc.thonly.reverie_dreams.registry.content.item.RDItems;
import cc.thonly.reverie_dreams.util.TouhouNotaUtils;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MusicSelectDialog {
    public static final int PER_PAGE_SIZE = 20;

    public static void open(ServerPlayer player, InteractionHand hand, int page) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(RDItems.KEYBOARD)) return;
        player.openDialog(Holder.direct(create(TouhouNotaUtils.getFileNames(), stack.get(RDDataComponents.PLAYING_MUSIC), hand, page)));
    }

    public static MultiActionDialog create(List<String> files, String selected, InteractionHand hand, int page) {
        int maxPage = Math.max(0, (files.size() - 1) / PER_PAGE_SIZE);
        int currentPage = Math.max(0, Math.min(page, maxPage));
        List<DialogBody> body = new ArrayList<>();
        body.add(new PlainMessage(Component.translatable("dialog.music.selected",
                selected == null ? Component.translatable("dialog.music.none") : Component.literal(selected)), 300));
        body.add(new PlainMessage(Component.translatable("dialog.music.page", currentPage + 1, maxPage + 1), 300));
        if (files.isEmpty()) body.add(new PlainMessage(Component.translatable("item.reverie_dreams.music.no_files"), 300));
        List<ActionButton> actions = new ArrayList<>();
        int start = currentPage * PER_PAGE_SIZE;
        for (int index = start; index < Math.min(files.size(), start + PER_PAGE_SIZE); index++) {
            String filename = files.get(index);
            Component title = Component.literal((filename.equals(selected) ? "▶ " : "") + filename);
            actions.add(button(title, hand, "select " + StringArgumentType.escapeIfRequired(filename)));
        }
        if (currentPage > 0) actions.add(button(Component.translatable("dialog.music.previous"), hand, "page " + (currentPage - 1)));
        if (currentPage < maxPage) actions.add(button(Component.translatable("dialog.music.next"), hand, "page " + (currentPage + 1)));
        actions.add(button(Component.translatable("dialog.music.play"), hand, "play"));
        actions.add(button(Component.translatable("dialog.music.stop"), hand, "stop"));
        return new MultiActionDialog(
                new CommonDialogData(Component.translatable("dialog.music.title"), Optional.empty(),
                        true, false, DialogAction.CLOSE, body, new ArrayList<>()),
                actions,
                Optional.of(new ActionButton(new CommonButtonData(Component.translatable("dialog.text.exit"), 150), Optional.empty())),
                2);
    }

    private static ActionButton button(Component title, InteractionHand hand, String action) {
        String command = "/touhou music " + (hand == InteractionHand.MAIN_HAND ? "main" : "off") + " " + action;
        return new ActionButton(new CommonButtonData(title, 150),
                Optional.of(new StaticAction(new ClickEvent.RunCommand(command))));
    }
}
