/* TTG FX passive SPIS RX diagnostic ring. SPDX-License-Identifier: MIT */
#ifndef TTG_SPIS_PROBE_H
#define TTG_SPIS_PROBE_H
#include <stdint.h>
#define TTG_SPIS_BYTES 512u
#define TTG_SPIS_SLOTS 4u
#define TTG_SPIS_DIVIDER 32u
/* Single producer: SPIS DMA-done ISR. Single consumer: foreground loop.
   No dynamic allocation, no serial I/O, no interrupts masked in ISR. */
typedef struct {
    uint8_t frames[TTG_SPIS_SLOTS][TTG_SPIS_BYTES];
    volatile unsigned head, tail;
    unsigned sample_count;
    volatile uint32_t captured, dropped;
} ttg_spis_probe;
void ttg_spis_probe_reset(ttg_spis_probe *p);
void ttg_spis_probe_rx_isr(ttg_spis_probe *p, const uint8_t completed_rx[TTG_SPIS_BYTES]);
int ttg_spis_probe_pop(ttg_spis_probe *p, uint8_t out[TTG_SPIS_BYTES]);
#endif
