package cc.thonly.reverie_dreams.dialog;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.world.InteractionHand;

import java.util.ArrayList;
import java.util.List;

public class MusicDialogTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static String command(ActionButton button) {
        return ((ClickEvent.RunCommand) ((StaticAction) button.action().orElseThrow()).value()).command();
    }

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        MultiActionDialog empty = MusicSelectDialog.create(List.of(), null, InteractionHand.MAIN_HAND, 0);
        check(empty.actions().size() == 2 && empty.exitAction().isPresent(), "Empty list lost controls or exit");
        check(empty.common().body().size() == 3, "Empty list missing its message");
        System.out.println("PASS empty music list keeps controls and explanatory body");

        List<String> files = new ArrayList<>();
        for (int i = 0; i < 21; i++) files.add("song-" + i + ".nbs");
        MultiActionDialog first = MusicSelectDialog.create(files, null, InteractionHand.MAIN_HAND, 0);
        MultiActionDialog last = MusicSelectDialog.create(files, "song-20.nbs", InteractionHand.MAIN_HAND, 1);
        check(first.actions().size() == 23, "First page must have twenty songs, next, play, stop");
        check(last.actions().size() == 4, "Last page must have one song, previous, play, stop");
        check(command(first.actions().get(19)).endsWith("select song-19.nbs"), "First page indexed wrong song");
        check(command(last.actions().getFirst()).endsWith("select song-20.nbs"), "Last page indexed wrong song");
        check(last.actions().getFirst().button().label().getString().startsWith("▶ "), "Selected song not marked");
        System.out.println("PASS pagination boundary, page commands and selected song marker");

        check(MusicSelectDialog.create(files, null, InteractionHand.MAIN_HAND, Integer.MAX_VALUE).actions().size() == 4, "Page overflow not clamped");
        check(MusicSelectDialog.create(files, null, InteractionHand.MAIN_HAND, -1).actions().size() == 23, "Negative page not clamped");
        System.out.println("PASS stale or invalid page numbers clamp to the available range");

        String filename = "东方 \"song\" \\ demo.nbs";
        MultiActionDialog quoted = MusicSelectDialog.create(List.of(filename), null, InteractionHand.OFF_HAND, 0);
        String action = command(quoted.actions().getFirst());
        String prefix = "/touhou music off select ";
        check(action.startsWith(prefix), "Off-hand action targets another hand");
        StringReader reader = new StringReader(action.substring(prefix.length()));
        check(StringArgumentType.string().parse(reader).equals(filename) && !reader.canRead(), "Filename failed command round trip");
        check(command(quoted.actions().get(1)).equals("/touhou music off play"), "Play action missing");
        check(command(quoted.actions().get(2)).equals("/touhou music off stop"), "Stop action missing");
        System.out.println("PASS Unicode, spaces, quotes, backslashes and play/stop command round trip");
    }
}
