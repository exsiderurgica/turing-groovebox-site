/* SPDX-License-Identifier: MIT */
#include "../src/ttgfx.h"
#include <assert.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#define CHUNK 64
static ttg_fx fx; /* never allocate in a small MCU stack */
static int16_t in[CHUNK*2],out[CHUNK*2];
static void all_off(ttg_params *p){memset(p,0,sizeof *p);}
static void reset_off(void){ttg_init(&fx);ttg_params p;all_off(&p);ttg_set_params(&fx,&p);}
static void passthrough(void){
    reset_off();
    for(int i=0;i<CHUNK*2;i++)in[i]=(int16_t)((i*977)%62000-31000);
    ttg_process(&fx,in,out,CHUNK);
    for(int i=0;i<CHUNK*2;i++)assert(labs((long)in[i]-out[i])<=1);
}
static void silence(void){
    ttg_init(&fx);memset(in,0,sizeof in);
    for(int b=0;b<150;b++)ttg_process(&fx,in,out,CHUNK);
    for(int i=0;i<CHUNK*2;i++)assert(out[i]==0);
}
static void bounded(void){
    ttg_init(&fx);ttg_params p=fx.p;
    p.d1.gain=1;p.f1.resonance=1;p.f2.resonance=1;p.delay.feedback=.92f;
    ttg_set_params(&fx,&p);
    for(int b=0;b<600;b++){
        for(int i=0;i<CHUNK*2;i++)in[i]=(i&1)?-32768:32767;
        ttg_process(&fx,in,out,CHUNK);
        for(int i=0;i<CHUNK*2;i++)assert(out[i]>=-32767&&out[i]<=32767);
    }
}
static void impulse_delay(void){
    reset_off();ttg_params p=fx.p;p.delay.on=1;p.delay.time_ms=80;
    p.delay.feedback=0;p.delay.wet=1;p.delay.color=1;
    ttg_set_params(&fx,&p);
    long long energy=0;int sample=0;
    for(int b=0;b<100;b++){
        memset(in,0,sizeof in);if(b==0)in[0]=30000;
        ttg_process(&fx,in,out,CHUNK);
        for(int i=0;i<CHUNK;i++,sample++)if(sample>2000)energy+=llabs((long long)out[2*i]);
    }
    assert(energy>1000);
}
static void impulse_reverb(void){
    reset_off();ttg_params p=fx.p;p.reverb.on=1;p.reverb.wet=1;
    ttg_set_params(&fx,&p);
    long long energy=0;
    for(int b=0;b<180;b++){
        memset(in,0,sizeof in);if(b==0)in[0]=30000;
        ttg_process(&fx,in,out,CHUNK);
        if(b>20)for(int i=0;i<CHUNK*2;i++)energy+=llabs((long long)out[i]);
    }
    assert(energy>0);
}
int main(void){
    assert(sizeof(ttg_fx)<110000);
    passthrough();silence();bounded();impulse_delay();impulse_reverb();
    printf("TTG FX host tests: PASS (state %zu bytes; 44.1 kHz, stereo)\n",sizeof(ttg_fx));
    return 0;
}
