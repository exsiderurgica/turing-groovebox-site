#include <stdint.h>

extern void ttg_prev(void);
extern void ttg_next(void);
extern void ttg_enc_up(void);
extern void ttg_enc_down(void);
extern void ttg_enc_press(void);

#define TTG_PENDING (*(volatile uint32_t *)(uintptr_t)0x2001bb74u)

/* v0.7: the global hook now runs at stock runtime 0x52c40, where r0 is
   statically verified to contain the real 0x7Fxx event code.  This callback
   consumes the queued event in the normal page scheduler context, so all GUI
   rendering and DSP state changes stay out of the global dispatcher. */
__attribute__((used,section(".page_event"),noinline))
uint32_t ttg_page2_event(void *ctx){
    (void)ctx;
    uint32_t e=TTG_PENDING;
    if(!e) return 0;
    TTG_PENDING=0;
    switch(e){
        case 0x7f36u: ttg_prev(); break;          /* MSG_PRE */
        case 0x7f37u: ttg_next(); break;          /* MSG_NEXT */
        case 0x7f5du: ttg_enc_up(); break;        /* MSG_MAIN_VOL_UP */
        case 0x7f5eu: ttg_enc_down(); break;      /* MSG_MAIN_VOL_DW */
        case 0x7f46u: ttg_enc_press(); break;     /* verified menu-key/long VALUE */
        default: break;
    }
    return 0;
}
