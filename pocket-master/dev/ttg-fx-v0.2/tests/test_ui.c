#include "../src/ttg_ui.h"
#include <assert.h>
#include <stdio.h>
static uint16_t fb[TTG_UI_H][TTG_UI_W];
int main(void){
 ttg_ui ui;ttg_ui_init(&ui);
 assert(ui.selected==0&&ui.enabled[0]);
 ttg_ui_side(&ui,-1);assert(ui.selected==5);
 ttg_ui_side(&ui,1);assert(ui.selected==0);
 ttg_ui_turn(&ui,99);assert(ui.parameter==3);
 ttg_ui_turn(&ui,-99);assert(ui.parameter==0);
 ttg_ui_press(&ui,0);assert(ui.editing);
 ttg_ui_turn(&ui,200);assert(ui.values[0][0]==100);
 ttg_ui_press(&ui,1);assert(!ui.enabled[0]&&!ui.editing);
 ttg_ui_draw(&ui,fb);assert(fb[0][0]!=fb[10][10]);
 puts("Independent UI 160x128: PASS");
 return 0;
}
