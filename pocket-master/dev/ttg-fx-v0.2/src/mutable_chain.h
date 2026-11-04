/* MIT SPDX-License-Identifier: MIT, TTG FX glue with genuine Mutable code. */
#ifndef TTG_MUTABLE_CHAIN_H_INCLUDED
#define TTG_MUTABLE_CHAIN_H_INCLUDED
#include <stdint.h>
#include <stddef.h>
#ifdef __cplusplus
extern "C" {
#endif
typedef struct {float gain,level,cutoff,resonance,delay_ms,feedback,wet,decay,diffusion,tone;uint8_t on[6];} ttg_mutable_params;
void ttg_mutable_init(void);
void ttg_mutable_set_params(const ttg_mutable_params *p);
void ttg_mutable_process(const int16_t *in,int16_t *out,size_t frames);
size_t ttg_mutable_static_bytes(void);
#ifdef __cplusplus
}
#endif
#endif
