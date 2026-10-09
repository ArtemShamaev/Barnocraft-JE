#import "Common/ShaderLib/GLSLCompat.glsllib"
uniform vec3 m_SunDirection;
uniform float m_Daylight;
uniform float m_Time;
uniform vec4 m_Horizon;
varying vec3 direction;
float hash(vec2 p) { return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453); }
float noise(vec2 p) {
    vec2 i=floor(p), f=fract(p); f=f*f*(3.0-2.0*f);
    return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+vec2(1,1)),f.x),f.y);
}
void main() {
    vec3 d=normalize(direction);
    float elevation=max(d.y,0.0);
    vec3 zenith=mix(vec3(.008,.015,.045),vec3(.14,.40,.77),m_Daylight);
    vec3 color=mix(m_Horizon.rgb,zenith,pow(elevation,.45));
    float alignment=max(dot(d,m_SunDirection),0.0);
    float twilight=1.0-smoothstep(.0,.4,abs(m_SunDirection.y));
    color+=vec3(.65,.18,.035)*pow(alignment,8.0)*twilight*exp(-elevation*5.0);
    color+=vec3(1.0,.65,.28)*pow(alignment,180.0)*.35;
    float disk=smoothstep(.99935,.99965,alignment);
    color=mix(color,vec3(1.0,.91,.66),disk);
    float moon=smoothstep(.99955,.99975,dot(d,-m_SunDirection));
    color=mix(color,vec3(.72,.80,.94),moon*(1.0-m_Daylight));
    vec2 stars=floor(d.xz/max(.15,elevation)*220.0);
    color+=vec3(pow(hash(stars),180.0))*smoothstep(.05,.3,elevation)*(1.0-smoothstep(0.0,.25,m_Daylight))*.55;
    if(d.y>.015) {
        vec2 p=d.xz/(d.y+.12)*3.5+vec2(m_Time*.009,m_Time*.003);
        float n=noise(p)*.58+noise(p*2.1)*.28+noise(p*4.3)*.14;
        float cloud=smoothstep(.49,.69,n)*smoothstep(.015,.16,d.y);
        vec3 cloudColor=mix(vec3(.08,.10,.17),vec3(.94,.96,1.0),m_Daylight);
        cloudColor=mix(cloudColor,vec3(.94,.49,.27),twilight*.55);
        cloudColor*=.78+.22*smoothstep(.5,.8,n);
        color=mix(color,cloudColor,cloud*.92);
    }
    gl_FragColor=vec4(color,1.0);
}
