/* TTG FX DSP host prototype, original code. SPDX-License-Identifier: MIT */
#ifndef TTGFX_H
#define TTGFX_H
#include <stdint.h>
#include <stddef.h>
#define TTG_RATE 44100
#define TTG_DELAY_LEN 16384
#define TTG_COMB_COUNT 4
#define TTG_COMB_LEN 1600
typedef struct { int on; float gain, level; } ttg_drive_params;
typedef struct { int on; float cutoff_hz, resonance, level; int highpass; } ttg_filter_params;
typedef struct { int on; float time_ms, feedback, color, wet; } ttg_delay_params;
typedef struct { int on; float decay, wet; } ttg_reverb_params;
typedef struct {
    ttg_drive_params d1,d2;
    ttg_filter_params f1,f2;
    ttg_delay_params delay;
    ttg_reverb_params reverb;
} ttg_params;
typedef struct { float ic1[2],ic2[2]; float a1,a2,a3,k; } ttg_svf;
typedef struct {
    ttg_params p;
    ttg_svf f[2];
    int16_t delay_buf[2][TTG_DELAY_LEN];
    float delay_lp[2], delay_wet[2], decim_input[2];
    unsigned delay_pos, decim_phase;
    int16_t comb[2][TTG_COMB_COUNT][TTG_COMB_LEN];
    float reverb_lp[2];
    unsigned comb_pos[TTG_COMB_COUNT];
} ttg_fx;
/* State must be static/global (approx. 90 KB), never on a microcontroller stack. */
void ttg_init(ttg_fx *fx);
void ttg_set_params(ttg_fx *fx, const ttg_params *params);
/* Interleaved signed 16-bit stereo. May process in place. */
void ttg_process(ttg_fx *fx, const int16_t *input, int16_t *output, size_t frames);
#endif
