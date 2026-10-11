#include <stdint.h>
#include <stddef.h>

typedef void   (*fn_update)(void *);
typedef void * (*fn_create)(void *);
typedef void   (*fn_style)(void *, void *, uint32_t);
typedef void   (*fn_xy)(void *, uint32_t, uint32_t);
typedef void   (*fn_text)(void *, const char *);
typedef void   (*fn_load_preset)(uint32_t);
typedef void   (*fn_set_algorithm)(uint32_t, uint32_t);
typedef void   (*fn_set_enable)(const uint8_t *);
extern void ttg_set_param_raw(uint32_t slot,uint32_t param,uint32_t dirty,uint32_t float_bits);

#define UPDATE        ((fn_update)(uintptr_t)0x00022800u)
#define OBJ_CREATE    ((fn_create)(uintptr_t)0x00047880u)
#define LABEL_CREATE  ((fn_create)(uintptr_t)0x0004838cu)
#define STYLE_ADD     ((fn_style)(uintptr_t)0x00032810u)
#define SET_POS       ((fn_xy)(uintptr_t)0x0002f15cu)
#define SET_SIZE      ((fn_xy)(uintptr_t)0x0002f1dcu)
#define TEXT          ((fn_text)(uintptr_t)0x00048cd0u)
#define LOAD_PRESET   ((fn_load_preset)(uintptr_t)0x0004e96cu)
#define SET_ALGORITHM ((fn_set_algorithm)(uintptr_t)0x0004eb1cu)
#define SET_ENABLE    ((fn_set_enable)(uintptr_t)0x0004ea74u)
#define STATE_MAGIC 0x54544746u

typedef struct {
  uint32_t magic;
  uint8_t page,param,edit,pad;
  uint8_t values[30];
  uint8_t enabled[10];
  uint32_t gui_root,overlay,head_label,page_label,param_label;
} TtgState;
#define ST (*(volatile TtgState *)(uintptr_t)0x2001bb40u)

static const uint8_t page_slot[6] __attribute__((section(".ttg_rodata")))={1,2,6,5,7,8};
static const uint8_t poff[7] __attribute__((section(".ttg_rodata")))={0,4,10,16,20,25,30};
static const char page_full[54] __attribute__((section(".ttg_rodata")))=
  "DRIVE 1\0\0FILTER 1\0FILTER 2\0DRIVE 2\0\0DELAY\0\0\0\0CLOUD\0\0\0\0";
static const char head_text[] __attribute__((section(".ttg_rodata")))="TTG FX\nD1>F1>F2>D2>DLY>CLD";
enum{P_DRV,P_COL,P_LVL,P_BYP,P_IN,P_CUT,P_RES,P_MOD,P_TON,P_MIX,P_TIM,P_FDB,P_SIZ,P_TEX,P_SPR};
static const char pnames[46] __attribute__((section(".ttg_rodata")))="DRVCOLLVLBYPIN CUTRESMODTONMIXTIMFDBSIZTEXSPR";
static const uint8_t pcode[30] __attribute__((section(".ttg_rodata")))={P_DRV,P_COL,P_LVL,P_BYP,P_IN,P_CUT,P_RES,P_DRV,P_MOD,P_BYP,P_IN,P_CUT,P_RES,P_DRV,P_MOD,P_BYP,P_DRV,P_TON,P_LVL,P_BYP,P_MIX,P_TIM,P_FDB,P_COL,P_BYP,P_MIX,P_SIZ,P_TEX,P_SPR,P_BYP};
static const uint8_t pindex[30] __attribute__((section(".ttg_rodata")))={0,1,2,0xff,0,1,2,3,4,0xff,0,1,2,3,4,0xff,0,1,2,0xff,0,1,2,3,0xff,0,1,2,3,0xff};
static const uint8_t defaults[30] __attribute__((section(".ttg_rodata")))={28,50,80,0,80,70,15,0,0,0,80,70,15,0,0,0,24,55,80,0,25,45,30,10,0,35,55,50,45,0};
static const uint8_t en_default[10] __attribute__((section(".ttg_rodata")))={0,1,1,0,0,1,1,1,1,0};

static inline uint8_t count(void){return(uint8_t)(poff[ST.page+1u]-poff[ST.page]);}
static inline unsigned vi(void){return(unsigned)poff[ST.page]+ST.param;}
static inline uint8_t code(void){return pcode[vi()];}
static inline uint8_t is_mode(uint8_t c){return c==P_MOD;}
static inline uint8_t is_bypass(uint8_t c){return c==P_BYP;}
static __attribute__((section(".ttg_aux"))) uint32_t int_float_bits(uint32_t v){if(!v)return 0;uint32_t e=0,x=v;while(x>1u){x>>=1;e++;}return((127u+e)<<23)|((v<<(23u-e))&0x7fffffu);}
static __attribute__((section(".ttg_aux"))) void copy10(volatile uint8_t*d,const uint8_t*s){for(unsigned i=0;i<10;i++)d[i]=s[i];}
static __attribute__((section(".ttg_aux"))) void copy30(volatile uint8_t*d,const uint8_t*s){for(unsigned i=0;i<30;i++)d[i]=s[i];}
static __attribute__((section(".ttg_aux"))) void publish_enable(void){SET_ENABLE((const uint8_t*)(uintptr_t)&ST.enabled[0]);}
static __attribute__((section(".ttg_aux"))) void apply_param(unsigned idx){uint8_t pi=pindex[idx];if(pi==0xffu)return;uint8_t pg=0;while(pg<5u&&idx>=poff[pg+1u])pg++;ttg_set_param_raw(page_slot[pg],pi,1u,int_float_bits(ST.values[idx]));}
static __attribute__((section(".ttg_aux"))) void init_chain(void){
  LOAD_PRESET(7u);SET_ALGORITHM(1u,0u);SET_ALGORITHM(2u,7u);SET_ALGORITHM(6u,1u);SET_ALGORITHM(5u,0u);SET_ALGORITHM(7u,7u);SET_ALGORITHM(8u,9u);
  copy10(ST.enabled,en_default);publish_enable();copy30(ST.values,defaults);
  for(unsigned i=0;i<30u;i++){if(pindex[i]!=0xffu)apply_param(i);}
  ST.page=0;ST.param=0;ST.edit=0;ST.gui_root=ST.overlay=ST.head_label=ST.page_label=ST.param_label=0;ST.magic=STATE_MAGIC;
}
static __attribute__((section(".ttg_aux"))) void line(char*d){
  unsigned idx=vi();uint8_t c=pcode[idx],v=ST.values[idx];const char*s=&pnames[(unsigned)c*3u];
  d[0]=ST.edit?'>':' ';d[1]=s[0];d[2]=s[1];d[3]=s[2];d[4]=' ';
  if(is_bypass(c)){uint8_t on=ST.enabled[page_slot[ST.page]];d[5]='O';d[6]=on?'N':'F';d[7]=on?0:'F';d[8]=0;return;}
  if(is_mode(c)){d[5]=v>=50u?'H':'L';d[6]='P';d[7]=0;return;}
  if(v>100u)v=100u;if(v==100u){d[5]='1';d[6]='0';d[7]='0';d[8]=0;return;}d[5]=(char)('0'+v/10u);d[6]=(char)('0'+v%10u);d[7]=0;
}
static __attribute__((section(".ttg_aux"))) void make_overlay(void*ctx,void*root){
  void *bg=OBJ_CREATE(root);if(!bg)return;
  STYLE_ADD(bg,(uint8_t*)ctx+0x1c,0);SET_POS(bg,0,0);SET_SIZE(bg,0x2064,0x2064);
  void *h=LABEL_CREATE(bg);if(h){STYLE_ADD(h,(uint8_t*)ctx+0x0c,0);SET_POS(h,7,7);TEXT(h,head_text);}
  void *p=LABEL_CREATE(bg);if(p){STYLE_ADD(p,(uint8_t*)ctx+0x14,0);SET_POS(p,7,58);}
  void *v=LABEL_CREATE(bg);if(v){STYLE_ADD(v,(uint8_t*)ctx+0x14,0);SET_POS(v,7,84);}
  ST.gui_root=(uint32_t)(uintptr_t)root;ST.overlay=(uint32_t)(uintptr_t)bg;ST.head_label=(uint32_t)(uintptr_t)h;ST.page_label=(uint32_t)(uintptr_t)p;ST.param_label=(uint32_t)(uintptr_t)v;
}

__attribute__((used,section(".ttg_text"),noinline)) uint32_t ttg_page2_hook(void*ctx){
  if(ST.magic!=STATE_MAGIC)init_chain();
  UPDATE(ctx);if(!ctx)return 0;void*root=*(void**)((uint8_t*)ctx+8);if(!root)return 0;
  if(ST.gui_root!=(uint32_t)(uintptr_t)root||!ST.overlay)make_overlay(ctx,root);
  char q[9];line(q);
  if(ST.page_label)TEXT((void*)(uintptr_t)ST.page_label,&page_full[(unsigned)ST.page*9u]);
  if(ST.param_label)TEXT((void*)(uintptr_t)ST.param_label,q);
  return 0;
}
__attribute__((used,section(".ttg_aux"),noinline)) void ttg_prev(void){ST.page=ST.page?ST.page-1u:5u;ST.param=0;ST.edit=0;}
__attribute__((used,section(".ttg_aux"),noinline)) void ttg_next(void){ST.page=ST.page==5u?0u:ST.page+1u;ST.param=0;ST.edit=0;}
static __attribute__((section(".ttg_text"))) void turn(uint8_t up){uint8_t n=count();if(!ST.edit){ST.param=up?(ST.param+1u<n?ST.param+1u:0u):(ST.param?ST.param-1u:n-1u);return;}unsigned idx=vi();uint8_t c=code();if(is_bypass(c))return;if(is_mode(c))ST.values[idx]=ST.values[idx]>=50u?0u:100u;else if(up){if(ST.values[idx]<100u)ST.values[idx]++;}else if(ST.values[idx])ST.values[idx]--;apply_param(idx);}
__attribute__((used,section(".ttg_text"),noinline)) void ttg_enc_up(void){turn(1);}
__attribute__((used,section(".ttg_text"),noinline)) void ttg_enc_down(void){turn(0);}
__attribute__((used,section(".ttg_text"),noinline)) void ttg_enc_press(void){unsigned idx=vi();uint8_t c=code();if(is_bypass(c)){uint8_t slot=page_slot[ST.page];ST.enabled[slot]=(uint8_t)!ST.enabled[slot];publish_enable();return;}if(is_mode(c)){ST.values[idx]=ST.values[idx]>=50u?0u:100u;apply_param(idx);return;}ST.edit^=1u;}
