package com.barnocraft;

record Zombie(float x,float y,float z,int health,float attackCooldown) {
    Zombie hurt() { return new Zombie(x,y,z,health-1,attackCooldown); }
    Zombie move(float x,float y,float z,float cooldown) { return new Zombie(x,y,z,health,cooldown); }
}