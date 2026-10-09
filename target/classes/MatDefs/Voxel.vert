#import "Common/ShaderLib/GLSLCompat.glsllib"
uniform mat4 g_WorldViewProjectionMatrix;
uniform mat4 g_WorldViewMatrix;
uniform vec3 m_SunDirection;
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
    shade=0.68+0.32*max(0.0,dot(inNormal,normalize(m_SunDirection)));
}
