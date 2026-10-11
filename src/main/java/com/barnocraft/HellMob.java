package com.barnocraft;

import com.jme3.math.Vector3f;
import java.util.*;

/** A hovering, hostile forest imp and a heavy ash boar that charges nearby players. */
public record HellMob(String id,Kind kind,float x,float y,float z,int health,float heading,float cooldown) {
    public enum Kind { IMP, BOAR }
    int maxHealth(){return kind==Kind.IMP?6:12;}
    static HellMob spawn(Kind kind,float x,float y,float z){return new HellMob(UUID.randomUUID().toString(),kind,x,y,z,kind==Kind.IMP?6:12,0,0);}
    HellMob tick(World world,float dt,Player target) {
        float dx=target.position.x-x,dz=target.position.z-z,distance=(float)Math.sqrt(dx*dx+dz*dz);
        float heading=distance>.01f?(float)Math.atan2(dx,dz):this.heading;
        float cooldown=Math.max(0,this.cooldown-dt),nx=x,nz=z,ny=y;
        float notice=kind==Kind.IMP?22:12,speed=kind==Kind.IMP?2.1f:3.2f;
        if(distance<notice && distance>1.1f) {
            float mx=dx/distance*speed*dt,mz=dz/distance*speed*dt;
            if(fits(world,x+mx,y,z))nx+=mx;
            if(fits(world,nx,y,z+mz))nz+=mz;
            if(nx==x && nz==z && fits(world,x+mx,y+1,z+mz)){nx+=mx;nz+=mz;ny+=Math.min(1,dt*6);}
        }
        if(kind==Kind.IMP) {
            float desired=Math.min(World.HEIGHT-3,Math.max(world.groundHeight((int)nx,(int)nz)+2.1f,target.position.y+1));
            float dy=Math.max(-dt,Math.min(dt,desired-ny));if(fits(world,nx,ny+dy,nz))ny+=dy;
        } else if(fits(world,nx,ny-dt*6,nz))ny-=dt*6;
        if(distance<(kind==Kind.IMP?1.7f:1.5f) && Math.abs(target.position.y-ny)<2.5f && cooldown==0) {
            if(world.raycast(new Vector3f(x,y+.6f,z),target.position.add(0,.6f,0).subtract(x,y+.6f,z).normalizeLocal(),distance)==null)target.damage(kind==Kind.IMP?2:3);
            cooldown=1.4f;
        }
        return new HellMob(id,kind,nx,ny,nz,health,heading,cooldown);
    }
    private boolean fits(World world,float x,float y,float z) {
        if(x<1 || z<1 || x>=world.width()-1 || z>=world.depth()-1 || y<1 || y>World.HEIGHT-3)return false;
        float radius=kind==Kind.IMP?.22f:.48f;
        for(int bx=(int)Math.floor(x-radius);bx<=Math.floor(x+radius);bx++)for(int bz=(int)Math.floor(z-radius);bz<=Math.floor(z+radius);bz++)
            for(int by=(int)Math.floor(y+.02f);by<=Math.floor(y+.95f);by++)if(world.get(bx,by,bz).solid() || world.get(bx,by,bz)==Block.HELL_LAVA)return false;
        return true;
    }
}
