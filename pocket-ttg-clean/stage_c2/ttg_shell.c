#include <stdint.h>
#include <stddef.h>

typedef void (*fn_update)(void *);
typedef void *(*fn_nested)(void *,int,int);
typedef void (*fn_text)(void *,const char *);
#define UPDATE ((fn_update)(uintptr_t)0x22800u)
#define NEST   ((fn_nested)(uintptr_t)0x2c214u)
#define TEXT   ((fn_text)(uintptr_t)0x48cd0u)

typedef struct __attribute__((packed)){ uint8_t page,param,edit,value[66]; } S;
#define ST (*(volatile S *)(uintptr_t)0x2001bb40u)

/* Fixed-width compact tables: no terminators in parameter names. */
static const char page_name[24] __attribute__((section(".ttg_rodata")))=
  {'D','1',0,0,'F','1',0,0,'F','2',0,0,'D','2',0,0,'D','L','Y',0,'C','L','D',0};
static const char pname[60] __attribute__((section(".ttg_rodata")))=
  "DRVCOLLVLBYPIN CUTRESMODTONMIXTIMFBKPOSSIZDENTEXPITSPRREVFRZ";
/* offsets delimit 6 variable-length page rows in pmap */
static const uint8_t off[7] __attribute__((section(".ttg_rodata")))={0,4,10,16,20,25,36};
static const uint8_t pmap[36] __attribute__((section(".ttg_rodata")))={
  0,1,2,3,
  4,5,6,0,7,3,
  4,5,6,0,7,3,
  0,8,2,3,
  9,10,11,1,3,
  12,13,14,15,16,17,11,18,9,19,3
};

static inline unsigned vi(void){return (unsigned)ST.page*11u+ST.param;}
static inline uint8_t pc(void){return pmap[(unsigned)off[ST.page]+ST.param];}
static inline uint8_t count(void){return (uint8_t)(off[ST.page+1u]-off[ST.page]);}
static inline uint8_t disc(uint8_t c){return c==3u||c==7u||c==19u;}

static __attribute__((section(".ttg_text"))) void line(char *d){
  uint8_t c=pc(),v=ST.value[vi()]; const char *s=&pname[(unsigned)c*3u];
  d[0]=ST.edit?'>':' '; d[1]=s[0]; d[2]=s[1]; d[3]=s[2]; d[4]=' ';
  if(disc(c)){ d[5]=(c==7u)?(v?'H':'L'):(v?'1':'0'); d[6]=0; return; }
  if(v>99u)v=99u; d[5]=(char)('0'+v/10u); d[6]=(char)('0'+v%10u); d[7]=0;
}

__attribute__((used,section(".ttg_text"),noinline)) uint32_t ttg_page2_hook(void *ctx){
  UPDATE(ctx); if(!ctx)return 0; void *r=*(void **)((uint8_t*)ctx+8); if(!r)return 0;
  void *a=NEST(r,3,0),*b=NEST(r,3,1); char q[8]; line(q);
  if(a)TEXT(a,&page_name[(unsigned)ST.page*4u]); if(b)TEXT(b,q); return 0;
}
__attribute__((used,section(".ttg_text"),noinline)) void ttg_prev(void){ST.page=ST.page?ST.page-1u:5u;ST.param=0;ST.edit=0;}
__attribute__((used,section(".ttg_text"),noinline)) void ttg_next(void){ST.page=ST.page==5u?0u:ST.page+1u;ST.param=0;ST.edit=0;}

static __attribute__((section(".ttg_text"))) void turn(uint8_t up){
  uint8_t n=count();
  if(!ST.edit){ST.param=up?(ST.param+1u<n?ST.param+1u:0u):(ST.param?ST.param-1u:n-1u);return;}
  unsigned i=vi();uint8_t c=pc(),v=ST.value[i];
  if(disc(c)){ST.value[i]=(uint8_t)!v;return;}
  if(up){if(v<99u)v++;}else if(v)v--;ST.value[i]=v;
}
__attribute__((used,section(".ttg_text"),noinline)) void ttg_enc_up(void){turn(1);}
__attribute__((used,section(".ttg_text"),noinline)) void ttg_enc_down(void){turn(0);}
__attribute__((used,section(".ttg_text"),noinline)) void ttg_enc_press(void){
  unsigned i=vi();uint8_t c=pc();if(disc(c))ST.value[i]=(uint8_t)!ST.value[i];else ST.edit^=1u;
}
