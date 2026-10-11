#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"; SRC="$ROOT/generated_arm_src"; OUT="$ROOT/build/arm"; mkdir -p "$SRC" "$OUT"
cat > "$SRC/d1.c" <<'SRC_EOF'
#include <stdint.h>
#define MAGIC 0x44315454u
#define A 0.0045f
typedef struct {float drive,color,level,dc[2];uint32_t magic;} State;__attribute__((section(".pfx_state"),used))static State S={0,.45f,.80f,{0,0},MAGIC};static float cl(float x){return x<0?0:(x>1?1:x);}static float ab(float x){return x<0?-x:x;}static float p(const float*q,uint32_t i,float d){return q?cl(q[i]*.01f):d;}static float sat(float x,float c){float a=ab(x),k=.55f+c*1.25f;if(a>2)return x<0?-1:1;x*=k;return x/(1+ab(x));}static float lim(float x){return x>1?1:(x<-1?-1:x);}__attribute__((section(".pfx_init"),used,noinline))void ttg_d1_init(const float*q){S.drive=p(q,0,0);S.color=p(q,1,.45f);S.level=p(q,2,.8f);S.dc[0]=S.dc[1]=0;S.magic=MAGIC;}__attribute__((section(".pfx_process"),used,noinline))void ttg_d1_process(float*st,uint32_t n,const float*q,uint32_t r3,const uint8_t*en){(void)r3;if(!st||!n||(en&&!*en))return;if(S.magic!=MAGIC)ttg_d1_init(q);float td=p(q,0,0),tc=p(q,1,.45f),tl=p(q,2,.8f);for(uint32_t i=0;i<n;i++){S.drive+=(td-S.drive)*A;S.color+=(tc-S.color)*A;S.level+=(tl-S.level)*A;for(uint32_t c=0;c<2;c++){float x=st[2*i+c],g=1+S.drive*S.drive*18,hp=x-S.dc[c];S.dc[c]+=.02f*hp;float pre=x+hp*(S.color-.5f)*.9f,w=sat(pre*g,S.color),m=.08f+S.drive*.84f,y=x*(1-m)+w*m,lv=S.level<=.8f?S.level*1.25f:1+(S.level-.8f)*1.5f;st[2*i+c]=lim(y*lv);}}}
SRC_EOF
cat > "$SRC/d2.c" <<'SRC_EOF'
#include <stdint.h>
#define MAGIC 0x44325454u
#define A 0.0045f
typedef struct{float drive,tone,level,lp[2];uint32_t magic;}State;__attribute__((section(".pfx_state"),used))static State S={0,.55f,.8f,{0,0},MAGIC};static float cl(float x){return x<0?0:(x>1?1:x);}static float ab(float x){return x<0?-x:x;}static float p(const float*q,uint32_t i,float d){return q?cl(q[i]*.01f):d;}static float lim(float x){return x>1?1:(x<-1?-1:x);}static float sat(float x){return x/(1+ab(x));}__attribute__((section(".pfx_init"),used,noinline))void ttg_d2_init(const float*q){S.drive=p(q,0,0);S.tone=p(q,1,.55f);S.level=p(q,2,.8f);S.lp[0]=S.lp[1]=0;S.magic=MAGIC;}__attribute__((section(".pfx_process"),used,noinline))void ttg_d2_process(float*st,uint32_t n,const float*q,uint32_t r3,const uint8_t*en){(void)r3;if(!st||!n||(en&&!*en))return;if(S.magic!=MAGIC)ttg_d2_init(q);float td=p(q,0,0),tt=p(q,1,.55f),tl=p(q,2,.8f);for(uint32_t i=0;i<n;i++){S.drive+=(td-S.drive)*A;S.tone+=(tt-S.tone)*A;S.level+=(tl-S.level)*A;float a=.015f+S.tone*.30f;for(uint32_t c=0;c<2;c++){float x=st[2*i+c],g=1+S.drive*S.drive*22,w=sat(x*g);S.lp[c]+=a*(w-S.lp[c]);float bright=w+(w-S.lp[c])*.7f,y=S.lp[c]*(1-S.tone)+bright*S.tone,m=.12f+S.drive*.80f,lv=S.level<=.8f?S.level*1.25f:1+(S.level-.8f)*1.5f;st[2*i+c]=lim((x*(1-m)+y*m)*lv);}}}
SRC_EOF
cat > "$SRC/f1_bridge.c" <<'SRC_EOF'
#include <stdint.h>
#define C 0x10003264u
#define E 0x10003278u
#define M 0x1000327Cu
#define A 0x2800064Cu
__attribute__((section(".text.ttg"),used,noinline))void ttg_f1_bridge(void*a,uint32_t b,const float*p,uint32_t d,const uint8_t*en){(void)a;(void)b;(void)d;volatile float*q=(volatile float*)C;static const float z[5]={80,70,15,0,0};for(uint32_t i=0;i<5;i++)q[i]=p?p[i]:z[i];*(volatile uint32_t*)E=(en&&*en)?1:0;*(volatile uint32_t*)M=0x46314331u;*(volatile uint32_t*)A=0;}
SRC_EOF
cat > "$SRC/filters.c" <<'SRC_EOF'
#include <stdint.h>
#define FC ((volatile float*)0x10003264u)
#define FE (*(volatile uint32_t*)0x10003278u)
#define FM (*(volatile uint32_t*)0x1000327Cu)
#define MAGIC 0x46494c32u
typedef struct{float s1[4],s2[4],f1l,f1c,f1r,f1d,f2l,f2c,f2r,f2d;uint32_t magic;}State;__attribute__((section(".pfx_state"),used))static State S={{0},{0},.8f,.7f,.15f,0,.8f,.7f,.15f,0,MAGIC};static float cl(float x){return x<0?0:(x>1?1:x);}static float ab(float x){return x<0?-x:x;}static float p(const float*q,uint32_t i,float d){return q?cl(q[i]*.01f):d;}static float raw(float x,float d){return x==x?cl(x*.01f):d;}static float sat(float x){return x/(1+ab(x));}static float lim(float x){return x>1?1:(x<-1?-1:x);}static float input(float x,float l){float g=l<=.8f?l*1.25f:1+(l-.8f)*6;return g<=1?x*g:sat(x*g);}static float core(float x,float cut,float res,float drv,uint32_t hp,uint32_t ch,uint32_t w){float g=.006f+cl(cut)*.62f,k=1.98f-cl(res)*1.90f;if(k<.06f)k=.06f;x=sat(x*(1+drv*drv*7));uint32_t j=w*2+ch;float a1=1/(1+g*(g+k)),a2=g*a1,a3=g*a2,v3=x-S.s2[j],v1=a1*S.s1[j]+a2*v3,v2=S.s2[j]+a2*S.s1[j]+a3*v3;S.s1[j]=2*v1-S.s1[j];S.s2[j]=2*v2-S.s2[j];float y=hp?(x-k*v1-v2):v2;if(res>.55f)y=sat(y*(1+(res-.55f)*1.1f));return y;}__attribute__((section(".pfx_init"),used,noinline))void ttg_filters_init(const float*q){for(int i=0;i<4;i++)S.s1[i]=S.s2[i]=0;S.f2l=p(q,0,.8f);S.f2c=p(q,1,.7f);S.f2r=p(q,2,.15f);S.f2d=p(q,3,0);S.magic=MAGIC;}__attribute__((section(".pfx_process"),used,noinline))void ttg_filters_process(float*st,uint32_t n,const float*q,uint32_t r3,const uint8_t*en){(void)r3;if(!st||!n)return;if(S.magic!=MAGIC)ttg_filters_init(q);uint32_t f1=(FM==0x46314331u&&FE),f2=(!en||*en);if(!f1&&!f2)return;float l1=.8f,c1=.7f,r1=.15f,d1=0,l2=p(q,0,.8f),c2=p(q,1,.7f),r2=p(q,2,.15f),d2=p(q,3,0);uint32_t hp1=0,hp2=(q&&q[4]>=50);if(f1){l1=raw(FC[0],.8f);c1=raw(FC[1],.7f);r1=raw(FC[2],.15f);d1=raw(FC[3],0);hp1=FC[4]>=50;}for(uint32_t i=0;i<n;i++){if(f1){S.f1l+=(l1-S.f1l)*.0035f;S.f1c+=(c1-S.f1c)*.006f;S.f1r+=(r1-S.f1r)*.0035f;S.f1d+=(d1-S.f1d)*.0035f;}if(f2){S.f2l+=(l2-S.f2l)*.0035f;S.f2c+=(c2-S.f2c)*.006f;S.f2r+=(r2-S.f2r)*.0035f;S.f2d+=(d2-S.f2d)*.0035f;}for(uint32_t c=0;c<2;c++){float y=st[2*i+c];if(f1)y=core(input(y,S.f1l),S.f1c,S.f1r,S.f1d,hp1,c,0);if(f2)y=core(input(y,S.f2l),S.f2c,S.f2r,S.f2d,hp2,c,1);st[2*i+c]=lim(y);}}}
SRC_EOF
cat > "$SRC/cloud.c" <<'SRC_EOF'
#include <stdint.h>
typedef void(*I)(void*);typedef void(*P)(float*,uint32_t,void*,uint32_t,uint32_t);#define ENG ((volatile uint32_t*)0x10018530u)
#define SI (*(volatile I*)0x10018800u)
#define SP (*(volatile P*)0x10018804u)
static uint32_t u(float x){union{float f;uint32_t u;}v;v.f=x;return v.u;}static float g(const float*p,unsigned i,float d){return p?p[i]:d;}static void map(const float*p){float m=g(p,0,50),s=g(p,1,55),t=g(p,2,50),w=g(p,3,45);ENG[0]=u(m);ENG[1]=u(25+s*.70f);ENG[2]=u(t);ENG[3]=u(85);ENG[4]=u(20);ENG[5]=u(16);ENG[6]=u(20);ENG[7]=ENG[8]=0;ENG[9]=u(5+w*.9f);ENG[10]=u(20);}__attribute__((section(".pfx_init"),used,noinline))void ttg_cloud_init(const float*p){map(p);SI((void*)ENG);}__attribute__((section(".pfx_process"),used,noinline))void ttg_cloud_process(float*st,uint32_t n,const float*p,uint32_t x,uint32_t a4,uint32_t a5){(void)x;map(p);SP(st,n,(void*)ENG,a4,a5);}
SRC_EOF
cat > "$SRC/d1.ld" <<'LD'
ENTRY(ttg_d1_init)
SECTIONS { . = 0x10001000; .text : ALIGN(4) { KEEP(*(.pfx_init)) KEEP(*(.pfx_process)) *(.text*) *(.rodata*) } .data : ALIGN(4) { *(.data*) KEEP(*(.pfx_state)) } /DISCARD/ : { *(.ARM.exidx*) *(.ARM.extab*) *(.comment*) *(.ARM.attributes*) *(.note*) } }
LD
cat > "$SRC/d2.ld" <<'LD'
ENTRY(ttg_d2_init)
SECTIONS { . = 0x10007c50; .text : ALIGN(4) { KEEP(*(.pfx_init)) KEEP(*(.pfx_process)) *(.text*) *(.rodata*) } .data : ALIGN(4) { *(.data*) KEEP(*(.pfx_state)) } /DISCARD/ : { *(.ARM.exidx*) *(.ARM.extab*) *(.comment*) *(.ARM.attributes*) *(.note*) } }
LD
cat > "$SRC/f1.ld" <<'LD'
ENTRY(ttg_f1_bridge)
SECTIONS { . = 0x10002a7c; .text : ALIGN(2) { KEEP(*(.text.ttg)) *(.text*) *(.rodata*) } /DISCARD/ : { *(.ARM.exidx*) *(.ARM.extab*) *(.comment*) *(.ARM.attributes*) *(.note*) *(.data*) *(.bss*) } }
LD
cat > "$SRC/f2.ld" <<'LD'
ENTRY(ttg_filters_init)
SECTIONS { . = 0x10009000; .text : ALIGN(4) { KEEP(*(.pfx_init)) KEEP(*(.pfx_process)) *(.text*) *(.rodata*) } .data : ALIGN(4) { *(.data*) KEEP(*(.pfx_state)) } /DISCARD/ : { *(.ARM.exidx*) *(.ARM.extab*) *(.comment*) *(.ARM.attributes*) *(.note*) } }
LD
cat > "$SRC/cloud.ld" <<'LD'
ENTRY(ttg_cloud_init)
SECTIONS { . = 0x10018400; .text : ALIGN(4) { KEEP(*(.pfx_init)) KEEP(*(.pfx_process)) *(.text*) *(.rodata*) } /DISCARD/ : { *(.ARM.exidx*) *(.ARM.extab*) *(.comment*) *(.ARM.attributes*) *(.note*) } }
LD
CC=${CC:-clang};OBJCOPY=${OBJCOPY:-llvm-objcopy};OBJDUMP=${OBJDUMP:-llvm-objdump};CFLAGS='--target=armv7em-none-eabi -mcpu=cortex-m4 -mthumb -mfpu=fpv4-sp-d16 -mfloat-abi=hard -Oz -ffreestanding -fno-builtin -fno-stack-protector -fno-unwind-tables -fno-asynchronous-unwind-tables -fdata-sections -ffunction-sections -nostdlib -Wl,--gc-sections -fuse-ld=lld'
for x in d1 d2 f1 f2 cloud;do case $x in d1)c=d1.c;;d2)c=d2.c;;f1)c=f1_bridge.c;;f2)c=filters.c;;cloud)c=cloud.c;;esac;$CC $CFLAGS -Wl,-T,"$SRC/$x.ld" "$SRC/$c" -o "$OUT/$x.elf";$OBJCOPY -O binary "$OUT/$x.elf" "$OUT/$x.bin";$OBJDUMP -dr "$OUT/$x.elf" > "$OUT/$x.dis";nm -n "$OUT/$x.elf" > "$OUT/$x.nm";readelf -r "$OUT/$x.elf" > "$OUT/$x.reloc";if grep -q ' R_' "$OUT/$x.reloc";then exit 1;fi;done
python3 - <<'PY'
from pathlib import Path
b=Path('pocket-ttg-clean/stage_d/build/arm');lims={'d1':0x930,'d2':0xff4,'f1':0x134,'f2':0x1484,'cloud':0x130}
for n,l in lims.items():s=(b/f'{n}.bin').stat().st_size;print(n,s,l);assert s<=l
PY
