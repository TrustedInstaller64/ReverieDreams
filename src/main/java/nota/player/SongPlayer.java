package nota.player;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import nota.Nota;
import nota.event.SongEndEvent;
import nota.event.SongStartEvent;
import nota.event.SongTickEvent;
import nota.model.Playlist;
import nota.model.RepeatMode;
import nota.model.Song;
import nota.model.playmode.ChannelMode;
import nota.model.playmode.MonoMode;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Plays a Song for a list of Players
 */
@SuppressWarnings("unused")
public abstract class SongPlayer {
	ResourceLocation id = ResourceLocation.parse("noteblock-api:unidentified");

	protected Song song;
	protected Playlist playlist;
	protected int currentSongIndex = 0;

	public volatile boolean playing = false;
	protected boolean fading = false;
	protected short tick = -1;
	protected Map<UUID, Boolean> playerList = new ConcurrentHashMap<>();

	protected boolean autoDestroy = false;
	protected volatile boolean destroyed = false;

	protected byte volume = 100;
	float songDelay = 50.0F; //milliseconds per tick

	protected RepeatMode repeat = RepeatMode.ALL;

	protected Nota api;
	protected ChannelMode channelMode = new MonoMode();
	protected boolean enable10Octave = false;
	protected final MinecraftServer server;

	Timer timer;
	private final AtomicBoolean tickQueued = new AtomicBoolean();

	public SongPlayer(Song song) {
		this(new Playlist(song));
	}

	public SongPlayer(Playlist playlist) {
		this.playlist = playlist;
		this.api = Nota.getAPI();
		this.song = playlist.get(this.currentSongIndex);
		this.songDelay = song.getDelay() * 50.0f;
		this.server = api.getServer();
		this.timer = new Timer("nota-song-player", true);
		this.api.registerSongPlayer(this);
		restartTask((long)this.songDelay);
	}

	/**
	 * Check if 6 octave range is enabled
	 *
	 * @return true if enabled, false otherwise
	 */
	public boolean isEnable10Octave() {
		return this.enable10Octave;
	}

	/**
	 * Enable or disable 6 octave range
	 * <p>
	 * If not enabled, notes will be transposed to 2 octave range
	 *
	 * @param enable10Octave true if enabled, false otherwise
	 */
	public void setEnable10Octave(boolean enable10Octave) {
		this.enable10Octave = enable10Octave;
	}

	private synchronized void play() {
		for(UUID uuid : playerList.keySet()) {
			Player player = Nota.getAPI().getServer().getPlayerList().getPlayer(uuid);
			if(player != null) {
				this.playTick(player, tick);
			}
		}
	}

	private synchronized void onTaskRun(TimerTask task) {
		if(this.destroyed || this.server != api.getServer() || api.isDisabling()) {
			this.destroy();
			return;
		}
		if(this.server.isPaused() || !this.server.tickRateManager().runsNormally()) return;
		if(playing) {
			tick++;
			if(tick == 0) {
				SongStartEvent.EVENT.invoker().onSongStart(this);
			}
			if(tick > song.getLength()) {
				SongEndEvent.EVENT.invoker().onSongEnd(this);
				tick = -1;

				if(playlist.hasNext(currentSongIndex)) {
					currentSongIndex++;
					song = playlist.get(currentSongIndex);
					songDelay = song.getDelay() * 50.0f;
					this.playSong(currentSongIndex);
					this.restartTask((long)this.songDelay);
					task.cancel();
					return;
				}
				else {
					currentSongIndex = 0;
					song = playlist.get(currentSongIndex);
					songDelay = song.getDelay() * 50.0f;
					if(repeat.equals(RepeatMode.ALL)) {
						this.restartTask((long)this.songDelay);
						task.cancel();
						return;
					}
					else {
						this.restartTask((long)this.songDelay);
						task.cancel();
					}
				}
				playing = false;
				if(this.autoDestroy) {
					this.destroy();
				}
				return;
			}
			SongTickEvent.EVENT.invoker().onSongTick(this);
			for(UUID uuid : playerList.keySet()) {
                MinecraftServer server = Nota.getAPI().getServer();
                if (server != null) {
                    Player player = server.getPlayerList().getPlayer(uuid);
                    if(player != null) {
                        this.playTick(player, tick);
                    }
                }
			}
		}
	}

	/**
	 * Starts this SongPlayer
	 */
	private synchronized void restartTask(long delay) {
		if(this.destroyed) return;
		this.timer.schedule(new TimerTask() {
			@Override
			public synchronized void run() {
				if(destroyed || server == null || server != api.getServer() || api.isDisabling()) {
					destroy();
					return;
				}
				if(tickQueued.compareAndSet(false, true)) {
					try {
						server.execute(() -> {
							try {
								onTaskRun(this);
							} catch (RuntimeException e) {
								destroy();
								Nota.LOGGER.error("Failed to play song", e);
							} finally {
								tickQueued.set(false);
							}
						});
					} catch (RuntimeException e) {
						tickQueued.set(false);
						destroy();
					}
				}
			}
		}, 0L, Math.max(1L, delay));
	}

	/**
	 * Gets unique id of this SongPlayer
	 *
	 * @return song entity's unique id
	 */
	public ResourceLocation getId() {
		return this.id;
	}

	/**
	 * Sets unique id for this SongPlayer
	 *
	 */
	public void setId(ResourceLocation id) {
		this.id = id;
	}

	/**
	*
	* **/
	public boolean hasPlayer(Player player) {
		return this.playerList.containsKey(player.getUUID());
	}

	/**
	 * Gets list of current Player UUIDs listening to this SongPlayer
	 *
	 * @return list of Player UUIDs
	 */
	public Set<UUID> getPlayerUUIDs() {
		Set<UUID> uuids = new HashSet<>(this.playerList.keySet());
		return Collections.unmodifiableSet(uuids);
	}

	/**
	 * Adds a Player to the list of Players listening to this SongPlayer
	 *
	 * @param player entity entity
	 */
	public void addPlayer(Player player) {
		addPlayer(player.getUUID());
	}

	/**
	 * Adds a Player to the list of Players listening to this SongPlayer
	 *
	 * @param playerUuid entity's uuid
	 */
	public synchronized void addPlayer(UUID playerUuid) {
		if(this.destroyed) return;
		if(!this.playerList.containsKey(playerUuid)) {
			this.playerList.put(playerUuid, false);
			synchronized(Nota.class) {
				ArrayList<SongPlayer> current = Nota.getSongPlayersByPlayer(playerUuid);
				ArrayList<SongPlayer> songs = current == null ? new ArrayList<>() : new ArrayList<>(current);
				songs.add(this);
				Nota.setSongPlayersByPlayer(playerUuid, songs);
			}
		}
	}

	/**
	 * Returns whether the SongPlayer is set to destroy itself when no one is listening
	 * or when the Song ends
	 *
	 * @return if autoDestroy is enabled
	 */
	public boolean getAutoDestroy() {
		return this.autoDestroy;
	}

	/**
	 * Sets whether the SongPlayer is going to destroy itself when no one is listening
	 * or when the Song ends
	 *
	 * @param autoDestroy if auto destroy should be enabled
	 */
	public void setAutoDestroy(boolean autoDestroy) {
		this.autoDestroy = autoDestroy;
	}

	/**
	 * Plays the Song for the specific entity
	 *
	 * @param player to play this SongPlayer for
	 * @param tick   to play at
	 */
	public abstract void playTick(Player player, int tick);

	/**
	 * SongPlayer will destroy itself
	 */
	public synchronized void destroy() {
		if(this.destroyed) return;
		this.destroyed = true;
		this.playing = false;
		this.setTick((short) -1);
		this.timer.cancel();
		for(UUID playerUuid : new HashSet<>(this.playerList.keySet())) {
			this.removePlayer(playerUuid);
		}
		this.playerList.clear();
		this.api.unregisterSongPlayer(this);
	}

	/**
	 * Returns whether the SongPlayer is actively playing
	 *
	 * @return if this entity is playing
	 */
	public boolean isPlaying() {
		return this.playing;
	}

	/**
	 * Sets whether the SongPlayer is playing
	 *
	 * @param playing if this entity should play
	 */
	public void setPlaying(boolean playing) {
		this.playing = playing && !this.destroyed;
	}

	/**
	 * Gets the current tick of this SongPlayer
	 *
	 * @return current tick
	 */
	public short getTick() {
		return this.tick;
	}

	/**
	 * Sets the current tick of this SongPlayer
	 *
	 * @param tick tick
	 */
	public void setTick(short tick) {
		this.tick = tick;
	}

	/**
	 * Removes a entity from this SongPlayer
	 *
	 * @param player to remove
	 */
	public void removePlayer(Player player) {
		removePlayer(player.getUUID());
	}

	/**
	 * Removes a entity from this SongPlayer
	 *
	 * @param playerUuid of entity to remove
	 */
	public synchronized void removePlayer(UUID playerUuid) {
		playerList.remove(playerUuid);
		synchronized(Nota.class) {
			ArrayList<SongPlayer> current = Nota.getSongPlayersByPlayer(playerUuid);
			if(current != null) {
				ArrayList<SongPlayer> songs = new ArrayList<>(current);
				songs.remove(this);
				Nota.setSongPlayersByPlayer(playerUuid, songs);
			}
		}
		if(this.playerList.isEmpty() && this.autoDestroy) {
			destroy();
		}
	}

	/**
	 * Gets the current volume of this SongPlayer
	 *
	 * @return volume (0-100)
	 */
	public byte getVolume() {
		return this.volume;
	}

	/**
	 * Sets the current volume of this SongPlayer
	 *
	 * @param volume (0-100)
	 */
	public void setVolume(byte volume) {
		if(volume > 100) {
			volume = 100;
		}
		else if(volume < 0) {
			volume = 0;
		}
		this.volume = volume;
	}

	/**
	 * Gets the Song being played by this SongPlayer
	 *
	 * @return song
	 */
	public Song getSong() {
		return this.song;
	}

	/**
	 * Gets the Playlist being played by this SongPlayer
	 *
	 * @return playlist
	 */
	public Playlist getPlaylist() {
		return this.playlist;
	}

	/**
	 * Sets the Playlist being played by this SongPlayer. Will affect next Song
	 */
	public void setPlaylist(Playlist playlist) {
		this.playlist = playlist;
	}

	/**
	 * Get index of actually played {@link Song} in {@link Playlist}
	 *
	 * @return song index
	 */
	public int getPlayedSongIndex() {
		return this.currentSongIndex;
	}

	/**
	 * Start playing {@link Song} at specified index in {@link Playlist}
	 * If there is no {@link Song} at this index, {@link SongPlayer} will continue playing current song
	 *
	 * @param index song index
	 */
	public void playSong(int index) {
		if(this.playlist.exist(index)) {
			this.song = this.playlist.get(index);
			this.currentSongIndex = index;
			this.tick = -1;
		}
	}

	/**
	 * Start playing {@link Song} that is next in {@link Playlist} or random {@link Song} from {@link Playlist}
	 */
	public void playNextSong() {
		this.tick = this.song.getLength();
	}

	/**
	 * Sets SongPlayer's {@link RepeatMode}
	 *
	 * @param repeatMode repeat mode
	 */
	public void setRepeatMode(RepeatMode repeatMode) {
		this.repeat = repeatMode;
	}

	/**
	 * Gets SongPlayer's {@link RepeatMode}
	 *
	 * @return repeat mode
	 */
	public RepeatMode getRepeatMode() {
		return this.repeat;
	}

	public ChannelMode getChannelMode() {
		return this.channelMode;
	}
}
