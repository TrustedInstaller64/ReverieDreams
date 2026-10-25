"""Compile the real task/player implementations against narrow external API substitutes."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
JAVA = Path(os.environ['JAVA_HOME']) / 'bin' if os.environ.get('JAVA_HOME') else Path(shutil.which('javac') or 'javac').resolve().parent
STUBS = {
    'net/fabricmc/api/ModInitializer.java': 'package net.fabricmc.api; public interface ModInitializer {void onInitialize();}',
    'net/fabricmc/fabric/api/event/lifecycle/v1/ServerLifecycleEvents.java': '''package net.fabricmc.fabric.api.event.lifecycle.v1;
import java.util.*;import java.util.function.Consumer;import net.minecraft.server.MinecraftServer;
public class ServerLifecycleEvents {public static final Event SERVER_STARTED=new Event(),SERVER_STOPPING=new Event();
public static class Event {private final List<Consumer<MinecraftServer>> callbacks=new ArrayList<>();public void register(Consumer<MinecraftServer> c){callbacks.add(c);}public void fire(MinecraftServer s){for(var c:callbacks)c.accept(s);}}}''',
    'net/minecraft/world/entity/player/Player.java': 'package net.minecraft.world.entity.player;import java.util.UUID;public class Player {public UUID getUUID(){return new UUID(0,1);}}',
    'net/minecraft/resources/ResourceLocation.java': 'package net.minecraft.resources;public class ResourceLocation {public static ResourceLocation parse(String s){return new ResourceLocation();}}',
    'net/minecraft/server/MinecraftServer.java': '''package net.minecraft.server;import java.util.*;import java.util.concurrent.ConcurrentLinkedQueue;import net.minecraft.world.entity.player.Player;
public class MinecraftServer {public final Queue<Runnable> queued=new ConcurrentLinkedQueue<>();public void execute(Runnable r){queued.add(r);}public Manager getPlayerList(){return new Manager();}public boolean isPaused(){return false;}public Rates tickRateManager(){return new Rates();}public static class Rates {public boolean runsNormally(){return true;}}public static class Manager {public Player getPlayer(UUID id){return null;}}}''',
    'org/slf4j/Logger.java': 'package org.slf4j;public class Logger {public void error(String s,Throwable t){} public void warn(String s,Throwable t){}}',
    'org/slf4j/LoggerFactory.java': 'package org.slf4j;public class LoggerFactory {public static Logger getLogger(String s){return new Logger();}}',
    'cc/thonly/reverie_dreams/ReverieDreams.java': 'package cc.thonly.reverie_dreams;public class ReverieDreams {public static final org.slf4j.Logger LOGGER=new org.slf4j.Logger();}',
    'nota/model/Song.java': 'package nota.model;public class Song {public float getDelay(){return 1.0f;}public short getLength(){return 100;}}',
    'nota/model/Playlist.java': 'package nota.model;public class Playlist {private final Song song;public Playlist(Song s){song=s;}public Song get(int n){return song;}public boolean hasNext(int i){return false;}public boolean exist(int i){return i==0;}}',
    'nota/model/RepeatMode.java': 'package nota.model;public enum RepeatMode {ALL,NONE}',
    'nota/model/playmode/ChannelMode.java': 'package nota.model.playmode;public interface ChannelMode {}',
    'nota/model/playmode/MonoMode.java': 'package nota.model.playmode;public class MonoMode implements ChannelMode {}',
}
for name in ('Getter', 'Setter', 'ToString'):
    STUBS['lombok/' + name + '.java'] = 'package lombok; public @interface ' + name + ' {}'
for name, method in (('SongEndEvent', 'onSongEnd'), ('SongStartEvent', 'onSongStart'), ('SongTickEvent', 'onSongTick')):
    STUBS['nota/event/' + name + '.java'] = 'package nota.event;import nota.player.SongPlayer;public class ' + name + ' {public static final ' + name + ' EVENT=new ' + name + '();public ' + name + ' invoker(){return this;}public void ' + method + '(SongPlayer p){}}'
PROBE = '''package nota.player;
import java.util.*;import nota.Nota;import nota.model.Song;import net.minecraft.world.entity.player.Player;import net.minecraft.server.MinecraftServer;import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;import cc.thonly.reverie_dreams.server.DelayedTask;
public class LifecycleRegression {
 static final MinecraftServer SERVER=new MinecraftServer();static int failed;
 static class TestPlayer extends SongPlayer {TestPlayer(){super(new Song());}public void playTick(Player p,int tick){}}
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static void run(String name,Runnable test){try{test.run();System.out.println("PASS "+name);}catch(AssertionError e){failed++;System.out.println("FAIL "+name+": "+e.getMessage());}finally{DelayedTask.TASKS.clear();}}
 public static void main(String[] args){
  new Nota().onInitialize();ServerLifecycleEvents.SERVER_STARTED.fire(SERVER);
  run("stopped task cannot execute",()->{int[] calls={0};var t=DelayedTask.create(SERVER,1,()->calls[0]++);t.stop();t.tick();check(calls[0]==0,"canceled action ran");});
  run("throwing one-shot task is not replayed",()->{int[] calls={0};var t=DelayedTask.create(SERVER,1,()->{calls[0]++;throw new RuntimeException("expected");});for(int i=0;i<2;i++)try{t.tick();}catch(RuntimeException expected){}check(calls[0]==1 && !DelayedTask.TASKS.contains(t),"failed task remains replayable");});
  run("destroy cancels timer and listener registration",()->{var p=new TestPlayer();UUID id=new UUID(0,123);try{p.addPlayer(id);p.destroy();p.destroy();var registered=Nota.getSongPlayersByPlayer(id);boolean rejected=false;try{p.timer.schedule(new TimerTask(){public void run(){}},100000);}catch(IllegalStateException expected){rejected=true;}check(rejected,"destroyed timer accepts work");check(registered==null || !registered.contains(p),"destroyed player remains registered");p.setPlaying(true);check(!p.isPlaying(),"destroyed player resurrected");}finally{p.timer.cancel();p.removePlayer(id);}});
  run("stopping listener removes only that listener",()->{var p=new TestPlayer();UUID a=new UUID(0,321),b=new UUID(0,322);try{p.addPlayer(a);p.addPlayer(b);Nota.stopPlaying(a);check(!p.getPlayerUUIDs().contains(a) && p.getPlayerUUIDs().contains(b),"listener removal affected other listener");}finally{p.destroy();p.timer.cancel();}});
  run("repeating task has one cancelable identity",()->{int[] calls={0};var t=DelayedTask.repeatUntil(SERVER,()->calls[0]>=3,1,()->calls[0]+=10,()->calls[0]++);for(int i=0;i<4;i++)DelayedTask.tick(SERVER);check(calls[0]==13 && !DelayedTask.TASKS.contains(t),"repeat did not finish exactly once");});
  run("canceling repeating task stops the chain",()->{int[] calls={0};var t=DelayedTask.repeatUntil(SERVER,()->false,1,()->{},()->calls[0]++);DelayedTask.tick(SERVER);t.stop();for(int i=0;i<4;i++)DelayedTask.tick(SERVER);check(calls[0]==1 && !DelayedTask.TASKS.contains(t),"canceled repeat requeued");});
  run("one failing task does not block another",()->{int[] calls={0};DelayedTask.create(SERVER,1,()->{throw new RuntimeException("expected");});DelayedTask.create(SERVER,1,()->calls[0]++);DelayedTask.tick(SERVER);check(calls[0]==1 && DelayedTask.TASKS.isEmpty(),"failure left task or blocked sibling");});
  run("server cleanup isolates task ownership",()->{var other=new MinecraftServer();var own=DelayedTask.create(SERVER,1,()->{});var foreign=DelayedTask.create(other,1,()->{});DelayedTask.clear(SERVER);check(!DelayedTask.TASKS.contains(own) && DelayedTask.TASKS.contains(foreign),"server cleanup affected another server");foreign.stop();});
  run("server stop destroys all registered players",()->{var p=new TestPlayer();UUID id=new UUID(0,444);try{p.addPlayer(id);ServerLifecycleEvents.SERVER_STOPPING.fire(SERVER);check(!Nota.isReceivingSong(id) && p.destroyed,"server stop retained music player");boolean rejected=false;try{p.timer.schedule(new TimerTask(){public void run(){}},100000);}catch(IllegalStateException expected){rejected=true;}check(rejected,"server stop retained timer");}finally{p.timer.cancel();}});
  if(failed!=0)throw new AssertionError(failed+" lifecycle regressions failed");
 }
}'''
def main():
    with tempfile.TemporaryDirectory(prefix='reverie-lifecycle-') as temp:
        base = Path(temp)
        src = base / 'src'
        for name, text in STUBS.items():
            path = src / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text)
        for name in ('nota/Nota.java', 'nota/player/SongPlayer.java', 'cc/thonly/reverie_dreams/server/DelayedTask.java'):
            path = src / name
            path.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / 'src/main/java' / name, path)
        (src / 'nota/player/LifecycleRegression.java').write_text(PROBE)
        subprocess.run([str(JAVA / 'javac'), '--release', '21', '-d', str(base / 'classes'), *map(str, src.rglob('*.java'))], check=True)
        subprocess.run([str(JAVA / 'java'), '-cp', str(base / 'classes'), 'nota.player.LifecycleRegression'], check=True, timeout=20)

if __name__ == '__main__':
    main()
