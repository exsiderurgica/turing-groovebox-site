/* TTG FX passive SPIS RX diagnostic ring. SPDX-License-Identifier: MIT */
#include "spis_probe.h"
#include <string.h>
/* A compiler barrier is sufficient for the assumed single-core in-order MCU,
   where aligned unsigned loads/stores are atomic. Validate before target port. */
#if defined(__GNUC__)
#define TTG_FENCE() __asm__ __volatile__("" ::: "memory")
#else
#define TTG_FENCE() do {} while(0)
#endif
void ttg_spis_probe_reset(ttg_spis_probe *p){ memset(p,0,sizeof *p); }
void ttg_spis_probe_rx_isr(ttg_spis_probe *p,const uint8_t rx[TTG_SPIS_BYTES]){
    if (++p->sample_count < TTG_SPIS_DIVIDER) return;
    p->sample_count=0;
    unsigned next=(p->head+1u)%TTG_SPIS_SLOTS;
    if (next==p->tail){p->dropped++;return;}
    memcpy(p->frames[p->head],rx,TTG_SPIS_BYTES);
    TTG_FENCE();
    p->head=next;
    p->captured++;
}
int ttg_spis_probe_pop(ttg_spis_probe *p,uint8_t out[TTG_SPIS_BYTES]){
    if (p->tail==p->head)return 0;
    memcpy(out,p->frames[p->tail],TTG_SPIS_BYTES);
    TTG_FENCE();
    p->tail=(p->tail+1u)%TTG_SPIS_SLOTS;
    return 1;
}
