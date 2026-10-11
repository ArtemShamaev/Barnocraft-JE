package com.barnocraft;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.*;
import com.jme3.scene.*;
import com.jme3.scene.shape.Box;
import java.util.*;

final class HellMobView {
    final Node node=new Node("Hell forest creatures");
    private final AssetManager assets;
    private final Map<String,Node> models=new HashMap<>();
    HellMobView(AssetManager assets){this.assets=assets;}
    void update(World world) {
        Set<String> alive=new HashSet<>();
        for(HellMob mob:world.hellMobs()) {
            alive.add(mob.id());Node model=models.computeIfAbsent(mob.id(),id->{Node created=create(mob.kind());node.attachChild(created);return created;});
            model.setLocalTranslation(mob.x(),mob.y(),mob.z());model.setLocalRotation(new Quaternion().fromAngles(0,mob.heading(),0));
            float stride=FastMath.sin((mob.x()+mob.z())*6)*.55f;
            for(String name:List.of("Left leg","Right leg","Rear left","Rear right")) {
                Spatial leg=model.getChild(name);if(leg!=null)leg.setLocalRotation(new Quaternion().fromAngles(name.contains("left") || name.startsWith("Left")?stride:-stride,0,0));
            }
            if(mob.kind()==HellMob.Kind.IMP) {
                float flap=FastMath.sin(world.dayTime()*600*16)*.45f;
                model.getChild("Left wing").setLocalRotation(new Quaternion().fromAngles(0,flap,.3f));
                model.getChild("Right wing").setLocalRotation(new Quaternion().fromAngles(0,-flap,-.3f));
            }
        }
        models.entrySet().removeIf(e->{if(!alive.contains(e.getKey())){e.getValue().removeFromParent();return true;}return false;});
    }
    private Node create(HellMob.Kind kind) {
        Node mob=new Node(kind.name());
        Material skin=material(kind==HellMob.Kind.IMP?"hell_stone.png":"hell_basalt.png",ColorRGBA.White);
        Material eyes=material("ember_crystal.png",new ColorRGBA(1,1,.6f,1));
        Material horn=material("hell_soil.png",new ColorRGBA(.85f,.7f,.65f,1));
        if(kind==HellMob.Kind.IMP) {
            part(mob,"Body",skin,0,.48f,0,.34f,.54f,.28f);part(mob,"Head",skin,0,.94f,.02f,.48f,.42f,.40f);
            for(float side:new float[]{-1,1}) {
                part(mob,"Horn",horn,side*.20f,1.23f,0,.10f,.30f,.12f);
                part(mob,"Eye",eyes,side*.13f,.99f,.23f,.10f,.085f,.025f);
            }
            part(mob,"Left leg",skin,-.13f,.16f,0,.12f,.32f,.14f);part(mob,"Right leg",skin,.13f,.16f,0,.12f,.32f,.14f);
            part(mob,"Left wing",skin,-.45f,.64f,-.04f,.6f,.30f,.05f);part(mob,"Right wing",skin,.45f,.64f,-.04f,.6f,.30f,.05f);
        } else {
            part(mob,"Body",skin,0,.61f,0,.90f,.64f,1.12f);part(mob,"Head",skin,0,.67f,.63f,.72f,.54f,.50f);
            part(mob,"Snout",horn,0,.57f,.92f,.40f,.22f,.20f);
            for(float side:new float[]{-1,1}) {
                part(mob,"Tusk",horn,side*.31f,.54f,.86f,.10f,.34f,.13f);
                part(mob,"Eye",eyes,side*.26f,.81f,.89f,.10f,.075f,.025f);
                part(mob,side<0?"Left leg":"Right leg",skin,side*.32f,.20f,.36f,.19f,.40f,.22f);
                part(mob,side<0?"Rear left":"Rear right",skin,side*.32f,.20f,-.36f,.19f,.40f,.22f);
            }
            part(mob,"Bristles",horn,0,.98f,-.12f,.14f,.15f,.82f);
        }
        return mob;
    }
    private Material material(String texture,ColorRGBA color) {
        Material material=new Material(assets,"Common/MatDefs/Misc/Unshaded.j3md");material.setColor("Color",color);material.setTexture("ColorMap",WorldView.texture(assets,texture));return material;
    }
    private static void part(Node node,String name,Material material,float x,float y,float z,float w,float h,float d) {
        Geometry part=new Geometry(name,new Box(w/2,h/2,d/2));part.setMaterial(material);part.setLocalTranslation(x,y,z);node.attachChild(part);
    }
}
