package com.barnocraft;

record Zombie(float x,float y,float z,int health,float attackCooldown,float velocityY,float heading,String id) {
    Zombie(float x,float y,float z,int health,float cooldown) { this(x,y,z,health,cooldown,0,0,"zombie:"+java.util.UUID.randomUUID()); }
    Zombie hurt() { return hurt(1); }
    Zombie hurt(int damage) { return new Zombie(x,y,z,health-damage,attackCooldown,velocityY,heading,id); }
    Zombie move(float x,float y,float z,float cooldown) { return new Zombie(x,y,z,health,cooldown,velocityY,heading,id); }
}
