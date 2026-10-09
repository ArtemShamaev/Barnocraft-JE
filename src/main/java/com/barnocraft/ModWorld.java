package com.barnocraft;

import com.jme3.math.Vector3f;
import java.util.*;

/** Engine primitives for mods. Entity AI and spawning rules belong to the Python packages. */
final class ModWorld {
    final World world;
    final Map<String,Map<String,Object>> definitions=new LinkedHashMap<>();
    final Map<String,Entity> entities=new LinkedHashMap<>();
    final List<Projectile> projectiles=new ArrayList<>();
    final Map<String,Object> storage=new LinkedHashMap<>();
    final ArrayDeque<Map<String,Object>> events=new ArrayDeque<>();
    final Map<String,Map<String,Object>> overlays=new LinkedHashMap<>();
    String notice="";
    private float shotCooldown;
    private int serial;
    static final class Entity {
        final String id,type;float x,y,z,vy,heading,cooldown,goalTime,hurtTime;int health;String owner="";
        float goalX,goalZ,stop;
        Entity(String id,String type,float x,float y,float z,int health){this.id=id;this.type=type;this.x=x;this.y=y;this.z=z;this.health=health;goalX=x;goalZ=z;}
        Map<String,Object> data(){return Map.of("id",id,"type",type,"x",x,"y",y,"z",z,"health",health,"owner",owner,"heading",heading);}
    }
    static final class Projectile {
        final String source;final int damage;final Vector3f position,velocity;float life=6;
        Projectile(String source,Vector3f pos,Vector3f velocity,int damage){this.source=source;this.position=pos;this.velocity=velocity;this.damage=damage;}
    }
    ModWorld(World world){this.world=world;}
    void event(String name,Map<String,Object> data){if(name.equals("notice"))notice=Json.str(data,"text","");if(events.size()>=256)events.removeFirst();events.add(Map.of("event",name,"data",data));}
    List<Map<String,Object>> drainEvents(){var result=List.copyOf(events);events.clear();return result;}
    void register(String namespace,Map<String,Object> manifest) {
        for(Object raw:Json.list(manifest.getOrDefault("items",List.of()))) {
            var d=Json.obj(raw);String id=owned(namespace,Json.str(d,"id",""));
            Object icon=d.get("icon"); if(icon!=null) validatePixels(Json.list(icon),32);
            if(d.containsKey("food"))Json.integer(d,"food",0,0,20);
            if(d.containsKey("cooking_result"))owned(namespace,Json.str(d,"cooking_result",""));
            if(d.containsKey("projectile")){var shot=Json.obj(d.get("projectile"));owned(namespace,Json.str(shot,"ammo",""));bounded(shot,"speed",20,5,30);bounded(shot,"cooldown",.6,.2,5);Json.integer(shot,"damage",4,1,10);}
            ItemType.register(id,d);
        }
        for(Object raw:Json.list(manifest.getOrDefault("entities",List.of()))) {
            var d=Json.obj(raw);String id=owned(namespace,Json.str(d,"id",""));
            Json.integer(d,"health",8,1,100); bounded(d,"speed",2,0,6);bounded(d,"height",1.7,.2,3);bounded(d,"radius",.3,.1,1);Json.integer(d,"damage",2,1,10);
            var parts=Json.list(d.getOrDefault("model",List.of()));if(parts.size()>48)throw new IllegalArgumentException("Model part limit");
            for(Object part:parts){var m=Json.obj(part);for(String key:List.of("x","y","z"))bounded(m,key,0,-3,3);
                for(String key:List.of("sx","sy","sz"))bounded(m,key,.2,.005,3);
                if(!Json.str(m,"color","dddddd").matches("[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Model color");}
            for(Object rawLoot:Json.list(d.getOrDefault("loot",List.of()))) {var loot=Json.obj(rawLoot);String item=Json.str(loot,"item","");if(ItemType.valueOf(item).definition.isEmpty() && item.contains(":"))throw new IllegalArgumentException("Unregistered loot item");Json.integer(loot,"count",1,1,16);}
            definitions.put(id,Map.copyOf(d));
        }
        for(Object raw:Json.list(manifest.getOrDefault("recipes",List.of())))ItemType.registerRecipe(namespace,Json.obj(raw));
    }
    static String owned(String namespace,String id){if(!id.matches(java.util.regex.Pattern.quote(namespace)+":[a-z][a-z0-9_]*"))throw new IllegalArgumentException("Foreign/invalid namespace: "+id);return id;}
    static double bounded(Map<String,Object> m,String key,double fallback,double min,double max){double v=Json.num(m,key,fallback);if(v<min || v>max)throw new IllegalArgumentException(key+": out of bounds");return v;}
    static void validatePixels(List<?> rows,int limit){if(rows.isEmpty() || rows.size()>limit)throw new IllegalArgumentException("Pixel dimensions");int width=Json.list(rows.get(0)).size();if(width<1 || width>limit)throw new IllegalArgumentException("Pixel width");for(Object r:rows){if(Json.list(r).size()!=width)throw new IllegalArgumentException("Ragged pixels");for(Object c:Json.list(r))if(!(c instanceof String str) || !str.matches("[0-9a-fA-F]{6}([0-9a-fA-F]{2})?"))throw new IllegalArgumentException("Pixel color");}}
    Entity spawn(String type,float x,float z) {
        Map<String,Object> d=definitions.get(type);if(d==null)throw new IllegalArgumentException("Unknown entity type");
        if(entities.size()>=64 || !world.inside((int)x,6,(int)z))throw new IllegalArgumentException("Entity cap or world boundary");
        float y=surface((int)x,(int)z)+1;
        Entity e=new Entity("mod:"+(++serial),type,x,y,z,Json.integer(d,"health",8,1,100));
        if(world.get((int)x,(int)y,(int)z)==Block.WATER || !fits(e,x,y,z))throw new IllegalArgumentException("Blocked spawn");
        entities.put(e.id,e);event("entity_spawn",e.data());return e;
    }
    int surface(int x,int z){int top=World.HEIGHT-2;for(int y=top;y>=0;y--)if(world.get(x,y,z).solid() || world.get(x,y,z)==Block.WATER)return y;return 0;}
    Entity controlled(String namespace,String id){Entity e=entities.get(id);if(e==null || !e.type.startsWith(namespace+":"))throw new IllegalArgumentException("Entity is not owned by this mod");return e;}
    Vector3f position(String id,Player player){if(id.equals("player"))return player.position.add(0,.8f,0);Entity e=entities.get(id);if(e!=null)return new Vector3f(e.x,e.y+height(e)*.5f,e.z);for(Zombie z:world.zombies())if(z.id().equals(id))return new Vector3f(z.x(),z.y()+.9f,z.z());for(Sheep s:world.sheep())if(s.id().equals(id))return new Vector3f(s.x(),s.y()+.6f,s.z());return null;}
    private float height(Entity e){return (float)Json.num(definitions.get(e.type),"height",1.7);}
    boolean fits(Entity e,float x,float y,float z){
        float r=(float)Json.num(definitions.get(e.type),"radius",.3);
        if(x<r || z<r || x>=world.width()-r || z>=world.depth()-r || y<0)return false;
        for(int bx=(int)Math.floor(x-r);bx<=Math.floor(x+r);bx++)for(int bz=(int)Math.floor(z-r);bz<=Math.floor(z+r);bz++)
            for(int by=(int)Math.floor(y+.001f);by<=Math.floor(y+height(e)-.01f);by++)if(world.get(bx,by,bz).solid())return false;
        return true;
    }
    void tick(float tpf,Player player){
        float dt=Math.min(.1f,Math.max(0,tpf));shotCooldown=Math.max(0,shotCooldown-dt);
        for(Entity e:entities.values()){
            e.cooldown=Math.max(0,e.cooldown-dt);e.goalTime=Math.max(0,e.goalTime-dt);e.hurtTime=Math.max(0,e.hurtTime-dt);
            float remaining=dt;
            while(remaining>0){float step=Math.min(remaining,1f/60);remaining-=step;
                float dx=e.goalX-e.x,dz=e.goalZ-e.z,dist=(float)Math.sqrt(dx*dx+dz*dz);
                boolean ground=!fits(e,e.x,e.y-.04f,e.z);
                if(e.goalTime>0 && dist>e.stop){e.heading=(float)Math.atan2(dx,dz);float speed=(float)Json.num(definitions.get(e.type),"speed",2);
                    float mx=dx/dist*speed*step,mz=dz/dist*speed*step;
                    if(ground && !fits(e,e.x+mx,e.y,e.z+mz) && fits(e,e.x+mx,e.y+1.1f,e.z+mz))e.vy=8;
                    if(fits(e,e.x+mx,e.y,e.z))e.x+=mx;if(fits(e,e.x,e.y,e.z+mz))e.z+=mz;
                }
                e.vy-=22*step;float next=e.y+e.vy*step;
                if(fits(e,e.x,next,e.z))e.y=next;else {float lo=0,hi=1;for(int i=0;i<10;i++){float mid=(lo+hi)/2;if(fits(e,e.x,e.y+(next-e.y)*mid,e.z))lo=mid;else hi=mid;}e.y+=(next-e.y)*lo;e.vy=0;}
            }
        }
        for(Iterator<Projectile> it=projectiles.iterator();it.hasNext();){Projectile p=it.next();p.life-=dt;boolean hit=false;
            int steps=Math.max(1,(int)Math.ceil(p.velocity.length()*dt/.15f));float h=dt/steps;
            for(int i=0;i<steps && !hit;i++){
                Vector3f delta=p.velocity.mult(h);float length=delta.length();
                if(world.raycast(p.position,delta,length)!=null){hit=true;break;}
                p.position.addLocal(delta);p.velocity.y-=8*h;
                List<String> targets=new ArrayList<>();targets.add("player");targets.addAll(entities.keySet());for(Zombie z:world.zombies())targets.add(z.id());for(Sheep s:world.sheep())targets.add(s.id());
                for(String id:targets){if(id.equals(p.source))continue;Entity shooter=entities.get(p.source);if(shooter!=null && id.equals(shooter.owner))continue;
                    Vector3f pos=position(id,player);float radius=id.equals("player")?.6f:entities.containsKey(id)?Math.max(.4f,height(entities.get(id))*.45f):.65f;
                    if(pos!=null && p.position.distanceSquared(pos)<radius*radius){damage(id,p.damage,p.source,player);hit=true;break;}
                }
            }
            if(hit || p.life<=0)it.remove();
        }
    }
    boolean damage(String id,int amount,String source,Player player){
        if(id.equals("player")){boolean result=player.damage(amount);if(result)event("damage",Map.of("source",source,"target",id,"amount",amount));return result;}
        Entity e=entities.get(id);if(e==null)return world.damageZombie(id,amount,source) || world.damageSheep(id,amount,source);
        e.health-=amount;e.hurtTime=.28f;event("damage",Map.of("source",source,"target",id,"amount",amount));
        if(e.health<=0){entities.remove(id);for(Object raw:Json.list(definitions.get(e.type).getOrDefault("loot",List.of()))){var d=Json.obj(raw);world.drop(ItemType.valueOf(Json.str(d,"item","")),Json.integer(d,"count",1,1,16),e.x,e.y,e.z);}event("entity_death",e.data());}
        return true;
    }
    boolean attack(Entity e,String target,boolean ranged,Player player){
        Vector3f from=position(e.id,player),to=position(target,player);if(to==null || target.equals(e.owner) || e.cooldown>0)return false;
        float range=ranged?18:1.65f;if(from.distance(to)>range || world.raycast(from,to.subtract(from),from.distance(to))!=null)return false;
        var d=definitions.get(e.type);int damage=Json.integer(d,"damage",2,1,10);e.heading=(float)Math.atan2(to.x-from.x,to.z-from.z);
        e.cooldown=ranged?1.8f:1f;
        if(ranged)launch(e.id,from,to.subtract(from),16,damage);else damage(target,damage,e.id,player);return true;
    }
    private void launch(String source,Vector3f from,Vector3f direction,float speed,int damage){if(projectiles.size()<128){Vector3f v=direction.normalize().mult(speed);projectiles.add(new Projectile(source,from.clone(),v,damage));event("projectile_spawn",Map.of("source",source));}}
    Entity rayEntity(Vector3f origin,Vector3f direction,float reach){
        Vector3f d=direction.normalize();Entity found=null;float closest=reach;World.Hit block=world.raycast(origin,d,reach);
        if(block!=null){Door.Contact c=new Door.Bounds(block.x(),block.y(),block.z(),block.x()+1,block.y()+1,block.z()+1).ray(origin,d,reach);if(c!=null)closest=c.distance();}
        for(Entity e:entities.values()) {float r=(float)Json.num(definitions.get(e.type),"radius",.3);Door.Contact c=new Door.Bounds(e.x-r,e.y,e.z-r,e.x+r,e.y+height(e),e.z+r).ray(origin,d,reach);if(c!=null && c.distance()<closest){closest=c.distance();found=e;}}
        return found;
    }
    boolean playerAction(boolean use,Vector3f eye,Vector3f direction,Player player,Inventory inventory,int selected){
        ItemType held=inventory.hotbarItemAt(selected);
        if(use && held!=null && held.definition.containsKey("projectile")){
            if(shotCooldown>0)return true;var d=Json.obj(held.definition.get("projectile"));ItemType ammo=ItemType.valueOf(Json.str(d,"ammo",""));
            if(inventory.take(ammo,1)){launch("player",eye.add(direction.mult(.4f)),direction,(float)bounded(d,"speed",20,5,30),Json.integer(d,"damage",4,1,10));inventory.damageToolAt(selected);shotCooldown=(float)bounded(d,"cooldown",.6,.2,5);}
            else event("notice",Map.of("text","Нужны стрелы"));return true;
        }
        Entity e=rayEntity(eye,direction,Player.REACH);if(e==null)return false;
        if(!use){damage(e.id,1,"player",player);inventory.damageToolAt(selected);return true;}
        String tame=Json.str(definitions.get(e.type),"tame_item","");
        if(e.owner.isEmpty() && held!=null && held.name().equals(tame) && inventory.consumeAt(selected,held)){
            e.owner="player";e.health=Json.integer(definitions.get(e.type),"health",8,1,100);event("entity_tamed",e.data());event("notice",Map.of("text","Волк приручён"));
        }else event("entity_use",Map.of("entity",e.id,"item",held==null?"":held.name()));return true;
    }
    Map<String,Object> snapshot(Player player,Inventory inv,int selected,float yaw){
        List<Map<String,Object>> list=new ArrayList<>();for(Entity e:entities.values())list.add(e.data());
        for(Zombie z:world.zombies())list.add(Map.of("id",z.id(),"type","core:zombie","x",z.x(),"y",z.y(),"z",z.z(),"health",z.health(),"owner",""));
        for(Sheep s:world.sheep())list.add(Map.of("id",s.id(),"type","core:sheep","x",s.x(),"y",s.y(),"z",s.z(),"health",s.health(),"owner",""));
        ItemType held=inv.hotbarItemAt(selected);
        return Map.of("player",Map.of("id","player","x",player.position.x,"y",player.position.y,"z",player.position.z,"health",player.health(),"yaw",yaw,"item",held==null?"":held.name()),"time",world.dayTime(),"entities",list);
    }
    String save(){List<Object> items=new ArrayList<>();for(ItemType i:ItemType.values())if(!i.definition.isEmpty())items.add(i.definition);
        List<Object> dropped=new ArrayList<>();for(DroppedItem d:world.droppedItems())dropped.add(Map.of("item",d.type().name(),"x",d.x(),"y",d.y(),"z",d.z()));
        return Json.stringify(Map.of("items",items,"definitions",definitions,"entities",entities.values().stream().map(Entity::data).toList(),"storage",storage,"serial",serial,"drops",dropped));}
    void restore(String text){var m=Json.obj(Json.parse(text));for(Object raw:Json.list(m.getOrDefault("items",List.of()))){var d=Json.obj(raw);ItemType.register(Json.str(d,"id",""),d);}
        for(var e:Json.obj(m.getOrDefault("definitions",Map.of())).entrySet())definitions.put(e.getKey(),Json.obj(e.getValue()));
        serial=Json.integer(m,"serial",0,0,Integer.MAX_VALUE);
        for(Object raw:Json.list(m.getOrDefault("entities",List.of()))){var d=Json.obj(raw);String type=Json.str(d,"type","");if(!definitions.containsKey(type))continue;
            Entity e=new Entity(Json.str(d,"id",""),type,(float)Json.num(d,"x",0),(float)Json.num(d,"y",0),(float)Json.num(d,"z",0),Json.integer(d,"health",1,1,100));e.owner=Json.str(d,"owner","");e.heading=(float)Json.num(d,"heading",0);entities.put(e.id,e);if(entities.size()>64)throw new IllegalArgumentException("Saved entity cap");}
        storage.putAll(Json.obj(m.getOrDefault("storage",Map.of())));
        for(Object raw:Json.list(m.getOrDefault("drops",List.of()))){var d=Json.obj(raw);world.drop(ItemType.valueOf(Json.str(d,"item","")),1,(float)Json.num(d,"x",0),(float)Json.num(d,"y",0),(float)Json.num(d,"z",0));}
    }
}
