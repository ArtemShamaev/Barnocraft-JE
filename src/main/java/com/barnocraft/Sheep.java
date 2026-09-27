package com.barnocraft;

record Sheep(float x,float y,float z,int health,float directionX,float directionZ,float fleeTime) {
    Sheep(float x,float y,float z,int health) { this(x,y,z,health,1,0,0); }
    Sheep hurt(float awayX,float awayZ) {
        float length=(float)Math.sqrt(awayX*awayX+awayZ*awayZ);
        if(length<.001f) { awayX=-directionX; awayZ=-directionZ; length=(float)Math.sqrt(awayX*awayX+awayZ*awayZ); }
        return new Sheep(x,y,z,health-1,awayX/length,awayZ/length,4f);
    }
    Sheep move(float x,float y,float z,float directionX,float directionZ,float fleeTime) {
        return new Sheep(x,y,z,health,directionX,directionZ,fleeTime);
    }
}
