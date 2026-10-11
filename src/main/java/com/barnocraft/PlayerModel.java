package com.barnocraft;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.*;
import com.jme3.scene.*;
import com.jme3.scene.shape.Box;
import com.jme3.scene.control.BillboardControl;

/** Two original block characters with shoulder/hip pivots and procedural animation. */
final class PlayerModel {
    final Node node=new Node("Player");
    final int skin;
    final Node head=new Node("Head"),leftArm=new Node("Left arm"),rightArm=new Node("Right arm"),leftLeg=new Node("Left leg"),rightLeg=new Node("Right leg");
    private final AssetManager assets;
    private float clock,stride;
    private boolean initialized;
    PlayerModel(AssetManager assets,int skin,boolean nameTag) {
        this.assets=assets;this.skin=skin;
        ColorRGBA flesh=color(skin==0?0xe5b18c:0xd29a73),shirt=color(skin==0?0x388bab:0xb34b3d),pants=color(skin==0?0x273e65:0x354733),hair=color(skin==0?0x6b422c:0x322a26);
        part(node,"Shirt",.48f,.61f,.28f,0,1.055f,0,shirt);
        part(node,"Collar",.18f,.08f,.292f,0,1.335f,0,color(0xe9dbb8));
        part(node,"Belt",.49f,.07f,.29f,0,.77f,0,color(0x433b32));
        pivot(head,0,1.44f,0);part(head,"Face",.45f,.45f,.43f,0,.135f,0,flesh);
        part(head,"Hair",.47f,.14f,.45f,0,.32f,0,hair);
        part(head,"Back hair",.46f,.30f,.06f,0,.17f,.205f,hair);
        part(head,"Fringe",skin==0?.21f:.45f,.08f,.045f,skin==0?-.12f:0,.245f,-.22f,hair);
        for(float x:new float[]{-.108f,.108f}) {
            part(head,"Eye white",.082f,.065f,.014f,x,.14f,-.222f,ColorRGBA.White);
            part(head,"Pupil",.035f,.052f,.018f,x,.137f,-.232f,color(skin==0?0x3a634f:0x3d5169));
            part(head,"Eyebrow",.095f,.025f,.02f,x,.20f,-.228f,hair);
        }
        part(head,"Nose",.07f,.075f,.055f,0,.07f,-.235f,flesh.mult(.91f));
        part(head,"Mouth",.10f,.022f,.015f,0,-.002f,-.222f,color(0x86594b));
        if(skin==1) {
            part(head,"Beard",.26f,.075f,.022f,0,-.04f,-.224f,hair);
            part(head,"Moustache",.16f,.025f,.02f,0,.021f,-.228f,hair);
            part(node,"Pocket",.12f,.14f,.018f,-.12f,1.16f,-.15f,color(0xd07b58));
        } else {
            part(head,"Freckle",.032f,.021f,.016f,-.16f,.064f,-.225f,color(0xae7050));
            part(head,"Freckle",.032f,.021f,.016f,.16f,.064f,-.225f,color(0xae7050));
            part(node,"Shirt stripe",.49f,.10f,.292f,0,1.08f,0,color(0xe2d7b5));
        }
        pivot(leftArm,-.35f,1.32f,0);pivot(rightArm,.35f,1.32f,0);
        for(Node arm:new Node[]{leftArm,rightArm}) {
            part(arm,"Sleeve",.20f,.30f,.25f,0,-.13f,0,shirt);
            part(arm,"Hand",.18f,.32f,.22f,0,-.43f,0,flesh);
        }
        pivot(leftLeg,-.125f,.76f,0);pivot(rightLeg,.125f,.76f,0);
        for(Node leg:new Node[]{leftLeg,rightLeg}) {
            part(leg,"Trousers",.22f,.61f,.25f,0,-.29f,0,pants);
            part(leg,"Boot",.23f,.16f,.31f,0,-.68f,-.025f,color(0x302f2d));
        }
        if(nameTag) {
            Node tag=new Node("Name tag");tag.setLocalTranslation(0,2.02f,0);
            Geometry text=Hud.label(assets,skin==0?"Саня":"Ваня",24);
            text.setLocalScale(.007f);text.setLocalTranslation(-.20f,0,0);tag.attachChild(text);
            tag.addControl(new BillboardControl());node.attachChild(tag);
        }
    }
    private void pivot(Node pivot,float x,float y,float z){pivot.setLocalTranslation(x,y,z);node.attachChild(pivot);}
    private void part(Node parent,String name,float w,float h,float d,float x,float y,float z,ColorRGBA color) {
        Geometry box=new Geometry(name,new Box(w/2,h/2,d/2));
        Material material=new Material(assets,"Common/MatDefs/Misc/Unshaded.j3md");material.setColor("Color",color);
        box.setMaterial(material);box.setLocalTranslation(x,y,z);parent.attachChild(box);
    }
    void update(NetworkPlayer target,float dt) {
        if(!initialized || node.getLocalTranslation().distanceSquared(target.position())>64) { node.setLocalTranslation(target.position());initialized=true; }
        else node.setLocalTranslation(node.getLocalTranslation().clone().interpolateLocal(target.position(),1-(float)Math.exp(-dt*16)));
        Quaternion rotation=new Quaternion().fromAngles(0,target.yaw(),0);
        Quaternion current=node.getLocalRotation().clone();current.slerp(rotation,1-(float)Math.exp(-dt*18));node.setLocalRotation(current);
        animate(dt,target.moving(),target.airborne(),target.swinging(),target.pitch());
    }
    void animate(float dt,boolean moving,boolean airborne,boolean swinging,float pitch) {
        clock+=dt;stride+=( (moving?1:0)-stride)*Math.min(1,dt*12);
        float step=FastMath.sin(clock*10)*.72f*stride;
        head.setLocalRotation(new Quaternion().fromAngles(pitch,0,0));
        leftLeg.setLocalRotation(new Quaternion().fromAngles(airborne?-.3f:step,0,0));
        rightLeg.setLocalRotation(new Quaternion().fromAngles(airborne?.3f:-step,0,0));
        leftArm.setLocalRotation(new Quaternion().fromAngles(-step,0,.035f+FastMath.sin(clock*2)*.02f));
        rightArm.setLocalRotation(new Quaternion().fromAngles(swinging?-1.0f-FastMath.sin(clock*20)*.65f:step,0,-.035f));
    }
    static ColorRGBA color(int rgb){return new ColorRGBA(((rgb>>16)&255)/255f,((rgb>>8)&255)/255f,(rgb&255)/255f,1);}
}
