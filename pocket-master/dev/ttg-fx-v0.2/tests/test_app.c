/* MIT — independent UI→DSP signal path smoke test with mock target. */
#include "../src/ttg_app.h"
#include <stdio.h>
#include <assert.h>
#include <string.h>
static ttg_mutable_params seen;
static unsigned calls=0,audio_calls=0;
void ttg_mutable_init(void){}
void ttg_mutable_set_params(const ttg_mutable_params *p){seen=*p;calls++;}
void ttg_mutable_process(const int16_t *in,int16_t *out,size_t n){
 audio_calls++;memcpy(out,in,2*n*sizeof *in);
}
size_t ttg_mutable_static_bytes(void){return 0;}
int main(void){
 ttg_app a;ttg_app_init(&a);
 assert(calls==1&&a.controls_changed==1&&seen.use_extended);
 assert(seen.f_hp[1]==1&&seen.f_hp[0]==0);
 assert(seen.f_cutoff[0]!=seen.f_cutoff[1]);
 for(int i=0;i<4;i++)ttg_app_event(&a,TTG_EV_RIGHT,10);
 assert(a.ui.selected==4);
 for(int i=0;i<4;i++)ttg_app_event(&a,TTG_EV_CW,10);
 assert(a.ui.parameter==4);
 ttg_app_event(&a,TTG_EV_PUSH_DOWN,100);
 ttg_app_event(&a,TTG_EV_PUSH_UP,150);
 assert(a.ui.editing);
 for(int i=0;i<55;i++)ttg_app_event(&a,TTG_EV_CW,200);
 assert(seen.pingpong==1&&seen.use_extended==1);
 ttg_app_event(&a,TTG_EV_PUSH_DOWN,500);
 ttg_app_tick(&a,1299);assert(a.ui.enabled[4]==1);
 ttg_app_tick(&a,1300);assert(a.ui.enabled[4]==0&&seen.on[4]==0);
 ttg_app_event(&a,TTG_EV_PUSH_UP,1400);assert(!a.ui.editing);
 static uint16_t fb[TTG_UI_H][TTG_UI_W];ttg_app_draw(&a,fb);
 assert(fb[0][0]!=fb[10][10]);
 int16_t in[64],out[64];for(int i=0;i<64;i++)in[i]=(int16_t)(i*200-3200);
 ttg_app_process(&a,in,out,32);assert(audio_calls==1);
 assert(!memcmp(in,out,sizeof in));
 puts("PASS standalone TTG FX app: controls→DSP, pingpong, bypass, RGB565");
 return 0;
}
