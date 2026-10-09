#import "Common/ShaderLib/GLSLCompat.glsllib"
uniform mat4 g_WorldViewProjectionMatrix;
attribute vec3 inPosition;
varying vec3 direction;
void main() {
    direction=inPosition;
    vec4 p=g_WorldViewProjectionMatrix*vec4(inPosition,1.0);
    gl_Position=p.xyww;
}
