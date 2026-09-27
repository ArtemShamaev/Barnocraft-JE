#import "Common/ShaderLib/GLSLCompat.glsllib"
uniform mat4 g_WorldViewProjectionMatrix;
uniform mat4 g_WorldViewMatrix;
attribute vec3 inPosition;
attribute vec3 inNormal;
attribute vec2 inTexCoord;
attribute vec2 inTexCoord2;
varying vec2 uv;
varying vec2 light;
varying float shade;
varying float distanceToEye;
varying vec3 worldPosition;
void main() {
    gl_Position=g_WorldViewProjectionMatrix*vec4(inPosition,1.0);
    worldPosition=inPosition;
    distanceToEye=length((g_WorldViewMatrix*vec4(inPosition,1.0)).xyz);
    uv=inTexCoord; light=inTexCoord2;
    shade=0.72+0.28*max(0.0,dot(inNormal,normalize(vec3(0.4,0.8,0.3))));
}
