#include <stdint.h>
#include <stddef.h>

typedef void   (*fn_void_u32)(uint32_t);
typedef void   (*fn_void_ptr)(void *);
typedef void   (*fn_prep_data)(void *,uint32_t);
typedef void * (*fn_get_display)(void);
typedef void * (*fn_get_active_screen)(void *);
typedef void * (*fn_screen_create)(void *);
typedef void * (*fn_obj_create)(void *);
typedef void   (*fn_pos)(void *,int,int);
typedef void   (*fn_style_init)(void *);
typedef uint32_t (*fn_color)(uint32_t,uint32_t);
typedef void   (*fn_style_u32)(void *,uint32_t);
typedef void * (*fn_font)(uint32_t);
typedef void   (*fn_apply_style)(void *,void *,uint32_t);
typedef void   (*fn_text)(void *,const char *);
typedef void   (*fn_load_preset)(uint32_t);
typedef void   (*fn_set_algorithm)(uint32_t,uint32_t);
typedef void   (*fn_set_enable)(const uint8_t *);
extern void ttg_set_param_raw(uint32_t slot,uint32_t param,uint32_t dirty,uint32_t float_bits);

/* GUI sequence copied byte-for-byte from the stock page2 creation path:
   display = 0x436b0(); parent = 0x2cce0(display); root = 0x2ca44(parent). */
#define PAGE_PREP0        ((fn_void_u32)(uintptr_t)0x00015ac4u)
#define PAGE_PREP1        ((fn_prep_data)(uintptr_t)0x00016358u)
#define PAGE_DELAY        ((fn_void_u32)(uintptr_t)0x00016280u)
#define GET_DISPLAY       ((fn_get_display)(uintptr_t)0x000436b0u)
#define GET_ACTIVE_SCREEN ((fn_get_active_screen)(uintptr_t)0x0002cce0u)
#define CREATE_SCREEN     ((fn_screen_create)(uintptr_t)0x0002ca44u)
#define CREATE_OBJ        ((fn_obj_create)(uintptr_t)0x00047880u)
#define CREATE_LABEL      ((fn_obj_create)(uintptr_t)0x0004838cu)
#define SET_POS           ((fn_pos)(uintptr_t)0x0002f15cu)
#define SET_SIZE          ((fn_pos)(uintptr_t)0x0002f1dcu)
#define STYLE_INIT        ((fn_style_init)(uintptr_t)0x00045c38u)
#define STYLE_RESET       ((fn_style_init)(uintptr_t)0x00045c40u)
#define COLOR_GET         ((fn_color)(uintptr_t)0x0002c1d0u)
#define STYLE_COLOR       ((fn_style_u32)(uintptr_t)0x000463a8u)
#define STYLE_FONT        ((void(*)(void*,void*))(uintptr_t)0x000463c0u)
#define STYLE_BG_COLOR    ((fn_style_u32)(uintptr_t)0x00046210u)
#define STYLE_BG_OPA      ((fn_style_u32)(uintptr_t)0x00046438u)
#define STYLE_BG_A        ((fn_style_u32)(uintptr_t)0x00046408u)
#define STYLE_BG_B        ((fn_style_u32)(uintptr_t)0x00046360u)
#define STYLE_BG_C        ((fn_style_u32)(uintptr_t)0x000461b0u)
#define STYLE_BG_D        ((fn_style_u32)(uintptr_t)0x000461c8u)
#define STYLE_BG_E        ((fn_style_u32)(uintptr_t)0x00046180u)
#define STYLE_BG_F        ((fn_style_u32)(uintptr_t)0x00046198u)
#define FONT_GET          ((fn_font)(uintptr_t)0x00015ab0u)
#define APPLY_STYLE       ((fn_apply_style)(uintptr_t)0x00032810u)
#define TEXT              ((fn_text)(uintptr_t)0x00048cd0u)
#define DELETE_ROOT       ((fn_void_ptr)(uintptr_t)0x00033980u)

#define LOAD_PRESET     ((fn_load_preset)(uintptr_t)0x0004e96cu)
#define SET_ALGORITHM   ((fn_set_algorithm)(uintptr_t)0x0004eb1cu)
#define SET_ENABLE      ((fn_set_enable)(uintptr_t)0x0004ea74u)
#define STATE_MAGIC 0x54544745u

#define CTX_SCREEN(c)   (*(void **)((uint8_t*)(c)+0x08u))
#define CTX_STYLE_SM(c) ((void *)((uint8_t*)(c)+0x0cu))
#define CTX_STYLE_LG(c) ((void *)((uint8_t*)(c)+0x14u))
#define CTX_STYLE_BG(c) ((void *)((uint8_t*)(c)+0x1cu))
#define CTX_PAGE(c)     (*(void **)((uint8_t*)(c)+0x34u))
#define CTX_PARAM(c)    (*(void **)((uint8_t*)(c)+0x38u))

typedef struct __attribute__((packed)){
  uint32_t magic;
  uint8_t page,param,edit;
  uint8_t values[30];
  uint8_t enabled[10];
  uint32_t ctx;
} TtgState;
#define ST (*(volatile TtgState *)(uintptr_t)0x2001bb40u)

static const uint8_t page_slot[6] __attribute__((section(".ttg_rodata")))={1,2,6,5,7,8};
static const uint8_t poff[7] __attribute__((section(".ttg_rodata")))={0,4,10,16,20,25,30};
static const char page_name[6][9] __attribute__((section(".ttg_rodata")))={"DRIVE 1","FILTER 1","FILTER 2","DRIVE 2","DELAY","CLOUD"};
static const char title[] __attribute__((section(".ttg_rodata")))="TTG FX";
static const char chain[] __attribute__((section(".ttg_rodata")))="D1>F1>F2>D2>DLY>CLD";
enum{P_DRV,P_COL,P_LVL,P_BYP,P_IN,P_CUT,P_RES,P_MOD,P_TON,P_MIX,P_TIM,P_FDB,P_SIZ,P_TEX,P_SPR};
static const char pnames[46] __attribute__((section(".ttg_rodata")))="DRVCOLLVLBYPIN CUTRESMODTONMIXTIMFDBSIZTEXSPR";
static const uint8_t pcode[30] __attribute__((section(".ttg_rodata")))={P_DRV,P_COL,P_LVL,P_BYP,P_IN,P_CUT,P_RES,P_DRV,P_MOD,P_BYP,P_IN,P_CUT,P_RES,P_DRV,P_MOD,P_BYP,P_DRV,P_TON,P_LVL,P_BYP,P_MIX,P_TIM,P_FDB,P_COL,P_BYP,P_MIX,P_SIZ,P_TEX,P_SPR,P_BYP};
static const uint8_t pindex[30] __attribute__((section(".ttg_rodata")))={0,1,2,0xff,0,1,2,3,4,0xff,0,1,2,3,4,0xff,0,1,2,0xff,0,1,2,3,0xff,0,1,2,3,0xff};
static const uint8_t defaults[30] __attribute__((section(".ttg_rodata")))={28,50,80,0,80,70,15,0,0,0,80,70,15,0,0,0,24,55,80,0,25,45,30,10,0,35,55,50,45,0};
static const uint8_t en_default[10] __attribute__((section(".ttg_rodata")))={0,1,1,0,0,1,1,1,1,0};

static inline uint8_t count(void){return (uint8_t)(poff[ST.page+1u]-poff[ST.page]);}
static inline unsigned vi(void){return (unsigned)poff[ST.page]+ST.param;}
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
  copy10(ST.enabled,en_default);publish_enable();copy30(ST.values,defaults);for(unsigned i=0;i<30u;i++)if(pindex[i]!=0xffu)apply_param(i);
  ST.page=0;ST.param=0;ST.edit=0;ST.ctx=0;ST.magic=STATE_MAGIC;
}
static __attribute__((section(".ttg_aux"))) void line(char*d){
  unsigned idx=vi();uint8_t c=pcode[idx],v=ST.values[idx];const char*s=&pnames[(unsigned)c*3u];
  d[0]=ST.edit?'>':' ';d[1]=s[0];d[2]=s[1];d[3]=s[2];d[4]=' ';
  if(is_bypass(c)){uint8_t on=ST.enabled[page_slot[ST.page]];d[5]='O';d[6]=on?'N':'F';d[7]=on?0:'F';d[8]=0;return;}
  if(is_mode(c)){d[5]=v>=50u?'H':'L';d[6]='P';d[7]=0;return;}
  if(v>100u)v=100u;if(v==100u){d[5]='1';d[6]='0';d[7]='0';d[8]=0;return;}d[5]=(char)('0'+v/10u);d[6]=(char)('0'+v%10u);d[7]=0;
}
static __attribute__((section(".ttg_text"))) void render(void*ctx){if(!ctx)return;char q[9];line(q);void*lp=CTX_PAGE(ctx),*lv=CTX_PARAM(ctx);if(lp)TEXT(lp,page_name[ST.page]);if(lv)TEXT(lv,q);}
static __attribute__((section(".ttg_text"))) void make_label(void*parent,void*style,int x,int y,const char*txt,void**save){void*l=CREATE_LABEL(parent);if(!l)return;APPLY_STYLE(l,style,0u);SET_POS(l,x,y);TEXT(l,txt);if(save)*save=l;}
static __attribute__((section(".ttg_text"))) void setup_styles(void*ctx){
  void*sm=CTX_STYLE_SM(ctx);void*lg=CTX_STYLE_LG(ctx);void*bg=CTX_STYLE_BG(ctx);
  STYLE_INIT(sm);STYLE_COLOR(sm,COLOR_GET(6u,0xffu));STYLE_FONT(sm,FONT_GET(0u));
  STYLE_INIT(lg);STYLE_COLOR(lg,COLOR_GET(6u,0xffu));STYLE_FONT(lg,FONT_GET(2u));
  STYLE_INIT(bg);STYLE_BG_COLOR(bg,COLOR_GET(2u,0xffu));STYLE_BG_OPA(bg,0xffu);STYLE_BG_A(bg,1u);STYLE_BG_B(bg,0u);STYLE_BG_C(bg,0u);STYLE_BG_D(bg,0u);STYLE_BG_E(bg,0u);STYLE_BG_F(bg,0u);
}

/* Complete replacement: stock renderer is not called. The parent sequence below is
   the exact ABI used by stock page0_enter and page2_enter. */
__attribute__((used,section(".page_enter"),noinline)) uint32_t ttg_page2_enter(void*ctx){
  if(!ctx)return 0;if(ST.magic!=STATE_MAGIC)init_chain();ST.ctx=(uint32_t)(uintptr_t)ctx;
  PAGE_PREP0(0u);PAGE_PREP1((void*)(uintptr_t)0x000e0b18u,0x0bu);PAGE_DELAY(10u);setup_styles(ctx);
  void*display=GET_DISPLAY();if(!display)return 0;
  void*parent=GET_ACTIVE_SCREEN(display);if(!parent)return 0;
  void*root=CREATE_SCREEN(parent);CTX_SCREEN(ctx)=root;if(!root)return 0;SET_SIZE(root,0x2064,0x2064);
  void*panel=CREATE_OBJ(root);if(!panel)return 0;APPLY_STYLE(panel,CTX_STYLE_BG(ctx),0u);SET_POS(panel,0,0);SET_SIZE(panel,0x2064,0x2064);
  make_label(panel,CTX_STYLE_SM(ctx),8,6,title,0);
  make_label(panel,CTX_STYLE_LG(ctx),8,32,page_name[ST.page],(void**)&CTX_PAGE(ctx));
  char q[9];line(q);make_label(panel,CTX_STYLE_LG(ctx),8,62,q,(void**)&CTX_PARAM(ctx));
  make_label(panel,CTX_STYLE_SM(ctx),4,102,chain,0);render(ctx);return 0;
}

__attribute__((used,section(".page_leave"),noinline)) uint32_t ttg_page2_leave(void*ctx){
  if(!ctx)return 0;ST.ctx=0;CTX_PAGE(ctx)=0;CTX_PARAM(ctx)=0;
  STYLE_RESET(CTX_STYLE_SM(ctx));STYLE_RESET(CTX_STYLE_LG(ctx));STYLE_RESET(CTX_STYLE_BG(ctx));
  if(CTX_SCREEN(ctx)){DELETE_ROOT(CTX_SCREEN(ctx));CTX_SCREEN(ctx)=0;}return 0;
}
__attribute__((used,section(".page_event"),noinline)) uint32_t ttg_page2_event(void*ctx){ST.ctx=(uint32_t)(uintptr_t)ctx;render(ctx);return 0;}

static __attribute__((section(".ttg_text"))) void refresh(void){if(ST.ctx)render((void*)(uintptr_t)ST.ctx);}
__attribute__((used,section(".ttg_aux"),noinline)) void ttg_prev(void){ST.page=ST.page?ST.page-1u:5u;ST.param=0;ST.edit=0;refresh();}
__attribute__((used,section(".ttg_aux"),noinline)) void ttg_next(void){ST.page=ST.page==5u?0u:ST.page+1u;ST.param=0;ST.edit=0;refresh();}
static __attribute__((section(".ttg_text"))) void turn(uint8_t up){
  uint8_t n=count();if(!ST.edit){ST.param=up?(ST.param+1u<n?ST.param+1u:0u):(ST.param?ST.param-1u:n-1u);refresh();return;}
  unsigned idx=vi();uint8_t c=code();if(is_bypass(c))return;if(is_mode(c))ST.values[idx]=ST.values[idx]>=50u?0u:100u;else if(up){if(ST.values[idx]<100u)ST.values[idx]++;}else if(ST.values[idx])ST.values[idx]--;apply_param(idx);refresh();
}
__attribute__((used,section(".ttg_text"),noinline)) void ttg_enc_up(void){turn(1u);}
__attribute__((used,section(".ttg_text"),noinline)) void ttg_enc_down(void){turn(0u);}
__attribute__((used,section(".ttg_text"),noinline)) void ttg_enc_press(void){
  unsigned idx=vi();uint8_t c=code();if(is_bypass(c)){uint8_t slot=page_slot[ST.page];ST.enabled[slot]=(uint8_t)!ST.enabled[slot];publish_enable();refresh();return;}if(is_mode(c)){ST.values[idx]=ST.values[idx]>=50u?0u:100u;apply_param(idx);refresh();return;}ST.edit^=1u;refresh();
}
