/* SPDX-License-Identifier: GPL-3.0-only
 * TTG FX original standalone main application, Pocket Master diagnostic-only.
 * Links with Molecular's GPLv3 board HAL and Mutable's MIT DSP code.
 * NEITHER guitar input wiring NOR build safety is verified on actual hardware.
 */
#ifndef TTG_DEVICE_DIAGNOSTIC_ONLY
#error "Diagnostic-only source: an unverified ADC1 route must not be published as stable"
#endif
#include <stdint.h>
#include <nds32_intrinsic.h>
#include "hal.h"
#include "usbmidi.h"
#include "armlink.h"
#include "irqn.h"
#include "watchdog.h"
#include "timeout.h"
#include "ttg_app.h"
#include "ttg_input.h"

static ttg_app app;
static ttg_input source;
static uint16_t lcd_fb[TTG_UI_H][TTG_UI_W];
extern unsigned ttg_adc_read(void *,uint32_t *,unsigned);
static unsigned count_bits(unsigned v){unsigned n=0;for(;v;v&=v-1u)n++;return n;}
static void audio_block(int16_t *output){
  int16_t input[2u*HAL_BLOCK];
  ttg_input_render(&source,input,HAL_BLOCK);
  if(source.source==TTG_SOURCE_SILENCE){
    for(unsigned i=0;i<2u*HAL_BLOCK;i++)output[i]=0; /* true hard mute */
  }else{
    ttg_app_process(&app,input,output,HAL_BLOCK);
  }
}
static void lcd_draw(void){
  ttg_app_draw(&app,lcd_fb);
  /* Permanent RED banner: diagnostic only; guitar jack unverified. */
  for(unsigned i=0;i<TTG_UI_W;i++)lcd_fb[0][i]=0xF800u;
  hal_lcd_frame(&lcd_fb[0][0],TTG_UI_W,TTG_UI_H);
}
int main(void){
  uint32_t held,last_draw=0,last_diag=0,last_right_up=0;
  uint32_t right_seen=0,encoder_held_at=0,both_at=0;
  unsigned i;
  WDG_Disable();
  held=hal_keys_at_boot();
  if(count_bits(held&7u)>=2u)boot_recover();
  for(i=1;i<=30u;i++)NVIC_DisableIRQ((IRQn_Type)i);
  hal_clocks();WDG_Disable();hal_dma_clear();GIE_ENABLE();
  usb_start();
  hal_init(held);
  arm_init();DelayMs(300);arm_start();
  ttg_app_init(&app);
  ttg_input_init(&source,ttg_adc_read,0); /* always muted */
  hal_audio(audio_block);
  lcd_draw();
  hal_lcd_on(1);hal_backlight(80);
  hal_watch=1;
  usb_log("TTG FX DIAGNOSTIC: MUTED; ADC1 UNVERIFIED");
  for(;;){
    uint32_t now=hal_ms();
    hal_render(); /* keeps SPI output buffer filled in foreground */
    usb_poll();
    if(usb_midi_rx()){DelayMs(50);boot_recover();}
    arm_poll();
    if((uint32_t)(now-last_diag)>=2000u){
      last_diag=now;
      usb_logn("source 0mute 1tone 2ADC",(uint32_t)source.source);
      usb_logn("ADC read frames",source.adc_frames);
      usb_logn("ADC short reads",source.adc_short);
      usb_logn("ADC peak L",source.adc_peak_l);
      usb_logn("ADC peak R",source.adc_peak_r);
      source.adc_peak_l=source.adc_peak_r=0u;
    }
    if(!hal_sides_held())both_at=0;
    else if(!both_at)both_at=now|1u;
    else if((uint32_t)(now-both_at)>=3000u){
      hal_backlight(0);DelayMs(50);boot_recover(); /* recovery */
    }
    int ev;
    while((ev=hal_event())>=0){
      switch(ev&0x8F){
      case HAL_EV_LEFT:
        ttg_app_event(&app,TTG_EV_LEFT,now);break;
      case HAL_EV_RIGHT:
        ttg_app_event(&app,TTG_EV_RIGHT,now);break;
      case HAL_EV_RIGHT|HAL_EV_UP:
        /* User gesture, never automatic: MUTE->TONE->UNVERIFIED ADC->MUTE. */
        if(right_seen&&(uint32_t)(now-last_right_up)<350u)
          ttg_input_set_source(&source,(ttg_source)((source.source+1u)%3u));
        last_right_up=now;right_seen=1u;break;
      case HAL_EV_CW:
        ttg_app_event(&app,TTG_EV_CW,now);break;
      case HAL_EV_CCW:
        ttg_app_event(&app,TTG_EV_CCW,now);break;
      case HAL_EV_PUSH:
        encoder_held_at=now|1u;
        ttg_app_event(&app,TTG_EV_PUSH_DOWN,now);break;
      case HAL_EV_PUSH|HAL_EV_UP:
        encoder_held_at=0u;
        ttg_app_event(&app,TTG_EV_PUSH_UP,now);break;
      }
    }
    ttg_app_tick(&app,now);
    if(encoder_held_at&&(uint32_t)(now-encoder_held_at)>=4500u){
      encoder_held_at=0;
      hal_backlight(0);
      hal_power_off();
      hal_audio(audio_block);
      arm_init();DelayMs(300);arm_start();
      hal_link_restart();
      ttg_app_init(&app);
      ttg_input_init(&source,ttg_adc_read,0);
      hal_lcd_on(1);hal_backlight(80);
    }
    if((uint32_t)(now-last_draw)>=40u){
      last_draw=now;
      lcd_draw();
    }
  }
}
