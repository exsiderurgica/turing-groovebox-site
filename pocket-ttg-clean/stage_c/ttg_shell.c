#include <stdint.h>
#include <stddef.h>

typedef void (*fn_update)(void *ctx);
typedef void *(*fn_nested)(void *root, int group, int child);
typedef void (*fn_text)(void *obj, const char *text);

#define STOCK_PAGE2_UPDATE ((fn_update)(uintptr_t)0x00022800u)
#define GET_NESTED         ((fn_nested)(uintptr_t)0x0002c214u)
#define SET_TEXT           ((fn_text)(uintptr_t)0x00048cd0u)

/* Stock startup zeroes GP[-0xb4a0 .. +0xc148). GP=0x2000fa40.
 * Static RE found no direct GP references in +0xc100..+0xc147.
 * Keep TTG state in that verified zero-initialized tail. */
#define TTG_PAGE_STATE (*(volatile uint32_t *)(uintptr_t)0x2001bb40u)

static const char page0[] __attribute__((section(".ttg_rodata"))) = "DRIVE 1";
static const char page1[] __attribute__((section(".ttg_rodata"))) = "FILTER 1";
static const char page2[] __attribute__((section(".ttg_rodata"))) = "FILTER 2";
static const char page3[] __attribute__((section(".ttg_rodata"))) = "DRIVE 2";
static const char page4[] __attribute__((section(".ttg_rodata"))) = "DELAY";
static const char page5[] __attribute__((section(".ttg_rodata"))) = "CLOUD";
static const char ttg[]   __attribute__((section(".ttg_rodata"))) = "TTG";

static __attribute__((section(".ttg_text"), noinline))
const char *page_name(uint32_t p) {
    switch (p % 6u) {
        case 0: return page0;
        case 1: return page1;
        case 2: return page2;
        case 3: return page3;
        case 4: return page4;
        default:return page5;
    }
}

/* Called in place of the final stock page2 updater. We deliberately run the
 * original updater first, then overwrite only two EXISTING label objects.
 * No widget creation, no guessed GUI ABI. */
__attribute__((used, section(".ttg_text"), noinline))
uint32_t ttg_page2_hook(void *ctx) {
    STOCK_PAGE2_UPDATE(ctx);
    if (!ctx) return 0;
    void *root = *(void **)((uint8_t *)ctx + 8);
    if (!root) return 0;
    void *small = GET_NESTED(root, 3, 0); /* stock Pxx field */
    void *name  = GET_NESTED(root, 3, 1); /* stock preset-name field */
    if (small) SET_TEXT(small, ttg);
    if (name)  SET_TEXT(name, page_name(TTG_PAGE_STATE));
    return 0;
}

/* Stock high-level key dispatcher has PRE=0x7f36 and NEXT=0x7f37.
 * These functions replace only the final preset-change calls in those two
 * blocks, so footswitches become TTG page navigation instead of preset nav. */
__attribute__((used, section(".ttg_text"), noinline))
void ttg_prev(void) {
    uint32_t p = TTG_PAGE_STATE % 6u;
    TTG_PAGE_STATE = (p == 0u) ? 5u : (p - 1u);
}

__attribute__((used, section(".ttg_text"), noinline))
void ttg_next(void) {
    TTG_PAGE_STATE = (TTG_PAGE_STATE + 1u) % 6u;
}
