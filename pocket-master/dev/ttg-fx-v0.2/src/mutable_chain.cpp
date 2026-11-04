/* TTG FX (MIT). This glue references original upstream Mutable headers (MIT). */
#include "mutable_chain.h"
#include <cmath>
#include <cstring>
#include "clouds/dsp/frame.h"
#include "clouds/dsp/fx/reverb.h"
#include "clouds/dsp/fx/fx_engine.h"
#include "stmlib/dsp/filter.h"
#include "stmlib/dsp/dsp.h"
namespace {
const unsigned DELAY_LEN=16384u,CHUNK=32u;
const unsigned DELAY_RATE=8000u, AUDIO_RATE=44100u;
/* MT4 P7.6.7: one Mutable FxEngine, two 8188-sample line segments. */
using MutableEcho=clouds::FxEngine<DELAY_LEN,clouds::FORMAT_16_BIT>;
using EchoMemory=MutableEcho::Reserve<8188,MutableEcho::Reserve<8188>>;
using EchoTapL=MutableEcho::DelayLine<EchoMemory,0>;
using EchoTapR=MutableEcho::DelayLine<EchoMemory,1>;
static_assert(EchoTapR::base+EchoTapR::length<DELAY_LEN,
              "Mutable delay taps exceed 16384-sample pool");
struct TTGState {
  ttg_mutable_params p;
  stmlib::Svf f[2][2];
  clouds::Reverb rv;
  uint16_t rv_mem[16384];
  /* Exactly 32 KiB for two Mutable echo taps. No second FxEngine. */
  uint16_t delay_mem[DELAY_LEN];
  MutableEcho echo;
  float delay_prev[2],delwet[2],lp[2];
  float decim_acc;
  unsigned decim_count,phase;
};
TTGState s;
float limit(float x,float a,float b){return x<a?a:x>b?b:x;}
float unit(float x){return limit(x,0,1);}
int16_t sample(float x){
  float y=limit(x,-1,1)*32767;
  return static_cast<int16_t>(y>=0?y+0.5f:y-0.5f);
}
float drive(float x){
  float g=1.0f+14.0f*s.p.gain;
  return stmlib::SoftClip(x*g)*s.p.level;
}
float filt(int stage,int channel,float x){
  if(!s.p.on[stage+1])return x;
  float y=s.f[stage][channel].Process<stmlib::FILTER_MODE_LOW_PASS>(x);
  return stmlib::SoftClip(y);
}
/* Shared MT4 P7.6.7 algorithm: Context::Interpolate + Write feedback.
 * The sample-rate adapter is Pocket-specific: exactly 8k ticks/second,
 * not the v0.2 half-rate 22.05k custom tape/echo. */
void echo_step(float input){
  MutableEcho::Context ctx;
  EchoTapL tap_l;
  EchoTapR tap_r;
  s.echo.Start(&ctx); /* ONCE per 8k frame */
  const float target=limit(s.p.delay_ms*8.0f,2.0f,8186.0f);
  const float tone=.008f+.96f*unit(s.p.tone);
  const float feedback=limit(s.p.feedback,0.0f,.92f)*.82f;
  ctx.Load(0.0f);
  ctx.Interpolate(tap_l,target,1.0f);
  float wet_l=0.0f;ctx.Write(wet_l);
  s.lp[0]+=(wet_l-s.lp[0])*tone;
  float wet_r=0.0f;
  if(s.p.pingpong){
    ctx.Load(0.0f);
    ctx.Interpolate(tap_r,target,1.0f);
    ctx.Write(wet_r);
    s.lp[1]+=(wet_r-s.lp[1])*tone;
    ctx.Load(limit(input+s.lp[1]*feedback,-.98f,.98f));
    ctx.Write(tap_l,0.0f);
    ctx.Load(limit(s.lp[0]*feedback,-.98f,.98f));
    ctx.Write(tap_r,0.0f);
  }else{
    ctx.Load(limit(input+s.lp[0]*feedback,-.98f,.98f));
    ctx.Write(tap_l,0.0f);
    ctx.Load(0.0f);
    ctx.Write(tap_r,0.0f); /* discard stale right-channel pingpong signal */
    s.lp[1]=0.0f;
    wet_r=wet_l; /* MT4 centered mono */
  }
  s.delay_prev[0]=s.delwet[0];
  s.delay_prev[1]=s.delwet[1];
  s.delwet[0]=wet_l;
  s.delwet[1]=wet_r;
}
void tick_delay(float *l,float *r){
  const float now_l=*l,now_r=*r;
  s.decim_acc+=(now_l+now_r)*.5f;
  ++s.decim_count;
  s.phase+=DELAY_RATE;
  if(s.phase>=AUDIO_RATE){
    s.phase-=AUDIO_RATE;
    const float input=s.decim_acc/(float)s.decim_count;
    s.decim_acc=0.0f;s.decim_count=0;
    echo_step(input);
  }
  if(s.p.on[4]){
    float mix=unit(s.p.wet);
    *l=now_l*(1-mix)+s.delwet[0]*mix;
    *r=now_r*(1-mix)+s.delwet[1]*mix;
  }
}
}
extern "C" void ttg_mutable_init(void){
  std::memset(&s.p,0,sizeof(s.p));
  std::memset(s.rv_mem,0,sizeof(s.rv_mem));
  s.echo.Init(s.delay_mem); /* only from init, NEVER from an audio IRQ */
  std::memset(s.delay_prev,0,sizeof(s.delay_prev));
  std::memset(s.delwet,0,sizeof(s.delwet));
  std::memset(s.lp,0,sizeof(s.lp));
  s.decim_acc=0.0f;s.decim_count=s.phase=0;
  for(int i=0;i<2;i++)for(int c=0;c<2;c++)s.f[i][c].Init();
  s.rv.Init(s.rv_mem);
  ttg_mutable_params p={};
  p.gain=.25f;p.level=.8f;p.cutoff=.55f;p.resonance=.2f;
  p.delay_ms=260;p.feedback=.4f;p.wet=.25f;p.decay=.58f;
  p.diffusion=.625f;p.tone=.7f;p.pingpong=0;
  for(int i=0;i<6;i++)p.on[i]=1;
  ttg_mutable_set_params(&p);
}
extern "C" void ttg_mutable_set_params(const ttg_mutable_params *p){
  if(!p)return;
  s.p=*p;s.p.gain=unit(p->gain);s.p.level=limit(p->level,0,1.5f);
  s.p.cutoff=unit(p->cutoff);s.p.resonance=unit(p->resonance);
  s.p.wet=unit(p->wet);s.p.feedback=limit(p->feedback,0,.9f);
  s.p.tone=unit(p->tone);s.p.decay=unit(p->decay);s.p.diffusion=unit(p->diffusion);
  s.p.pingpong=(uint8_t)(p->pingpong?1:0);
  float fc=20*powf(800,s.p.cutoff),q=.7f+s.p.resonance*9;
  for(int i=0;i<2;i++)for(int c=0;c<2;c++)
    s.f[i][c].set_f_q<stmlib::FREQUENCY_FAST>(fc/44100,q);
  s.rv.set_amount(.65f);
  s.rv.set_input_gain(.25f);
  s.rv.set_time(.2f+.73f*s.p.decay);
  s.rv.set_diffusion(.25f+.65f*s.p.diffusion);
  s.rv.set_lp(.1f+.85f*s.p.tone);
}
extern "C" void ttg_mutable_process(const int16_t *in,int16_t *out,size_t count){
  if(!in||!out)return;
  size_t done=0;
  while(done<count){
    clouds::FloatFrame frame[CHUNK];
    float dry[CHUNK][2];
    unsigned n=(unsigned)(count-done<CHUNK?count-done:CHUNK);
    for(unsigned i=0;i<n;i++){
      float l=in[2*(done+i)]/32768.0f,r=in[2*(done+i)+1]/32768.0f;
      if(s.p.on[0]){l=drive(l);r=drive(r);}
      l=filt(0,0,l);r=filt(0,1,r);
      l=filt(1,0,l);r=filt(1,1,r);
      if(s.p.on[3]){l=drive(l);r=drive(r);}
      tick_delay(&l,&r);
      dry[i][0]=l;dry[i][1]=r;frame[i].l=l;frame[i].r=r;
    }
    if(s.p.on[5])s.rv.Process(frame,n);
    for(unsigned i=0;i<n;i++){
      float wet=s.p.on[5]?s.p.wet:0;
      out[2*(done+i)]=sample(dry[i][0]*(1-wet)+frame[i].l*wet);
      out[2*(done+i)+1]=sample(dry[i][1]*(1-wet)+frame[i].r*wet);
    }
    done+=n;
  }
}
extern "C" size_t ttg_mutable_static_bytes(void){return sizeof(s);}
