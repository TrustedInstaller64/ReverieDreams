package cc.thonly.reverie_dreams.server;

import cc.thonly.reverie_dreams.ReverieDreams;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@ToString
public class DelayedTask {
    public static final List<DelayedTask> TASKS = new ArrayList<>();
    private MinecraftServer server;
    private float ticksLeft;
    private Runnable action;
    private volatile boolean stopped;
    private float intervalTicks;
    private BooleanPredicate condition;
    private Runnable onComplete;

    public DelayedTask(MinecraftServer server, float ticksLeft, Runnable action) {
        this.server = server;
        this.ticksLeft = ticksLeft;
        this.action = action;
        synchronized (DelayedTask.class) {
            TASKS.add(this);
        }
    }

    public static synchronized DelayedTask create(MinecraftServer server, float ticksLeft, Runnable action) {
        return new DelayedTask(server, ticksLeft, action);
    }

    public static synchronized DelayedTask createFromSecond(MinecraftServer server, float second, Runnable action) {
        return new DelayedTask(server, second * 20, action);
    }

    public static synchronized void when(MinecraftServer server, BooleanPredicate predicate, float intervalSeconds, Runnable action, Runnable elseAction) {
        repeatUntil(server, predicate, intervalSeconds * 20, action, elseAction);
    }

    public static synchronized void whenTick(MinecraftServer server, BooleanPredicate predicate, float intervalTick, Runnable action, Runnable elseAction) {
        repeatUntil(server, predicate, intervalTick, action, elseAction);
    }

    public static synchronized DelayedTask repeatUntil(MinecraftServer server, BooleanPredicate predicate, float intervalTick, Runnable action, Runnable elseAction) {
        DelayedTask task = create(server, intervalTick, elseAction);
        task.intervalTicks = intervalTick;
        task.condition = predicate;
        task.onComplete = action;
        return task;
    }

    public static synchronized void repeat(MinecraftServer server, int times, float intervalSeconds, Runnable action) {
        if (times <= 0) return;
        createFromSecond(server, intervalSeconds, () -> {
            action.run();
            repeat(server, times - 1, intervalSeconds, action);
        });
    }

    public static synchronized void repeat(MinecraftServer server, int times, int intervalTick, Runnable action) {
        if (times <= 0) return;
        create(server, intervalTick, () -> {
            action.run();
            repeat(server, times - 1, intervalTick, action);
        });
    }


    public static void tick(MinecraftServer server) {
        List<DelayedTask> collect;
        synchronized (DelayedTask.class) {
            collect = TASKS.stream().filter(t -> t.server == server).toList();
        }
        for (var task : collect) {
            try {
                task.tick();
            } catch (Exception e) {
                ReverieDreams.LOGGER.error("Failed to execute delayed task", e);
            }
        }
    }

    public void stop() {
        this.stopped = true;
        synchronized (DelayedTask.class) {
            TASKS.remove(this);
        }
    }

    public static synchronized void clear(MinecraftServer server) {
        TASKS.removeIf(task -> {
            if (task.server != server) return false;
            task.stopped = true;
            return true;
        });
    }

    public synchronized boolean tick() {
        if (this.stopped) return true;
        if (--this.ticksLeft <= 0) {
            synchronized (DelayedTask.class) {
                TASKS.remove(this);
            }
            try {
                if (this.condition == null) {
                    this.stopped = true;
                    this.action.run();
                    return true;
                }
                if (this.condition.get()) {
                    this.stopped = true;
                    this.onComplete.run();
                    return true;
                }
                this.action.run();
                synchronized (DelayedTask.class) {
                    if (!this.stopped) {
                        this.ticksLeft = this.intervalTicks;
                        TASKS.add(this);
                    }
                }
                return this.stopped;
            } catch (RuntimeException | Error e) {
                this.stopped = true;
                throw e;
            }
        }
        return false;
    }

    public interface BooleanPredicate {
        Boolean get();
    }
}
