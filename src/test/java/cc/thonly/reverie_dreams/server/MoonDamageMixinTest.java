package cc.thonly.reverie_dreams.server;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.launch.knot.Knot;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class MoonDamageMixinTest {
    private static final String ENTITY_PACKAGE = "net.minecraft.world.entity.";

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void property(String name, String value) {
        if (System.getProperty(name) == null) System.setProperty(name, value);
    }

    private static ClassNode transformed(Path directory, String name) throws Exception {
        Path file = directory.resolve(".mixin.out/class/" + name.replace('.', '/') + ".class");
        check(Files.isRegularFile(file), "Missing actual Mixin export: " + file);
        ClassNode node = new ClassNode();
        new ClassReader(Files.readAllBytes(file)).accept(node, 0);
        return node;
    }

    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(value -> value.name.equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing transformed method " + node.name + "." + name));
    }

    private static int invocation(MethodNode method, String fragment) {
        int result = -1;
        for (int i = 0; i < method.instructions.size(); i++) {
            if (method.instructions.get(i) instanceof MethodInsnNode invoke && invoke.name.contains(fragment)) {
                check(result == -1, "Duplicate " + fragment + " in " + method.name);
                result = i;
            }
        }
        check(result >= 0, "Missing " + fragment + " in " + method.name);
        return result;
    }

    private static AbstractInsnNode nextInstruction(AbstractInsnNode current) {
        do {
            current = current.getNext();
        } while (current != null && current.getOpcode() < 0);
        return current;
    }

    private static void assertFinalDamagePlacement(ClassNode node, boolean livingEntity) {
        MethodNode method = method(node, "actuallyHurt");
        int inserted = invocation(method, "$moonTrueDamage");
        int firstAbsorption = invocation(method, "setAbsorptionAmount");
        int tracker = invocation(method, "getCombatTracker");
        int health = invocation(method, "setHealth");
        check(firstAbsorption < inserted && inserted < tracker && tracker < health,
                node.name + " added true damage outside the post-absorption native damage path");
        AbstractInsnNode store = nextInstruction(method.instructions.get(inserted));
        check(store instanceof VarInsnNode variable && variable.getOpcode() == Opcodes.FSTORE && variable.var == 3,
                node.name + " did not modify the actual damage parameter");
        AbstractInsnNode load = nextInstruction(store);
        check(load instanceof VarInsnNode variable && variable.getOpcode() == Opcodes.FLOAD && variable.var == 3,
                node.name + " did not reload final damage for its zero guard");
        check(nextInstruction(load).getOpcode() == Opcodes.FCONST_0,
                node.name + " injected at a different float load instead of the zero-damage guard");
        if (livingEntity) {
            int compensation = invocation(method, "$moonAbsorption");
            check(compensation > health, "Absorption compensation replaced the first subtraction");
        }
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode invoke) {
                check(!invoke.name.equals("hurtServer") && !invoke.name.equals("hurtOrSimulate"),
                        "True damage unexpectedly starts a second native damage operation");
            }
        }
    }

    private static void loaded(ClassLoader loader, String name, String... handlers) throws Exception {
        Class<?> type = Class.forName(name, false, loader);
        check(type.getClassLoader() == loader, "Target bypassed Knot transformation: " + name);
        Method[] methods = type.getDeclaredMethods();
        for (String handler : handlers) {
            check(Arrays.stream(methods).anyMatch(method -> method.getName().contains(handler)),
                    "Actual transformed class lacks " + handler + ": " + name);
        }
    }

    public static void main(String[] args) throws Exception {
        Path project = Path.of(System.getProperty("moon.test.projectDir", ".")).toAbsolutePath().normalize();
        Path build = Path.of(System.getProperty("moon.test.buildDir", project.resolve("build").toString())).toAbsolutePath().normalize();
        Path directory = Path.of("").toAbsolutePath().normalize();
        check(directory.startsWith(build), "Use the build output directory for this check");
        for (String name : new String[]{"world", "server.properties", "eula.txt"}) {
            check(!Files.exists(directory.resolve(name)), "Unexpected server state before transformation: " + name);
        }
        property("fabric.development", "true");
        property("fabric.unitTest", "true");
        property("fabric.remapClasspathFile", project.resolve(".gradle/loom-cache/remapClasspath.txt").toString());
        property("fabric.classPathGroups", build.resolve("classes/java/main") + File.pathSeparator
                + build.resolve("resources/main"));
        System.setProperty("mixin.debug.export", "true");
        System.setProperty("mixin.debug.export.filter", ENTITY_PACKAGE + "**");
        String classpathFile = System.getProperty("moon.test.classpathFile");
        if (classpathFile != null) {
            System.setProperty("java.class.path", Files.readString(Path.of(classpathFile)));
        }
        ClassLoader loader = new Knot(EnvType.SERVER).init(new String[0]);
        loaded(loader, ENTITY_PACKAGE + "player.Player", "$moonDamage", "$moonTrueDamage");
        loaded(loader, ENTITY_PACKAGE + "LivingEntity", "$moonCooldown", "$moonBlocking", "$moonTrueDamage", "$moonAbsorption");
        loaded(loader, ENTITY_PACKAGE + "Mob", "$moonDamage");
        System.out.println("PASS real Fabric Knot transforms Player, LivingEntity and Mob without launching the game");
        ClassNode player = transformed(directory, ENTITY_PACKAGE + "player.Player");
        ClassNode living = transformed(directory, ENTITY_PACKAGE + "LivingEntity");
        ClassNode mob = transformed(directory, ENTITY_PACKAGE + "Mob");
        invocation(method(player, "attack"), "$moonDamage");
        invocation(method(mob, "doHurtTarget"), "$moonDamage");
        System.out.println("PASS direct player and mob attack hooks transform once");
        assertFinalDamagePlacement(player, false);
        assertFinalDamagePlacement(living, true);
        System.out.println("PASS true damage modifies the post-absorption damage parameter before the native zero guard");
        for (String name : new String[]{"world", "server.properties", "eula.txt"}) {
            check(!Files.exists(directory.resolve(name)), "Transformation created unexpected server state: " + name);
        }
        System.out.println("PASS transformation check did not create a world, server properties or EULA file");
    }
}
