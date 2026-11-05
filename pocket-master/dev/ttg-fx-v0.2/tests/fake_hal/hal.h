/* TEST ONLY; never include in a real device build. */
#pragma once
#include <stdint.h>
#define HAL_BLOCK 32u
#define HAL_EV_LEFT 1
#define HAL_EV_RIGHT 2
#define HAL_EV_PUSH 3
#define HAL_EV_CW 4
#define HAL_EV_CCW 5
#define HAL_EV_UP 0x80
extern volatile uint32_t hal_watch;
uint32_t hal_keys_at_boot(void);
void hal_clocks(void);void hal_dma_clear(void);int hal_init(uint32_t);
void hal_audio(void (*)(int16_t*));
void hal_backlight(uint32_t);void hal_lcd_frame(const uint16_t*,uint32_t,uint32_t);
void hal_lcd_on(int);void hal_render(void);int hal_sides_held(void);
int hal_event(void);uint32_t hal_ms(void);void boot_recover(void);
int hal_power_off(void);void hal_link_restart(void);
