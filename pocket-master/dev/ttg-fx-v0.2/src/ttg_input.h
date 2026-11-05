/* TTG FX native ADC1 bridge — MIT. Electrical jack path remains unverified. */
#ifndef TTG_INPUT_H
#define TTG_INPUT_H
#include <stdint.h>
#include <stddef.h>
#ifdef __cplusplus
extern "C" {
#endif
#define TTG_INPUT_RATE 44100u
#define TTG_INPUT_MAX_BLOCK 32u
typedef enum {TTG_SOURCE_SILENCE=0,TTG_SOURCE_TEST_TONE=1,TTG_SOURCE_ADC1_UNVERIFIED=2} ttg_source;
typedef unsigned (*ttg_adc_reader)(void *context,uint32_t *words,unsigned max_frames);
typedef struct {
 ttg_source source;
 uint32_t tone_phase;
 ttg_adc_reader adc;
 void *adc_context;
 uint32_t blocks,adc_frames,adc_short,adc_peak_l,adc_peak_r;
} ttg_input;
void ttg_input_init(ttg_input *s,ttg_adc_reader reader,void *context);
void ttg_input_set_source(ttg_input *s,ttg_source source);
void ttg_input_render(ttg_input *s,int16_t *interleaved,unsigned frames);
#ifdef __cplusplus
}
#endif
#endif
