/* EXS TTG FX original standalone application glue — MIT */
#include "ttg_app.h"
#include <string.h>
static float norm(uint8_t v){return (float)v/100.0f;}
static void sync_params(ttg_app *a){
  const ttg_ui *u=&a->ui;
  ttg_mutable_params *p=&a->params;
  memset(p,0,sizeof *p);p->use_extended=1;
  for(int i=0;i<TTG_UI_BLOCKS;i++)p->on[i]=u->enabled[i];
  for(int i=0;i<2;i++){
    int drive=i?3:0,filter=i?2:1;
    p->d_gain[i]=norm(u->values[drive][0]);
    p->d_level[i]=norm(u->values[drive][1])*1.35f;
    p->d_tone[i]=norm(u->values[drive][2]);
    p->d_mode[i]=u->values[drive][3]>=50;
    p->f_cutoff[i]=norm(u->values[filter][0]);
    p->f_resonance[i]=norm(u->values[filter][1]);
    p->f_level[i]=norm(u->values[filter][2])*1.3f;
    p->f_hp[i]=u->values[filter][3]>=50;
  }
  p->delay_ms=30.0f+9.8f*(float)u->values[4][0];
  p->feedback=norm(u->values[4][1])*.9f;
  p->tone=norm(u->values[4][2]);
  p->delay_wet=norm(u->values[4][3]);
  p->pingpong=u->values[4][4]>=50;
  p->decay=norm(u->values[5][0]);
  p->diffusion=norm(u->values[5][1]);
  p->reverb_tone=norm(u->values[5][2]);
  p->reverb_wet=norm(u->values[5][3]);
  p->gain=p->d_gain[0];p->level=p->d_level[0];
  p->cutoff=p->f_cutoff[0];p->resonance=p->f_resonance[0];p->wet=p->delay_wet;
  ttg_mutable_set_params(p);
  a->controls_changed++;
}
void ttg_app_init(ttg_app *a){
  memset(a,0,sizeof(*a));ttg_ui_init(&a->ui);
  a->ui.values[0][0]=20;a->ui.values[0][1]=70;
  a->ui.values[1][0]=70;a->ui.values[1][1]=20;a->ui.values[1][2]=85;a->ui.values[1][3]=0;
  a->ui.values[2][0]=10;a->ui.values[2][1]=20;a->ui.values[2][2]=90;a->ui.values[2][3]=100;
  a->ui.values[3][0]=15;a->ui.values[3][1]=80;
  a->ui.values[4][0]=25;a->ui.values[4][1]=45;a->ui.values[4][2]=65;
  a->ui.values[4][3]=32;a->ui.values[4][4]=0;
  a->ui.values[5][0]=60;a->ui.values[5][1]=63;
  a->ui.values[5][2]=72;a->ui.values[5][3]=28;
  ttg_mutable_init();sync_params(a);
}
void ttg_app_event(ttg_app *a,ttg_event ev,uint32_t now){
  switch(ev){
    case TTG_EV_LEFT:ttg_ui_side(&a->ui,-1);return;
    case TTG_EV_RIGHT:ttg_ui_side(&a->ui,+1);return;
    case TTG_EV_CW:ttg_ui_turn(&a->ui,+1);break;
    case TTG_EV_CCW:ttg_ui_turn(&a->ui,-1);break;
    case TTG_EV_PUSH_DOWN:a->push_at=now;a->push_down=1;a->push_long_fired=0;return;
    case TTG_EV_PUSH_UP:
      if(a->push_down&&!a->push_long_fired)ttg_ui_press(&a->ui,0);
      a->push_down=0;return;
  }
  if(a->ui.editing)sync_params(a);
}
void ttg_app_tick(ttg_app *a,uint32_t now){
  if(a->push_down&&!a->push_long_fired&&(uint32_t)(now-a->push_at)>=800u){
    ttg_ui_press(&a->ui,1);a->push_long_fired=1;sync_params(a);
  }
}
void ttg_app_draw(ttg_app *a,uint16_t fb[TTG_UI_H][TTG_UI_W]){ttg_ui_draw(&a->ui,fb);}
void ttg_app_process(ttg_app *a,const int16_t *in,int16_t *out,size_t frames){
  (void)a;ttg_mutable_process(in,out,frames);
}
