/* SPDX-License-Identifier: MIT */
#include "../src/spis_probe.h"
#include <stdio.h>
#include <string.h>
#include <assert.h>
static ttg_spis_probe p;
int main(void){
    static uint8_t src[TTG_SPIS_BYTES], dst[TTG_SPIS_BYTES];
    ttg_spis_probe_reset(&p);
    memset(src,0xA5,sizeof src);
    for(unsigned i=0;i<TTG_SPIS_DIVIDER-1;i++)ttg_spis_probe_rx_isr(&p,src);
    assert(p.captured==0&&ttg_spis_probe_pop(&p,dst)==0);
    ttg_spis_probe_rx_isr(&p,src);
    assert(p.captured==1&&ttg_spis_probe_pop(&p,dst)==1);
    assert(memcmp(src,dst,TTG_SPIS_BYTES)==0);
    assert(ttg_spis_probe_pop(&p,dst)==0);
    for(unsigned j=0;j<6;j++)for(unsigned i=0;i<TTG_SPIS_DIVIDER;i++)ttg_spis_probe_rx_isr(&p,src);
    assert(p.captured==4&&p.dropped==3); /* capacity: 3 queued frames */
    for(unsigned i=0;i<3;i++)assert(ttg_spis_probe_pop(&p,dst)==1);
    assert(ttg_spis_probe_pop(&p,dst)==0);
    puts("SPIS RX capture ring host tests: PASS (512-byte frames, bounded ISR, overflow)");
    return 0;
}
