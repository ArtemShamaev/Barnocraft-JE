package com.barnocraft;

import com.jme3.math.*;
import java.io.*;

record NetworkPlayer(int id,int skin,float x,float y,float z,float yaw,float pitch,boolean moving,boolean airborne,boolean swinging,Dimension dimension) {
    NetworkPlayer(int id,int skin,float x,float y,float z,float yaw,float pitch,boolean moving,boolean airborne,boolean swinging) {
        this(id,skin,x,y,z,yaw,pitch,moving,airborne,swinging,Dimension.SURFACE);
    }
    String name(){return skin==0?"Саня":"Ваня";}
    Vector3f position(){return new Vector3f(x,y,z);}
    Vector3f direction(){return new Vector3f(-FastMath.sin(yaw)*FastMath.cos(pitch),FastMath.sin(pitch),-FastMath.cos(yaw)*FastMath.cos(pitch));}
    void write(DataOutputStream out) throws IOException {
        out.writeInt(id);out.writeByte(skin);out.writeFloat(x);out.writeFloat(y);out.writeFloat(z);
        out.writeFloat(yaw);out.writeFloat(pitch);out.writeBoolean(moving);out.writeBoolean(airborne);out.writeBoolean(swinging);out.writeUTF(dimension.name());
    }
    static NetworkPlayer read(DataInputStream in,int assignedId) throws IOException {
        int id=in.readInt(),skin=in.readUnsignedByte();float x=in.readFloat(),y=in.readFloat(),z=in.readFloat(),yaw=in.readFloat(),pitch=in.readFloat();
        boolean moving=in.readBoolean(),airborne=in.readBoolean(),swinging=in.readBoolean();
        Dimension dimension;try{dimension=Dimension.valueOf(in.readUTF());}catch(IllegalArgumentException e){throw new IOException("Неизвестное измерение",e);}
        if(skin>1 || !Float.isFinite(x+y+z+yaw+pitch) || x<0 || x>=World.WIDTH || z<0 || z>=World.DEPTH
                || y < -100 || y>World.HEIGHT+100 || Math.abs(yaw)>1e7 || Math.abs(pitch)>FastMath.HALF_PI)throw new IOException("Некорректная позиция игрока");
        return new NetworkPlayer(assignedId<0?id:assignedId,skin,x,y,z,yaw,pitch,moving,airborne,swinging,dimension);
    }
}
