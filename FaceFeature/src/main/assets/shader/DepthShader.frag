precision mediump float;
varying vec2 v_TexCoord;
uniform sampler2D sTexture;
//uniform samplerExternalOES sTexture;


void main() {
    vec2 tx =  texture2D(sTexture, v_TexCoord).ra;
    float ret = tx.y * 256.0 + tx.x;
    gl_FragColor =vec4(vec3(ret/5.0), 1.0);
}