/* TTG FX independent RGB565 UI — MIT */
#ifndef TTG_UI_INCLUDED_H
#define TTG_UI_INCLUDED_H
#include <stdint.h>
#define TTG_UI_W 160
#define TTG_UI_H 128
#define TTG_UI_BLOCKS 6
#define TTG_UI_PARAMETERS 5
typedef struct {
 uint8_t selected,parameter,editing,enabled[TTG_UI_BLOCKS];
 uint8_t values[TTG_UI_BLOCKS][TTG_UI_PARAMETERS];
 uint8_t cpu_meter;
} ttg_ui;
void ttg_ui_init(ttg_ui *ui);
void ttg_ui_side(ttg_ui *ui,int side);
void ttg_ui_turn(ttg_ui *ui,int detents);
void ttg_ui_press(ttg_ui *ui,int held);
void ttg_ui_draw(const ttg_ui *ui,uint16_t pixels[TTG_UI_H][TTG_UI_W]);
#endif
