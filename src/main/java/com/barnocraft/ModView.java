package com.barnocraft;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.*;
import com.jme3.scene.*;
import com.jme3.scene.shape.Box;
import com.jme3.texture.*;
import com.jme3.texture.plugins.AWTLoader;
import java.awt.image.BufferedImage;
import java.util.*;

/** Renders declarative cuboid models, projectiles and mod pixel assets. */
final class ModView {
    final Node node=new Node("Mod entities"),arrows=new Node("Mod projectiles");
    final ModWorld state;final AssetManager assets;final Map<String,Node> models=new HashMap<>();final Map<String,Material> materials=new HashMap<>();
    ModView(AssetManager assets,ModWorld state){this.assets=assets;this.state=state;node.attachChild(arrows);}
    static Texture pixels(List<?> rows){int h=rows.size(),w=Json.list(rows.get(0)).size();BufferedImage img=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){String c=Json.list(rows.get(y)).get(x).toString();long value=Long.parseLong(c,16);int argb=c.length()==8?(int)((value>>>8)|((value&255)<<24)):(int)value|0xff000000;img.setRGB(x,y,argb);}
        Texture t=new Texture2D(new AWTLoader().load(img,true));t.setMagFilter(Texture.MagFilter.Nearest);t.setMinFilter(Texture.MinFilter.NearestNoMipMaps);return t;}
    static Texture item(ItemType item){if(item.definition.containsKey("icon"))return pixels(Json.list(item.definition.get("icon")));return pixels(List.of(List.of("b67ac4","51465c"),List.of("51465c","b67ac4")));}
    Material material(String color){return materials.computeIfAbsent(color,k->{Material m=new Material(assets,"Common/MatDefs/Misc/Unshaded.j3md");int c=Integer.parseInt(k,16);m.setColor("Color",new ColorRGBA((c>>16&255)/255f,(c>>8&255)/255f,(c&255)/255f,1));return m;});}
    void update(){
        models.entrySet().removeIf(entry->{if(state.entities.containsKey(entry.getKey()))return false;entry.getValue().removeFromParent();return true;});
        for(var e:state.entities.values()){
            Node model=models.computeIfAbsent(e.id,k->{Node n=new Node(e.type);var definition=state.definitions.get(e.type);
                for(Object raw:Json.list(definition.getOrDefault("model",List.of()))){var p=Json.obj(raw);String name=Json.str(p,"name","part");
                    Geometry g=new Geometry(name,new Box((float)Math.abs(Json.num(p,"sx",.2))*.5f,(float)Math.abs(Json.num(p,"sy",.2))*.5f,(float)Math.abs(Json.num(p,"sz",.2))*.5f));
                    Material normal=material(Json.str(p,"color","dddddd")).clone(),hurt=normal.clone();
                    hurt.setColor("Color",new ColorRGBA(1f,.48f,.48f,1));
                    g.setUserData("normalMaterial",normal);g.setUserData("hurtMaterial",hurt);g.setMaterial(normal);
                    g.setLocalTranslation((float)Json.num(p,"x",0),(float)Json.num(p,"y",0),(float)Json.num(p,"z",0));
                    g.setUserData("tamed",Boolean.TRUE.equals(p.get("tamed_only")));n.attachChild(g);
                }node.attachChild(n);return n;});
            model.setLocalTranslation(e.x,e.y,e.z);model.setLocalRotation(new Quaternion().fromAngleAxis(e.heading,Vector3f.UNIT_Y));
            for(Spatial part:model.getChildren()){
                if(Boolean.TRUE.equals(part.getUserData("tamed")))part.setCullHint(e.owner.isEmpty()?Spatial.CullHint.Always:Spatial.CullHint.Inherit);
                if(part instanceof Geometry geometry)geometry.setMaterial((Material)geometry.getUserData(e.hurtTime>0?"hurtMaterial":"normalMaterial"));
                if(part.getName().contains("leg"))part.setLocalRotation(new Quaternion().fromAngles((float)Math.sin((e.x+e.z)*5)*(part.getName().contains("left")?1:-1)*.35f,0,0));
            }
        }
        arrows.detachAllChildren();for(var p:state.projectiles){Node arrow=new Node("Arrow");Geometry shaft=new Geometry("Shaft",new Box(.018f,.018f,.35f));shaft.setMaterial(material("aa8050"));arrow.attachChild(shaft);Geometry tip=new Geometry("Tip",new Box(.035f,.035f,.06f));tip.setMaterial(material("d9d9cc"));tip.setLocalTranslation(0,0,.37f);arrow.attachChild(tip);arrow.setLocalTranslation(p.position);arrow.setLocalRotation(new Quaternion().lookAt(p.velocity.normalize(),Vector3f.UNIT_Y));arrows.attachChild(arrow);}
    }
}
