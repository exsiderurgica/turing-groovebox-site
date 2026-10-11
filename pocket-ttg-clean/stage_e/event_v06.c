#include <stdint.h>

extern void ttg_prev(void);
extern void ttg_next(void);
extern void ttg_enc_up(void);
extern void ttg_enc_down(void);
extern void ttg_enc_press(void);

#define TTG_PENDING (*(volatile uint32_t *)(uintptr_t)0x2001bb74u)

/* Called by the normal page2 scheduler. The global raw-event gate only deposits
   events here; all real TTG actions execute from this normal C callback. */
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
        case 0x7f46u: ttg_enc_press(); break;     /* stock debug string: menu key */
        default: break;
    }
    return 0;
}
