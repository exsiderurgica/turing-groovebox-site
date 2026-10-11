#include <stdint.h>
#include <stddef.h>

typedef void (*fn_update)(void *ctx);
typedef void *(*fn_nested)(void *root,int group,int child);
typedef void (*fn_text)(void *obj,const char *text);
#define UPDATE ((fn_update)(uintptr_t)0x22800u)
#define NEST   ((fn_nested)(uintptr_t)0x2c214u)
#define TEXT   ((fn_text)(uintptr_t)0x48cd0u)

typedef struct __attribute__((packed)) {
  uint8_t page,param,edit;
  uint8_t value[66];
} S;
#define ST (*(volatile S *)(uintptr_t)0x2001bb40u)

/* 20 fixed 3-char parameter codes, four bytes each including NUL. */
static const char names[] __attribute__((section(".ttg_rodata"))) =
 "DRV\0COL\0LVL\0BYP\0IN \0CUT\0RES\0MOD\0TON\0MIX\0TIM\0FBK\0POS\0SIZ\0DEN\0TEX\0PIT\0SPR\0REV\0FRZ\0";
static const uint8_t cnt[6] __attribute__((section(".ttg_rodata")))={4,6,6,4,5,11};
/* six rows x eleven slots; unused slots are harmless zeros */
static const uint8_t map[66] __attribute__((section(".ttg_rodata")))={
  0,1,2,3,0,0,0,0,0,0,0,
  4,5,6,0,7,3,0,0,0,0,0,
  4,5,6,0,7,3,0,0,0,0,0,
  0,8,2,3,0,0,0,0,0,0,0,
  9,10,11,1,3,0,0,0,0,0,0,
  12,13,14,15,16,17,11,18,9,19,3
};

static inline unsigned idx(void){ return (unsigned)ST.page*11u+ST.param; }
static inline uint8_t code(void){ return map[idx()]; }
static inline uint8_t discrete(uint8_t c){ return (c==3u)||(c==7u)||(c==19u); }

static __attribute__((section(".ttg_text"))) void page_label(char *d){
  uint8_t p=ST.page; d[0]=(p==0||p==3)?'D':(p==4?'D':(p==5?'C':'F'));
  if(p<4){ d[1]=(p==0||p==1)?'1':'2'; d[2]=0; }
  else if(p==4){ d[1]='L'; d[2]='Y'; d[3]=0; }
  else { d[1]='L'; d[2]='D'; d[3]=0; }
}
static __attribute__((section(".ttg_text"))) void param_label(char *d){
  uint8_t c=code(),v=ST.value[idx()]; const char *s=&names[(unsigned)c*4u];
  d[0]=ST.edit?'>':' '; d[1]=s[0]; d[2]=s[1]; d[3]=s[2]; d[4]=' ';
  if(c==3u||c==19u){ d[5]=v?'O':'O'; d[6]=v?'N':'F'; d[7]=v?0:'F'; d[8]=0; return; }
  if(c==7u){ d[5]=v?'H':'L'; d[6]='P'; d[7]=0; return; }
  if(v>100u)v=100u; d[5]=(v==100u)?'1':'0'; if(v==100u)v=0;
  uint8_t t=0; while(v>=10u){v-=10u;t++;} d[6]=(char)('0'+t); d[7]=(char)('0'+v); d[8]=0;
}

__attribute__((used,section(".ttg_text"),noinline)) uint32_t ttg_page2_hook(void *ctx){
  UPDATE(ctx); if(!ctx)return 0; if(ST.page>5u)ST.page=0; if(ST.param>=cnt[ST.page])ST.param=0;
  void *root=*(void **)((uint8_t*)ctx+8); if(!root)return 0;
  void *a=NEST(root,3,0),*b=NEST(root,3,1); char p[4],q[9]; page_label(p); param_label(q);
  if(a)TEXT(a,p); if(b)TEXT(b,q); return 0;
}
__attribute__((used,section(".ttg_text"),noinline)) void ttg_prev(void){ ST.page=(ST.page==0u)?5u:(uint8_t)(ST.page-1u); ST.param=0; ST.edit=0; }
__attribute__((used,section(".ttg_text"),noinline)) void ttg_next(void){ ST.page=(ST.page>=5u)?0u:(uint8_t)(ST.page+1u); ST.param=0; ST.edit=0; }

static __attribute__((section(".ttg_text"))) void turn(uint8_t up){
  if(ST.page>5u)ST.page=0; uint8_t c=cnt[ST.page]; if(ST.param>=c)ST.param=0;
  if(!ST.edit){ ST.param=up?(uint8_t)((ST.param+1u>=c)?0u:ST.param+1u):(uint8_t)(ST.param?ST.param-1u:c-1u); return; }
  unsigned i=idx(); uint8_t n=map[i],v=ST.value[i]; if(discrete(n)){ST.value[i]=(uint8_t)!v;return;}
  if(up){if(v<100u)v++;}else if(v)v--; ST.value[i]=v;
}
__attribute__((used,section(".ttg_text"),noinline)) void ttg_enc_up(void){turn(1);}
__attribute__((used,section(".ttg_text"),noinline)) void ttg_enc_down(void){turn(0);}
__attribute__((used,section(".ttg_text"),noinline)) void ttg_enc_press(void){
  if(ST.page>5u)ST.page=0; if(ST.param>=cnt[ST.page])ST.param=0; unsigned i=idx(); uint8_t n=map[i];
  if(discrete(n))ST.value[i]=(uint8_t)!ST.value[i]; else ST.edit^=1u;
}
