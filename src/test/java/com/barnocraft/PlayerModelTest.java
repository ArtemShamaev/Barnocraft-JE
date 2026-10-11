package com.barnocraft;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.scene.Node;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerModelTest {
    @Test void remoteMovementAndLookRefreshSceneTransforms() {
        PlayerModel model=new PlayerModel(new DesktopAssetManager(true),1,false);
        Node scene=new Node();scene.attachChild(model.node);
        model.update(new NetworkPlayer(1,1,40,90,40,0,0,false,false,false),.1f);scene.updateGeometricState();
        assertEquals(40,model.node.getWorldTranslation().x,.001f);
        model.update(new NetworkPlayer(1,1,42,90,40,1,.2f,true,false,false),.1f);scene.updateGeometricState();
        assertTrue(model.node.getWorldTranslation().x>41,"Moving the local vector without setLocalTranslation leaves world transforms stale");
        assertTrue(Math.abs(model.node.getWorldRotation().getY())>.1f);
        assertTrue(model.head.getLocalRotation().getX()>0);
        assertTrue(model.leftLeg.getLocalRotation().getX()*model.rightLeg.getLocalRotation().getX()<0);
    }
    @Test void modelsHaveDifferentFacesAndJumpAndSwingPoses() {
        var assets=new DesktopAssetManager(true);
        PlayerModel sanya=new PlayerModel(assets,0,false),vanya=new PlayerModel(assets,1,false);
        assertNull(sanya.head.getChild("Beard"));assertNotNull(vanya.head.getChild("Beard"));
        sanya.animate(.1f,false,true,true,0);
        assertTrue(sanya.leftLeg.getLocalRotation().getX()<0);
        assertTrue(sanya.rightLeg.getLocalRotation().getX()>0);
        assertTrue(sanya.rightArm.getLocalRotation().getX()<-.3f);
    }
}
