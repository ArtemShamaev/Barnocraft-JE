package com.barnocraft;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.*;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.*;
import com.jme3.scene.shape.Sphere;

/** Camera-centred procedural sky, sharing its sun and horizon with terrain. */
final class Atmosphere {
    final Geometry sky = new Geometry("Atmosphere",new Sphere(24,48,200));
    final Vector3f sun = new Vector3f();
    final ColorRGBA tint = new ColorRGBA(), horizon = new ColorRGBA();
    private final Material material;
    private float elapsed;

    Atmosphere(AssetManager assets) {
        material=new Material(assets,"MatDefs/Atmosphere.j3md");
        material.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        material.getAdditionalRenderState().setDepthWrite(false);
        sky.setMaterial(material);
        sky.setQueueBucket(RenderQueue.Bucket.Sky);
        sky.setCullHint(Spatial.CullHint.Never);
    }
    void update(Camera camera,float time,float dt) {
        elapsed+=dt;
        float angle=(time-.25f)*FastMath.TWO_PI;
        sun.set(FastMath.cos(angle),FastMath.sin(angle),.22f).normalizeLocal();
        float day=FastMath.clamp((sun.y+.08f)/.78f,0,1);
        float dusk=(1-FastMath.clamp(Math.abs(sun.y)/.38f,0,1));
        tint.set(1,.80f+.20f*day,.64f+.36f*day,1);
        horizon.set(.035f+.55f*day+.38f*dusk,.055f+.67f*day+.15f*dusk,.12f+.70f*day,1);
        material.setVector3("SunDirection",sun);
        material.setFloat("Daylight",day);
        material.setFloat("Time",elapsed);
        material.setColor("Horizon",horizon);
        sky.setLocalTranslation(camera.getLocation());
    }
}
