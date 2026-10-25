"""Run the actual NPC tick/seat-cleanup bodies with a narrow lifecycle fixture."""
from pathlib import Path
import os
import re
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
JAVA = Path(os.environ['JAVA_HOME']) / 'bin' if os.environ.get('JAVA_HOME') else Path(shutil.which('javac')).resolve().parent


def method(source, name):
    match = re.search(r'(?:public|private) void ' + name + r'\(\)\s*\{', source)
    if match is None:
        return ''
    end, depth = match.end(), 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[match.start():end]


FIXTURE = '''import java.util.*;
public class NPCDeathTest {
 static class Parent {
  boolean dead, removed, riding; int deathTime, nativeTicks;
  public void tick(){nativeTicks++;if(dead && ++deathTime>=20)removed=true;}
 }
 static class Pose {static final Pose STANDING=new Pose(), CROUCHING=new Pose();}
 static class NPCStates {static final Object NORMAL=new Object(),SEATED=new Object(),SNAKING=new Object();}
 static class ArmorStand {boolean removed;UUID id=UUID.randomUUID();void discard(){removed=true;}UUID getUUID(){return id;}}
 static class AABB {AABB(double a,double b,double c,double d,double e,double f){}}
 static class Level {
  List<ArmorStand> seats=new ArrayList<>();
  List<ArmorStand> getEntitiesOfClass(Class<ArmorStand> c,AABB b,java.util.function.Predicate<ArmorStand> p){return seats.stream().filter(p).toList();}
 }
 static class ServerLevel extends Level {Object getEntity(UUID id){return seats.stream().filter(s->s.id.equals(id)).findFirst().orElse(null);}}
 static class Navigation {void stop(){}}
 static class NPC extends Parent {
  Object npcState=NPCStates.NORMAL,lastNpcState,prevPos;
  ArmorStand seat;String seatUUID="";boolean sit;int spawned,updateAttackTick,maxUpdateAttackTick=40;
  final ServerLevel world=new ServerLevel();Pose pose=Pose.STANDING;
  Level level(){return world;}void updateHealth(){}void updateHunger(){}void updateHungerConsumption(){}void updateWorking(){}void updateName(){}void fixPitchYaw(){}void updateAttackType(){}
  Object position(){return null;}Navigation getNavigation(){return new Navigation();}Pose getPose(){return pose;}void setPose(Pose p){pose=p;}
  double getX(){return 0;}double getY(){return 0;}double getZ(){return 0;}void setSit(boolean s){sit=s;}
  boolean isDeadOrDying(){return dead;}void stopRiding(){riding=false;}
  void spawnSeatAndSit(){seat=new ArmorStand();seatUUID=seat.id.toString();world.seats.add(seat);riding=true;spawned++;}
  TICK_BODY
  CLEANUP_BODY
 }
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static void ticks(NPC npc){for(int i=0;i<20;i++)npc.tick();}
 public static void main(String[] args){
  NPC npc=new NPC();npc.npcState=NPCStates.SEATED;npc.spawnSeatAndSit();var seat=npc.seat;npc.dead=true;ticks(npc);
  check(npc.removed && npc.deathTime>=20,"Seated corpse never advanced native death ticks");
  check(seat.removed && npc.seat==null && npc.seatUUID.isEmpty() && !npc.sit && !npc.riding,"Death retained its seat or riding state");
  check(npc.spawned==1,"Dead NPC recreated its seat");System.out.println("PASS seated death advances and releases its seat without respawning it");
  NPC loaded=new NPC();loaded.npcState=NPCStates.SEATED;loaded.dead=true;ArmorStand saved=new ArmorStand();loaded.world.seats.add(saved);loaded.seatUUID=saved.id.toString();ticks(loaded);
  check(loaded.removed && saved.removed && loaded.spawned==0,"Reloaded corpse retained/recreated its saved seat");System.out.println("PASS reloaded seated corpse releases its saved seat");
  NPC standing=new NPC();standing.dead=true;ticks(standing);check(standing.removed,"Standing death stopped advancing");System.out.println("PASS standing death still advances");
  NPC living=new NPC();living.npcState=NPCStates.SEATED;living.tick();check(living.sit && living.riding && living.spawned==1 && !living.removed,"Living NPC lost seated behavior");living.npcState=NPCStates.NORMAL;var old=living.seat;living.tick();check(old.removed && living.seat==null && !living.sit,"Standing NPC retained its seat");System.out.println("PASS living sit/stand transition retains existing behavior");
  NPC corrupt=new NPC();corrupt.dead=true;corrupt.npcState=NPCStates.SEATED;corrupt.seatUUID="null";ticks(corrupt);check(corrupt.removed && corrupt.spawned==0,"Legacy missing seat blocked death");System.out.println("PASS legacy missing seat UUID does not block death");
 }
}'''


def main():
    source = (ROOT / 'src/main/java/cc/thonly/reverie_dreams/entity/npc/BaseNPCLikeEntity.java').read_text()
    fixture = FIXTURE.replace('TICK_BODY', method(source, 'tick')).replace('CLEANUP_BODY', method(source, 'removeSeat'))
    with tempfile.TemporaryDirectory(prefix='reverie-npc-death-') as temp:
        base = Path(temp)
        file = base / 'NPCDeathTest.java'
        file.write_text(fixture)
        subprocess.run([str(JAVA / 'javac'), '--release', '21', '-d', str(base), str(file)], check=True)
        subprocess.run([str(JAVA / 'java'), '-cp', str(base), 'NPCDeathTest'], cwd=base, check=True)


if __name__ == '__main__':
    main()
