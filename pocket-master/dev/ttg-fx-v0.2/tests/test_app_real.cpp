/* MIT: end-to-end standalone EXS app with real original Mutable DSP. */
#include "../src/ttg_app.h"
#include <cassert>
#include <cstdint>
#include <cstdio>
static ttg_app app;
static int16_t in[64],out[64];
static uint16_t rgb[TTG_UI_H][TTG_UI_W];
static int64_t run_audio(void){
  int64_t sum=0;
  for(unsigned k=0;k<300;k++){
    for(int i=0;i<64;i++){
      int x=(int)((k*64+i)*277u%32768);
      in[i]=(int16_t)(x-16384);
    }
    ttg_app_process(&app,in,out,32);
    for(int i=0;i<64;i++)sum+=out[i]<0?-(int64_t)out[i]:(int64_t)out[i];
  }
  return sum;
}
int main(){
  ttg_app_init(&app);
  assert(ttg_mutable_static_bytes()<120000);
  assert(app.params.use_extended);
  assert(app.params.f_cutoff[0]!=app.params.f_cutoff[1]);
  int64_t original=run_audio();assert(original>10000);
  ttg_app_event(&app,TTG_EV_RIGHT,10);
  ttg_app_event(&app,TTG_EV_RIGHT,20);
  assert(app.ui.selected==2);
  float first=app.params.f_cutoff[0],second=app.params.f_cutoff[1];
  ttg_app_event(&app,TTG_EV_PUSH_DOWN,30);
  ttg_app_event(&app,TTG_EV_PUSH_UP,80);
  for(int i=0;i<30;i++)ttg_app_event(&app,TTG_EV_CW,100);
  assert(app.params.f_cutoff[1]>second&&app.params.f_cutoff[0]==first);
  int64_t changed=run_audio();assert(changed>10000);
  ttg_app_event(&app,TTG_EV_PUSH_DOWN,200);
  ttg_app_tick(&app,1001);
  assert(!app.params.on[2]&&app.params.on[1]);
  ttg_app_event(&app,TTG_EV_PUSH_UP,1100);
  ttg_app_draw(&app,rgb);
  unsigned different=0;
  for(int y=0;y<TTG_UI_H;y++)for(int x=0;x<TTG_UI_W;x++)
    if(rgb[y][x]!=rgb[0][0])different++;
  assert(different>1000);
  std::printf("PASS real Mutable TTG app UI→audio, filter independence, bypass; sums %lld/%lld RAM %zu\n",
    (long long)original,(long long)changed,ttg_mutable_static_bytes());
}
