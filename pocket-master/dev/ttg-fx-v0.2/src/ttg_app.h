/* EXS TTG FX standalone application controller — MIT */
#ifndef TTG_APP_H
#define TTG_APP_H
#include <stdint.h>
#include <stddef.h>
#include "ttg_ui.h"
#include "mutable_chain.h"
#ifdef __cplusplus
extern "C" {
#endif
typedef enum { TTG_EV_LEFT, TTG_EV_RIGHT, TTG_EV_CW, TTG_EV_CCW,
               TTG_EV_PUSH_DOWN, TTG_EV_PUSH_UP } ttg_event;
typedef struct {
  ttg_ui ui;
  ttg_mutable_params params;
  uint32_t push_at;
  uint8_t push_down, push_long_fired;
  uint32_t controls_changed;
} ttg_app;
void ttg_app_init(ttg_app *app);
void ttg_app_event(ttg_app *app,ttg_event ev,uint32_t now_ms);
void ttg_app_tick(ttg_app *app,uint32_t now_ms);
void ttg_app_draw(ttg_app *app,uint16_t fb[TTG_UI_H][TTG_UI_W]);
void ttg_app_process(ttg_app *app,const int16_t *in,int16_t *out,size_t frames);
#ifdef __cplusplus
}
#endif
#endif
