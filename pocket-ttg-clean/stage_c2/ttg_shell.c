#include <stdint.h>
#include <stddef.h>

typedef void (*fn_update)(void *ctx);
typedef void *(*fn_nested)(void *root, int group, int child);
typedef void (*fn_text)(void *obj, const char *text);

#define STOCK_PAGE2_UPDATE ((fn_update)(uintptr_t)0x00022800u)
#define GET_NESTED         ((fn_nested)(uintptr_t)0x0002c214u)
#define SET_TEXT           ((fn_text)(uintptr_t)0x00048cd0u)

/* Verified stock BSS zero tail: GP+0xc100 .. GP+0xc147 = 72 bytes.
 * This complete state is 70 bytes and stays entirely inside that audited tail. */
typedef struct __attribute__((packed)) {
    uint8_t magic;
    uint8_t page;
    uint8_t param;
    uint8_t edit;
    uint8_t value[66]; /* 6 pages x 11 parameter slots, byte 0..100 / toggles 0..1 */
} ttg_state_t;
#define ST (*(volatile ttg_state_t *)(uintptr_t)0x2001bb40u)
#define MAGIC 0xA5u

static const char p_d1[] __attribute__((section(".ttg_rodata")))="DRIVE 1";
static const char p_f1[] __attribute__((section(".ttg_rodata")))="FILTER 1";
static const char p_f2[] __attribute__((section(".ttg_rodata")))="FILTER 2";
static const char p_d2[] __attribute__((section(".ttg_rodata")))="DRIVE 2";
static const char p_dl[] __attribute__((section(".ttg_rodata")))="DELAY";
static const char p_cl[] __attribute__((section(".ttg_rodata")))="CLOUD";

static const char n_drv[] __attribute__((section(".ttg_rodata")))="DRIVE";
static const char n_col[] __attribute__((section(".ttg_rodata")))="COLOR";
static const char n_lvl[] __attribute__((section(".ttg_rodata")))="LEVEL";
static const char n_byp[] __attribute__((section(".ttg_rodata")))="BYPASS";
static const char n_in[]  __attribute__((section(".ttg_rodata")))="IN LVL";
static const char n_cut[] __attribute__((section(".ttg_rodata")))="CUTOFF";
static const char n_res[] __attribute__((section(".ttg_rodata")))="RESO";
static const char n_mod[] __attribute__((section(".ttg_rodata")))="MODE";
static const char n_ton[] __attribute__((section(".ttg_rodata")))="TONE";
static const char n_mix[] __attribute__((section(".ttg_rodata")))="MIX";
static const char n_tim[] __attribute__((section(".ttg_rodata")))="TIME";
static const char n_fbk[] __attribute__((section(".ttg_rodata")))="FEEDBK";
static const char n_pos[] __attribute__((section(".ttg_rodata")))="POS";
static const char n_siz[] __attribute__((section(".ttg_rodata")))="SIZE";
static const char n_den[] __attribute__((section(".ttg_rodata")))="DENS";
static const char n_tex[] __attribute__((section(".ttg_rodata")))="TEXT";
static const char n_pit[] __attribute__((section(".ttg_rodata")))="PITCH";
static const char n_spr[] __attribute__((section(".ttg_rodata")))="SPREAD";
static const char n_rev[] __attribute__((section(".ttg_rodata")))="REVERB";
static const char n_frz[] __attribute__((section(".ttg_rodata")))="FREEZE";

static __attribute__((section(".ttg_text"),noinline)) void init(void){
    if(ST.magic==MAGIC) return;
    ST.magic=MAGIC; ST.page=0; ST.param=0; ST.edit=0;
    for(unsigned i=0;i<66;i++) ST.value[i]=50;
    /* discrete fields */
    ST.value[3]=0; ST.value[11+4]=0; ST.value[11+5]=0;
    ST.value[22+4]=0; ST.value[22+5]=0; ST.value[33+3]=0;
    ST.value[44+4]=0; ST.value[55+9]=0; ST.value[55+10]=0;
}

static __attribute__((section(".ttg_text"),noinline)) uint8_t count(uint8_t p){
    if(p==0||p==3) return 4;
    if(p==1||p==2) return 6;
    if(p==4) return 5;
    return 11;
}
static __attribute__((section(".ttg_text"),noinline)) uint8_t kind(uint8_t p,uint8_t q){
    if((p==1||p==2)&&q==4) return 2;             /* HP/LP */
    if(p==5&&q==9) return 3;                     /* freeze */
    if((p==0&&q==3)||(p==1&&q==5)||(p==2&&q==5)||(p==3&&q==3)||(p==4&&q==4)||(p==5&&q==10)) return 1; /* bypass */
    return 0;
}
static __attribute__((section(".ttg_text"),noinline)) const char *page_name(uint8_t p){
    if(p==0) return p_d1; if(p==1) return p_f1; if(p==2) return p_f2;
    if(p==3) return p_d2; if(p==4) return p_dl; return p_cl;
}
static __attribute__((section(".ttg_text"),noinline)) const char *param_name(uint8_t p,uint8_t q){
    if(p==0){ if(q==0)return n_drv; if(q==1)return n_col; if(q==2)return n_lvl; return n_byp; }
    if(p==1||p==2){ if(q==0)return n_in; if(q==1)return n_cut; if(q==2)return n_res; if(q==3)return n_drv; if(q==4)return n_mod; return n_byp; }
    if(p==3){ if(q==0)return n_drv; if(q==1)return n_ton; if(q==2)return n_lvl; return n_byp; }
    if(p==4){ if(q==0)return n_mix; if(q==1)return n_tim; if(q==2)return n_fbk; if(q==3)return n_col; return n_byp; }
    if(q==0)return n_pos; if(q==1)return n_siz; if(q==2)return n_den; if(q==3)return n_tex; if(q==4)return n_pit;
    if(q==5)return n_spr; if(q==6)return n_fbk; if(q==7)return n_rev; if(q==8)return n_mix; if(q==9)return n_frz; return n_byp;
}
static __attribute__((section(".ttg_text"),noinline)) void make_line(char *d,uint8_t p,uint8_t q){
    const char *s=param_name(p,q); unsigned n=0; d[n++]=ST.edit?'>':' ';
    while(*s && n<15) d[n++]=*s++;
    d[n++]=' ';
    uint8_t k=kind(p,q),v=ST.value[(unsigned)p*11u+q];
    if(k==1||k==3){ d[n++]=v?'O':'O'; d[n++]=v?'N':'F'; if(!v)d[n++]='F'; }
    else if(k==2){ d[n++]=v?'H':'L'; d[n++]='P'; }
    else { if(v>100)v=100; d[n++]=(char)('0'+(v/100)); d[n++]=(char)('0'+((v/10)%10)); d[n++]=(char)('0'+(v%10)); }
    d[n]=0;
}

__attribute__((used,section(".ttg_text"),noinline)) uint32_t ttg_page2_hook(void *ctx){
    STOCK_PAGE2_UPDATE(ctx); init(); if(!ctx)return 0;
    void *root=*(void **)((uint8_t*)ctx+8); if(!root)return 0;
    void *small=GET_NESTED(root,3,0), *name=GET_NESTED(root,3,1);
    char line[24]; make_line(line,ST.page,ST.param);
    if(small) SET_TEXT(small,page_name(ST.page));
    if(name) SET_TEXT(name,line);
    return 0;
}

__attribute__((used,section(".ttg_text"),noinline)) void ttg_prev(void){ init(); ST.page=(ST.page==0)?5:(uint8_t)(ST.page-1); ST.param=0; ST.edit=0; }
__attribute__((used,section(".ttg_text"),noinline)) void ttg_next(void){ init(); ST.page=(uint8_t)((ST.page+1u)%6u); ST.param=0; ST.edit=0; }

static __attribute__((section(".ttg_text"),noinline)) void turn(int up){
    init(); uint8_t c=count(ST.page); if(ST.param>=c)ST.param=0;
    if(!ST.edit){ if(up) ST.param=(uint8_t)((ST.param+1u)%c); else ST.param=(ST.param==0)?(uint8_t)(c-1):(uint8_t)(ST.param-1); return; }
    unsigned i=(unsigned)ST.page*11u+ST.param; uint8_t k=kind(ST.page,ST.param),v=ST.value[i];
    if(k){ ST.value[i]=(uint8_t)(v?0:1); return; }
    if(up){ if(v<100)++v; } else { if(v>0)--v; } ST.value[i]=v;
}
__attribute__((used,section(".ttg_text"),noinline)) void ttg_enc_up(void){ turn(1); }
__attribute__((used,section(".ttg_text"),noinline)) void ttg_enc_down(void){ turn(0); }
__attribute__((used,section(".ttg_text"),noinline)) void ttg_enc_press(void){
    init(); uint8_t c=count(ST.page); if(ST.param>=c)ST.param=0;
    unsigned i=(unsigned)ST.page*11u+ST.param; uint8_t k=kind(ST.page,ST.param);
    if(k) ST.value[i]=(uint8_t)(ST.value[i]?0:1); else ST.edit^=1u;
}
