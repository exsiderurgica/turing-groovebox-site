/* TTG FX DSP host prototype, original code. SPDX-License-Identifier: MIT */
#include "ttgfx.h"
#include <math.h>
#include <string.h>
#define PI 3.14159265358979323846f
static float lim(float x,float lo,float hi){return x<lo?lo:x>hi?hi:x;}
static float clip(float x){return lim(x,-1.0f,1.0f);}
static int16_t pcm(float x){float y=clip(x)*32767.0f;return (int16_t)(y>=0.0f?y+0.5f:y-0.5f);}
static float sat(float x){return x/(1.0f+fabsf(x));}
static float drive(float in,ttg_drive_params p){
    if(!p.on)return in;
    float gain=1.0f+lim(p.gain,0,1)*24.0f;
    /* Normalize so that gain increase changes grit more than loudness. */
    return sat(in*gain)*(1.0f+gain)*0.9f/gain*lim(p.level,0,2);
}
static void svf_coeff(ttg_svf *s,ttg_filter_params p){
    float fc=lim(p.cutoff_hz,20.0f,17000.0f);
    float q=0.707f+lim(p.resonance,0,1)*9.0f;
    float g=tanf(PI*fc/(float)TTG_RATE);
    s->k=1.0f/q;
    s->a1=1.0f/(1.0f+g*(g+s->k));
    s->a2=g*s->a1;
    s->a3=g*s->a2;
}
static float filter(ttg_fx *fx,int n,int c,float x){
    ttg_filter_params p=n?fx->p.f2:fx->p.f1;
    if(!p.on)return x;
    ttg_svf *s=&fx->f[n];
    float v3=x-s->ic2[c];
    float v1=s->a1*s->ic1[c]+s->a2*v3;
    float v2=s->ic2[c]+s->a2*s->ic1[c]+s->a3*v3;
    s->ic1[c]=2.0f*v1-s->ic1[c];
    s->ic2[c]=2.0f*v2-s->ic2[c];
    return sat((p.highpass?(x-s->k*v1-v2):v2)*lim(p.level,0,2)*1.4f)*1.5f;
}
static void delay_tick(ttg_fx *fx,const float input[2],float output[2]){
    unsigned p=fx->delay_pos;
    unsigned n=(unsigned)(lim(fx->p.delay.time_ms,2.0f,740.0f)*0.001f*(TTG_RATE/2));
    float feedback=lim(fx->p.delay.feedback,0.0f,0.92f);
    float damping=0.02f+lim(fx->p.delay.color,0.0f,1.0f)*0.55f;
    for(int c=0;c<2;c++){
        unsigned rd=(p+TTG_DELAY_LEN-n)%TTG_DELAY_LEN;
        float delayed=fx->delay_buf[c][rd]*(1.0f/32768.0f);
        fx->delay_lp[c]+=damping*(delayed-fx->delay_lp[c]);
        float w=fx->delay_lp[c];
        fx->delay_buf[c][p]=pcm(sat((input[c]+w*feedback)*1.2f)*1.7f);
        output[c]=w;
    }
    fx->delay_pos=(p+1u)%TTG_DELAY_LEN;
}
static void delay_process(ttg_fx *fx,float *l,float *r){
    float now[2]={*l,*r};
    if(fx->decim_phase==0){
        fx->decim_input[0]=now[0];fx->decim_input[1]=now[1];
        fx->decim_phase=1;
    }else{
        float half[2]={(now[0]+fx->decim_input[0])*0.5f,
                       (now[1]+fx->decim_input[1])*0.5f};
        delay_tick(fx,half,fx->delay_wet);
        fx->decim_phase=0;
    }
    if(fx->p.delay.on){
        float mix=lim(fx->p.delay.wet,0,1);
        *l=now[0]*(1-mix)+fx->delay_wet[0]*mix;
        *r=now[1]*(1-mix)+fx->delay_wet[1]*mix;
    }
}
static const unsigned lengths[TTG_COMB_COUNT]={1103,1217,1373,1559};
static void reverb_process(ttg_fx *fx,float *l,float *r){
    float in[2]={*l,*r};
    float result[2]={0,0};
    float fb=0.35f+0.55f*lim(fx->p.reverb.decay,0,1);
    for(int k=0;k<TTG_COMB_COUNT;k++){
        unsigned at=fx->comb_pos[k];
        for(int c=0;c<2;c++){
            float old=fx->comb[c][k][at]*(1.0f/32768.0f);
            fx->reverb_lp[c]+=0.1f*(old-fx->reverb_lp[c]);
            float into=in[c]*0.20f+fx->reverb_lp[c]*fb;
            fx->comb[c][k][at]=pcm(clip(into));
            result[c]+=old*0.25f;
        }
        fx->comb_pos[k]=(at+1u)%lengths[k];
    }
    if(fx->p.reverb.on){
        float wet=lim(fx->p.reverb.wet,0,1);
        *l=(1-wet)*in[0]+wet*result[0];
        *r=(1-wet)*in[1]+wet*result[1];
    }
}
void ttg_init(ttg_fx *fx){
    memset(fx,0,sizeof *fx);
    ttg_params p={0};
    p.d1=(ttg_drive_params){1,.32f,.9f};
    p.d2=(ttg_drive_params){0,.24f,.9f};
    p.f1=(ttg_filter_params){1,3600,.18f,1,0};
    p.f2=(ttg_filter_params){1,60,.1f,1,1};
    p.delay=(ttg_delay_params){1,310,.35f,.52f,.22f};
    p.reverb=(ttg_reverb_params){1,.55f,.15f};
    ttg_set_params(fx,&p);
}
void ttg_set_params(ttg_fx *fx,const ttg_params *params){
    fx->p=*params;
    svf_coeff(&fx->f[0],fx->p.f1);
    svf_coeff(&fx->f[1],fx->p.f2);
}
void ttg_process(ttg_fx *fx,const int16_t *input,int16_t *output,size_t frames){
    for(size_t i=0;i<frames;i++){
        float l=input[2*i]*(1.0f/32768.0f),r=input[2*i+1]*(1.0f/32768.0f);
        l=drive(l,fx->p.d1);r=drive(r,fx->p.d1);
        l=filter(fx,0,0,l);r=filter(fx,0,1,r);
        l=filter(fx,1,0,l);r=filter(fx,1,1,r);
        l=drive(l,fx->p.d2);r=drive(r,fx->p.d2);
        delay_process(fx,&l,&r);
        reverb_process(fx,&l,&r);
        output[2*i]=pcm(l);output[2*i+1]=pcm(r);
    }
}
