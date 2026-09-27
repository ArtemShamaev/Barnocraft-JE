#import "Common/ShaderLib/GLSLCompat.glsllib"
uniform sampler2D m_DiffuseMap;
uniform vec4 m_Diffuse;
uniform vec4 m_FogColor;
uniform vec2 m_LinearFog;
uniform float m_AlphaDiscardThreshold;
uniform float m_Daylight;
uniform float m_Underwater;
uniform vec3 m_TorchPos;
uniform bool m_HeldTorch;
varying vec2 uv;
varying vec2 light;
varying float shade;
varying float distanceToEye;
varying vec3 worldPosition;
void main() {
    vec4 tex=texture2D(m_DiffuseMap,uv)*m_Diffuse;
    if(tex.a<m_AlphaDiscardThreshold) discard;
    float sky=pow(light.x,1.5)*(0.045+0.955*m_Daylight);
    float torch=pow(light.y,1.5);
    if (m_HeldTorch) {
        float distanceToTorch=distance(worldPosition,m_TorchPos);
        float carriedTorch=max(0.0,1.0-distanceToTorch/8.0);
        torch=max(torch,carriedTorch*carriedTorch);
    }
    vec3 illumination=max(vec3(sky),vec3(1.0,0.72,0.42)*torch)+vec3(0.012);
    vec3 color=tex.rgb*illumination*shade;
    float fog=clamp((distanceToEye-m_LinearFog.x)/(m_LinearFog.y-m_LinearFog.x),0.0,1.0);
    vec3 fogColor=m_FogColor.rgb*max(light.x,light.y);
    if(m_Underwater>0.5) {
        fog=clamp(distanceToEye/12.0,0.0,0.94);
        fogColor=vec3(0.025,0.12,0.23)*max(0.08,max(sky,torch));
        color*=vec3(0.6,0.85,1.0);
    }
    gl_FragColor=vec4(mix(color,fogColor,fog),tex.a);
}
