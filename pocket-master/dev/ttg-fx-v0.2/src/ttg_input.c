/* SPDX-License-Identifier: MIT; NOT verified on Pocket Master hardware. */
#include "ttg_input.h"
#include <string.h>
static uint32_t abs16(int16_t v){return v<0?(uint32_t)-(int32_t)v:(uint32_t)v;}
void ttg_input_init(ttg_input *s,ttg_adc_reader reader,void *context){
 memset(s,0,sizeof *s);s->adc=reader;s->adc_context=context;
}
void ttg_input_set_source(ttg_input *s,ttg_source src){
 if(src!=TTG_SOURCE_SILENCE&&src!=TTG_SOURCE_TEST_TONE&&src!=TTG_SOURCE_ADC1_UNVERIFIED)return;
 s->source=src;
}
void ttg_input_render(ttg_input *s,int16_t *out,unsigned frames){
 if(!out||!s||frames>TTG_INPUT_MAX_BLOCK)return;
 uint32_t words[TTG_INPUT_MAX_BLOCK];
 memset(out,0,2u*frames*sizeof *out);
 ++s->blocks;
 if(s->source==TTG_SOURCE_TEST_TONE){
  for(unsigned i=0;i<frames;i++){
   s->tone_phase+=176u;
   if(s->tone_phase>=TTG_INPUT_RATE)s->tone_phase-=TTG_INPUT_RATE;
   int32_t triangle=s->tone_phase<22050u?(int32_t)s->tone_phase:
                     (int32_t)TTG_INPUT_RATE-(int32_t)s->tone_phase;
   int16_t v=(int16_t)((triangle-11025)/12);
   out[2*i]=out[2*i+1]=v;
  }
  return;
 }
 if(s->source!=TTG_SOURCE_ADC1_UNVERIFIED||!s->adc)return;
 unsigned got=s->adc(s->adc_context,words,frames);
 if(got>frames)got=frames;
 s->adc_frames+=got;
 if(got<frames)s->adc_short++;
 for(unsigned i=0;i<got;i++){
  /* HYPOTHESIS: 4-byte DMA frame is packed left/right signed PCM16. */
  int16_t l=(int16_t)(uint16_t)words[i];
  int16_t r=(int16_t)(uint16_t)(words[i]>>16);
  out[2*i]=l;out[2*i+1]=r;
  uint32_t p=abs16(l),q=abs16(r);
  if(p>s->adc_peak_l)s->adc_peak_l=p;
  if(q>s->adc_peak_r)s->adc_peak_r=q;
 }
}
