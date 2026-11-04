#include "../src/ttg_input.h"
#include <assert.h>
#include <stdio.h>
#include <string.h>
static int reads;
static unsigned get(void *arg,uint32_t *dest,unsigned n){
 (void)arg;++reads;
 unsigned got=n>20?20:n;
 for(unsigned i=0;i<got;i++)dest[i]=(uint32_t)(uint16_t)(int16_t)-1000|
       ((uint32_t)(uint16_t)(int16_t)2000<<16);
 return got;
}
int main(void){
 ttg_input s;int16_t b[64];ttg_input_init(&s,get,0);
 memset(b,0x7e,sizeof b);
 ttg_input_render(&s,b,32);
 for(int i=0;i<64;i++)assert(!b[i]);
 assert(reads==0&&s.blocks==1);
 ttg_input_set_source(&s,TTG_SOURCE_TEST_TONE);
 ttg_input_render(&s,b,32);
 assert(reads==0);int nonzero=0;
 for(int i=0;i<64;i++)nonzero+=b[i]!=0;
 assert(nonzero>10);
 ttg_input_set_source(&s,TTG_SOURCE_ADC1_UNVERIFIED);
 ttg_input_render(&s,b,32);
 assert(reads==1&&s.adc_frames==20&&s.adc_short==1);
 for(int i=0;i<20;i++)assert(b[i*2]==-1000&&b[i*2+1]==2000);
 for(int i=20;i<32;i++)assert(b[i*2]==0&&b[i*2+1]==0);
 assert(s.adc_peak_l==1000&&s.adc_peak_r==2000);
 puts("PASS TTG ADC bridge: muted boot, tone, packed stereo, short-read zero pad");
}
