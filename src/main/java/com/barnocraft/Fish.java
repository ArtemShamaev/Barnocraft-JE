package com.barnocraft;

/** Small river fish; fish are recreated from the river when a world loads. */
record Fish(float x,float y,float z,float dx,float dz) {
    Fish move(float x,float y,float z,float dx,float dz) { return new Fish(x,y,z,dx,dz); }
}
