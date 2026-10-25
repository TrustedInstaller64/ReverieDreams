"""Exercise the actual session manager and scheduler with non-loading world substitutes."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile
from test_music_lifecycle import ROOT, JAVA, STUBS as BASE_STUBS

STUBS = dict(BASE_STUBS)
STUBS.update({
 'net/minecraft/core/BlockPos.java': '''package net.minecraft.core;import net.minecraft.world.phys.Vec3;public record BlockPos(int getX,int getY,int getZ){public int getX(){return getX;}public int getY(){return getY;}public int getZ(){return getZ;}public BlockPos immutable(){return this;}public long asLong(){return ((long)getX & 0x3ffffff)<<38|((long)getZ & 0x3ffffff)<<12|((long)getY & 0xfff);}public static BlockPos of(long p){return new BlockPos((int)(p>>38),(int)(p<<52>>52),(int)(p<<26>>38));}public double distToCenterSqr(Vec3 p){return 0;}}''',
 'net/minecraft/core/particles/ParticleTypes.java':'package net.minecraft.core.particles;public class ParticleTypes {public static final Object NOTE=new Object();}',
 'net/minecraft/network/chat/Component.java':'package net.minecraft.network.chat;public class Component {public static Component translatable(String s,Object... args){return new Component();}}',
 'net/minecraft/world/phys/Vec3.java':'package net.minecraft.world.phys;public class Vec3 {public double x,y,z;}',
 'net/minecraft/world/level/ChunkPos.java':'package net.minecraft.world.level;public class ChunkPos {public final int x,z;public ChunkPos(int x,int z){this.x=x;this.z=z;}}',
 'net/minecraft/world/level/block/state/properties/NoteBlockInstrument.java':'package net.minecraft.world.level.block.state.properties;public enum NoteBlockInstrument {PLING}',
 'net/minecraft/world/level/block/state/BlockState.java':'package net.minecraft.world.level.block.state;public class BlockState {public Object getBlock(){return new cc.thonly.reverie_dreams.block.MusicBlock();}}',
 'cc/thonly/reverie_dreams/block/MusicBlock.java':'package cc.thonly.reverie_dreams.block;public class MusicBlock {}',
 'cc/thonly/reverie_dreams/block/entity/MusicBlockEntity.java':'package cc.thonly.reverie_dreams.block.entity;public class MusicBlockEntity {public boolean removed;public boolean isRemoved(){return removed;}}',
 'cc/thonly/reverie_dreams/item/prop/MusicalInstrumentItem.java':'package cc.thonly.reverie_dreams.item.prop;public class MusicalInstrumentItem {}',
 'net/minecraft/world/item/ItemStack.java':'package net.minecraft.world.item;public class ItemStack {public Object getItem(){return new cc.thonly.reverie_dreams.item.prop.MusicalInstrumentItem();}}',
 'net/minecraft/world/entity/LivingEntity.java':'''package net.minecraft.world.entity;import net.minecraft.world.level.Level;import net.minecraft.world.item.ItemStack;import net.minecraft.world.phys.Vec3;import java.util.UUID;public class LivingEntity {public Level level(){return null;}public String getStringUUID(){return "actor";}public UUID getUUID(){return new UUID(0,1);}public boolean isAlive(){return true;}public boolean isRemoved(){return false;}public ItemStack getMainHandItem(){return new ItemStack();}public ItemStack getOffhandItem(){return new ItemStack();}public Vec3 getViewVector(float f){return new Vec3();}public double getX(){return 0;}public double getY(){return 0;}public double getZ(){return 0;}public float getEyeHeight(){return 1;}}''',
 'net/minecraft/server/level/ServerPlayer.java':'package net.minecraft.server.level;import net.minecraft.network.chat.Component;import net.minecraft.world.phys.Vec3;public class ServerPlayer extends net.minecraft.world.entity.LivingEntity {public Vec3 position(){return new Vec3();}public void displayClientMessage(Component c,boolean b){}}',
 'net/minecraft/server/MinecraftServer.java':'''package net.minecraft.server;import java.util.*;import net.minecraft.server.level.ServerPlayer;public class MinecraftServer {public Manager getPlayerList(){return new Manager();}public static class Manager {public List<ServerPlayer> getPlayers(){return List.of();}}}''',
 'net/minecraft/world/level/Level.java':'''package net.minecraft.world.level;import net.minecraft.core.BlockPos;import net.minecraft.server.MinecraftServer;import cc.thonly.reverie_dreams.block.entity.MusicBlockEntity;import net.minecraft.world.level.block.state.BlockState;public class Level {public boolean loaded=true,powered=true;public int reads;public MusicBlockEntity owner=new MusicBlockEntity();public final MinecraftServer server=new MinecraftServer();public MinecraftServer getServer(){return server;}public boolean hasChunkAt(BlockPos p){return loaded;}private void read(){reads++;if(!loaded)throw new AssertionError("unloaded chunk queried");}public MusicBlockEntity getBlockEntity(BlockPos p){read();return owner;}public BlockState getBlockState(BlockPos p){read();return new BlockState();}public boolean hasNeighborSignal(BlockPos p){read();return powered;}}''',
 'net/minecraft/server/level/ServerLevel.java':'package net.minecraft.server.level;import java.util.*;public class ServerLevel extends net.minecraft.world.level.Level {public List<ServerPlayer> players(){return List.of();}public void sendParticles(Object o,double x,double y,double z,int c,double a,double b,double d,double speed){}}',
 'nota/player/SongPlayer.java':'''package nota.player;import net.minecraft.server.level.ServerPlayer;import net.minecraft.resources.ResourceLocation;import nota.model.RepeatMode;public class SongPlayer {public boolean playing,destroyed;public void addPlayer(ServerPlayer p){}public boolean hasPlayer(ServerPlayer p){return false;}public void setPlaying(boolean b){playing=b;}public boolean isPlaying(){return playing;}public void destroy(){destroyed=true;playing=false;}public void setDistance(int n){}public void setRepeatMode(RepeatMode m){}public void setAutoDestroy(boolean b){}public void setId(ResourceLocation id){}}''',
 'nota/player/PositionSongPlayer.java':'package nota.player;import nota.model.Song;import net.minecraft.world.level.Level;import net.minecraft.core.BlockPos;public class PositionSongPlayer extends SongPlayer {public PositionSongPlayer(Song s,Level w){}public void setBlockPos(BlockPos p){}}',
 'nota/player/EntitySongPlayer.java':'package nota.player;import nota.model.Song;import net.minecraft.world.entity.LivingEntity;public class EntitySongPlayer extends SongPlayer {public EntitySongPlayer(Song s){}public void setEntity(LivingEntity e){}}',
 'nota/utils/NBSDecoderPlus.java':'''package nota.utils;import java.io.File;import nota.model.Song;import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;public class NBSDecoderPlus {public static int reads;public static Song parse(File f){reads++;if(f.getName().equals("broken.nbs"))throw new IllegalArgumentException("broken fixture");return new Song();}public static Song parse(File f,NoteBlockInstrument n){return parse(f);}}''',
 'org/slf4j/Logger.java':'package org.slf4j;public class Logger {public void error(String s,Throwable e){}public void error(String s,Object a,Throwable e){}public void warn(String s,Throwable e){}}',
 'org/slf4j/LoggerFactory.java':'package org.slf4j;public class LoggerFactory {public static Logger getLogger(Class<?> c){return new Logger();}public static Logger getLogger(String s){return new Logger();}}',
})
PROBE='''package test;
import java.nio.file.*;import cc.thonly.reverie_dreams.util.TouhouNotaUtils;import cc.thonly.reverie_dreams.server.DelayedTask;import net.minecraft.core.BlockPos;import net.minecraft.server.level.ServerLevel;import cc.thonly.reverie_dreams.block.entity.MusicBlockEntity;import nota.utils.NBSDecoderPlus;
public class SessionRegression {
 static final BlockPos POS=new BlockPos(-6269,66,4249);
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static void ticks(ServerLevel w,int n){for(int i=0;i<n;i++)DelayedTask.tick(w.getServer());}
 static void clean(ServerLevel w){TouhouNotaUtils.stopAll(w.getServer());DelayedTask.clear(w.getServer());}
 public static void main(String[] args)throws Exception{
  Files.createDirectories(TouhouNotaUtils.PATH);for(String s:new String[]{"one.nbs","two.nbs","broken.nbs"})Files.writeString(TouhouNotaUtils.PATH.resolve(s),"fixture");
  var w=new ServerLevel();
  TouhouNotaUtils.playAt(w,POS,"one.nbs");TouhouNotaUtils.playAt(w,POS,"one.nbs");check(DelayedTask.TASKS.size()==1,"duplicate pending starts");ticks(w,2);check(DelayedTask.TASKS.size()==1,"duplicate checking tasks");System.out.println("PASS duplicate start requests share one pending/playback task");
  int reads=w.reads;w.loaded=false;ticks(w,4);check(w.reads==reads,"unloaded world queried");check(DelayedTask.TASKS.isEmpty()&&!TouhouNotaUtils.blockMusicPlayCache.containsKey(w),"unloaded session retained");System.out.println("PASS unloaded chunk never queried and session released");
  w.loaded=true;TouhouNotaUtils.playAt(w,POS,"one.nbs");ticks(w,2);var old=TouhouNotaUtils.blockMusicPlayCache.get(w).get(POS.asLong());var owner=w.owner;w.owner=new MusicBlockEntity();TouhouNotaUtils.playAt(w,POS,"two.nbs");TouhouNotaUtils.stopAt(w,POS,owner);ticks(w,2);var replacement=TouhouNotaUtils.blockMusicPlayCache.get(w).get(POS.asLong());check(old.destroyed&&replacement!=old&&replacement.isPlaying(),"old cleanup removed replacement");System.out.println("PASS replaced block/player survives stale owner cleanup");clean(w);
  TouhouNotaUtils.playAt(w,POS,"one.nbs");w.powered=false;ticks(w,2);check(!TouhouNotaUtils.blockMusicPlayCache.containsKey(w)&&DelayedTask.TASKS.isEmpty(),"powered-off pending start ran");System.out.println("PASS power loss cancels pending start");w.powered=true;
  int parses=NBSDecoderPlus.reads;for(int i=0;i<80;i++){TouhouNotaUtils.playAt(w,POS,"broken.nbs");ticks(w,1);}check(NBSDecoderPlus.reads==parses+1,"bad file parsed on every tick");check(DelayedTask.TASKS.isEmpty(),"failed load left active timer task");System.out.println("PASS broken song load is bounded until explicit reset");clean(w);
  TouhouNotaUtils.playAt(w,POS,"one.nbs");ticks(w,2);TouhouNotaUtils.stopChunk(w,new net.minecraft.world.level.ChunkPos(-392,265));check(DelayedTask.TASKS.isEmpty()&&!TouhouNotaUtils.blockMusicPlayCache.containsKey(w),"chunk cleanup retained session");System.out.println("PASS negative chunk coordinates cleaned correctly");
  boolean rejected=false;try{TouhouNotaUtils.getFilePath("../other.nbs");}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"path escaped music directory");check(TouhouNotaUtils.getFileNames().equals(java.util.List.of("broken.nbs","one.nbs","two.nbs")),"song order unstable");System.out.println("PASS song ordering and path validation");
 }
}'''

def main():
    home = Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle')))
    lombok = next((home / 'caches/modules-2/files-2.1/org.projectlombok/lombok/1.18.34').rglob('*.jar'))
    with tempfile.TemporaryDirectory(prefix='reverie-sessions-') as temp:
        base=Path(temp);src=base/'src'
        for name,text in STUBS.items():
            p=src/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text)
        for name in ('cc/thonly/reverie_dreams/util/TouhouNotaUtils.java','cc/thonly/reverie_dreams/server/DelayedTask.java'):
            p=src/name;p.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(ROOT/'src/main/java'/name,p)
        p=src/'test/SessionRegression.java';p.parent.mkdir(parents=True,exist_ok=True);p.write_text(PROBE)
        subprocess.run([str(JAVA/'javac'),'--release','21','-cp',str(lombok),'-processorpath',str(lombok),'-d',str(base/'classes'),*map(str,src.rglob('*.java'))],check=True)
        subprocess.run([str(JAVA/'java'),'-cp',str(base/'classes'),'test.SessionRegression'],cwd=base,check=True,timeout=20)

if __name__=='__main__':
    main()
