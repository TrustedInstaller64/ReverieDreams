package cc.thonly.reverie_dreams.util;

import cc.thonly.reverie_dreams.block.MusicBlock;
import cc.thonly.reverie_dreams.block.entity.MusicBlockEntity;
import cc.thonly.reverie_dreams.item.prop.MusicalInstrumentItem;
import cc.thonly.reverie_dreams.server.DelayedTask;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.phys.Vec3;
import nota.model.RepeatMode;
import nota.model.Song;
import nota.player.EntitySongPlayer;
import nota.player.PositionSongPlayer;
import nota.player.SongPlayer;
import nota.utils.NBSDecoderPlus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

@Slf4j
@SuppressWarnings("deprecation")
public final class TouhouNotaUtils {
    public static final String STR_PATH = "config/reverie_dreams/nota";
    public static final Path PATH = Paths.get(STR_PATH);
    public static final Map<String, SongPlayer> id2SongCache = new HashMap<>();
    public static final Map<Level, Map<Long, SongPlayer>> blockMusicPlayCache = new HashMap<>();
    private static final Map<Level, Map<Long, MusicSession>> blockMusicSessions = new HashMap<>();
    private static final Map<String, MusicSession> entityMusicSessions = new HashMap<>();
    public static int MAX_DISTANCE = 32;

    static {
        try {
            Files.createDirectories(PATH);
        } catch (IOException e) {
            log.error("Failed to create directory: " + STR_PATH, e);
        }
    }

    public static void playAt(Level world, BlockPos pos, String select) {
        if (!(world instanceof ServerLevel serverWorld) || select == null || select.isBlank() || !world.hasChunkAt(pos)) {
            return;
        }
        if (!(world.getBlockEntity(pos) instanceof MusicBlockEntity blockEntity)) {
            return;
        }
        Map<Long, MusicSession> sessions = blockMusicSessions.get(world);
        MusicSession current = sessions == null ? null : sessions.get(pos.asLong());
        if (current != null && current.blockEntity == blockEntity && current.filename.equals(select)) {
            return;
        }
        stopAt(world, pos);
        MinecraftServer server = serverWorld.getServer();
        BlockPos blockPos = pos.immutable();
        MusicSession session = new MusicSession(world, select, blockEntity);
        blockMusicSessions.computeIfAbsent(world, key -> new HashMap<>()).put(blockPos.asLong(), session);
        session.task = DelayedTask.create(server, 2, () -> {
            if (!isCurrent(world, blockPos, session)) return;
            session.task = null;
            if (!isValid(world, blockPos, session)) {
                stopAt(world, blockPos, session);
                return;
            }
            Song song = readSong(select, null);
            if (song == null) return;
            try {
                PositionSongPlayer psp = new PositionSongPlayer(song, world);
                session.player = psp;
                psp.setBlockPos(blockPos);
                psp.setDistance(MAX_DISTANCE);
                psp.setRepeatMode(RepeatMode.ALL);
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    psp.addPlayer(player);
                }
                psp.setPlaying(true);
                blockMusicPlayCache.computeIfAbsent(world, key -> new HashMap<>()).put(blockPos.asLong(), psp);
                session.task = DelayedTask.repeatUntil(server,
                        () -> !isCurrent(world, blockPos, session) || !psp.isPlaying() || !isValid(world, blockPos, session),
                        4,
                        () -> stopAt(world, blockPos, session),
                        () -> {
                            try {
                                for (ServerPlayer player : serverWorld.players()) {
                                    if (psp.hasPlayer(player)) continue;
                                    if (blockPos.distToCenterSqr(player.position()) > MAX_DISTANCE * MAX_DISTANCE) continue;
                                    psp.addPlayer(player);
                                }
                                serverWorld.sendParticles(ParticleTypes.NOTE,
                                        blockPos.getX() + 0.5, blockPos.getY() + 1, blockPos.getZ() + 0.5,
                                        1, 0, 0, 0, 0.01);
                            } catch (RuntimeException e) {
                                stopAt(world, blockPos, session);
                                throw e;
                            }
                        });
            } catch (RuntimeException e) {
                stopAt(world, blockPos, session);
                throw e;
            }
        });
    }

    private static boolean isCurrent(Level world, BlockPos pos, MusicSession session) {
        Map<Long, MusicSession> sessions = blockMusicSessions.get(world);
        return sessions != null && sessions.get(pos.asLong()) == session;
    }

    private static boolean isValid(Level world, BlockPos pos, MusicSession session) {
        try {
            return world.hasChunkAt(pos)
                    && !session.blockEntity.isRemoved()
                    && world.getBlockEntity(pos) == session.blockEntity
                    && world.getBlockState(pos).getBlock() instanceof MusicBlock
                    && world.hasNeighborSignal(pos);
        } catch (RuntimeException e) {
            stopAt(world, pos, session);
            throw e;
        }
    }

    public static void stopAt(Level world, BlockPos pos) {
        Map<Long, MusicSession> sessions = blockMusicSessions.get(world);
        MusicSession session = sessions == null ? null : sessions.get(pos.asLong());
        if (session != null) stopAt(world, pos, session);
    }

    public static void stopAt(Level world, BlockPos pos, MusicBlockEntity blockEntity) {
        Map<Long, MusicSession> sessions = blockMusicSessions.get(world);
        MusicSession session = sessions == null ? null : sessions.get(pos.asLong());
        if (session != null && session.blockEntity == blockEntity) stopAt(world, pos, session);
    }

    private static void stopAt(Level world, BlockPos pos, MusicSession session) {
        session.stop();
        Map<Long, MusicSession> sessions = blockMusicSessions.get(world);
        if (sessions != null) {
            sessions.remove(pos.asLong(), session);
            if (sessions.isEmpty()) blockMusicSessions.remove(world, sessions);
        }
        Map<Long, SongPlayer> players = blockMusicPlayCache.get(world);
        if (players != null) {
            players.remove(pos.asLong(), session.player);
            if (players.isEmpty()) blockMusicPlayCache.remove(world, players);
        }
    }

    public static void stopChunk(Level world, ChunkPos chunkPos) {
        Map<Long, MusicSession> sessions = blockMusicSessions.get(world);
        if (sessions == null) return;
        for (long key : new ArrayList<>(sessions.keySet())) {
            BlockPos pos = BlockPos.of(key);
            if ((pos.getX() >> 4) == chunkPos.x && (pos.getZ() >> 4) == chunkPos.z) stopAt(world, pos);
        }
    }

    public static void stopWorld(Level world) {
        Map<Long, MusicSession> sessions = blockMusicSessions.get(world);
        if (sessions != null) {
            for (long key : new ArrayList<>(sessions.keySet())) stopAt(world, BlockPos.of(key));
        }
        for (Map.Entry<String, MusicSession> entry : new ArrayList<>(entityMusicSessions.entrySet())) {
            if (entry.getValue().world == world) stop(entry.getKey(), entry.getValue());
        }
    }

    public static void stopAll(MinecraftServer server) {
        for (Level world : new ArrayList<>(blockMusicSessions.keySet())) {
            if (world.getServer() == server) stopWorld(world);
        }
        for (Map.Entry<String, MusicSession> entry : new ArrayList<>(entityMusicSessions.entrySet())) {
            if (entry.getValue().world.getServer() == server) stop(entry.getKey(), entry.getValue());
        }
    }

    public static void play(LivingEntity user, String playingMusic, NoteBlockInstrument noteBlockInstrument) {
        tryPlay(user, playingMusic, noteBlockInstrument);
    }

    public static boolean tryPlay(LivingEntity user, String playingMusic, NoteBlockInstrument noteBlockInstrument) {
        if (!(user.level() instanceof ServerLevel world) || playingMusic == null || playingMusic.isBlank()) {
            return false;
        }
        Song song = readSong(playingMusic, noteBlockInstrument);
        if (song == null) {
            if (user instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("item.reverie_dreams.music.read_failed", playingMusic), false);
            }
            return false;
        }
        stop(user);
        MinecraftServer server = world.getServer();
        String id = "music_" + user.getStringUUID();
        MusicSession session = new MusicSession(world, playingMusic, null);
        entityMusicSessions.put(id, session);
        try {
            EntitySongPlayer esp = new EntitySongPlayer(song);
            session.player = esp;
            esp.setId(ResourceLocation.parse(UUID.randomUUID().toString()));
            esp.setEntity(user);
            esp.setDistance(MAX_DISTANCE);
            esp.setRepeatMode(RepeatMode.NONE);
            esp.setAutoDestroy(true);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                esp.addPlayer(player);
            }
            esp.setPlaying(true);
            id2SongCache.put(id, esp);
            session.task = DelayedTask.repeatUntil(server, () -> {
                if (entityMusicSessions.get(id) != session || !esp.isPlaying() || !user.isAlive() || user.isRemoved() || user.level() != world) return true;
                ItemStack handStack = user.getMainHandItem();
                ItemStack offStack = user.getOffhandItem();
                return !(handStack.getItem() instanceof MusicalInstrumentItem) && !(offStack.getItem() instanceof MusicalInstrumentItem);
            }, 2, () -> stop(id, session), () -> {
                try {
                    Vec3 frontVec = user.getViewVector(1.0F);
                    world.sendParticles(ParticleTypes.NOTE,
                            user.getX() + frontVec.x * 0.5,
                            user.getY() + user.getEyeHeight() - 0.1,
                            user.getZ() + frontVec.z * 0.5,
                            2, 0.05, 0.05, 0.05, 0.01);
                } catch (RuntimeException e) {
                    stop(id, session);
                    throw e;
                }
            });
        } catch (RuntimeException e) {
            stop(id, session);
            throw e;
        }
        return true;
    }

    public static void stop(LivingEntity user) {
        String id = "music_" + user.getStringUUID();
        MusicSession session = entityMusicSessions.get(id);
        if (session != null) stop(id, session);
    }

    private static void stop(String id, MusicSession session) {
        session.stop();
        entityMusicSessions.remove(id, session);
        id2SongCache.remove(id, session.player);
    }

    private static Song readSong(String filename, NoteBlockInstrument instrument) {
        try {
            Path file = getFilePath(filename);
            if (!Files.isRegularFile(file)) return null;
            return instrument == null ? NBSDecoderPlus.parse(file.toFile()) : NBSDecoderPlus.parse(file.toFile(), instrument);
        } catch (Exception e) {
            log.error("读取音乐失败: {}", filename, e);
            return null;
        }
    }

    public static List<String> getFileNames() {
        try (Stream<Path> files = Files.list(PATH)) {
            return files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(filename -> filename.toLowerCase(Locale.ROOT).endsWith(".nbs"))
                    .sorted(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()))
                    .toList();
        } catch (Exception e) {
            log.warn("扫描音乐目录失败", e);
            return new ArrayList<>();
        }
    }

    public static Path getFilePath(String filename) {
        Path file = PATH.resolve(filename).normalize();
        if (!Objects.equals(file.getParent(), PATH) || !file.getFileName().toString().equals(filename)) {
            throw new IllegalArgumentException("Invalid music filename");
        }
        return file;
    }

    private static class MusicSession {
        private final Level world;
        private final String filename;
        private final MusicBlockEntity blockEntity;
        private DelayedTask task;
        private SongPlayer player;

        private MusicSession(Level world, String filename, MusicBlockEntity blockEntity) {
            this.world = world;
            this.filename = filename;
            this.blockEntity = blockEntity;
        }

        private void stop() {
            if (this.task != null) this.task.stop();
            if (this.player != null) this.player.destroy();
        }
    }
}
