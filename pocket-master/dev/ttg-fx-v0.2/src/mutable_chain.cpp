/* TTG FX (MIT). This glue references original upstream Mutable headers (MIT). */
#include "mutable_chain.h"
#include <cmath>
#include <cstring>
#include "clouds/dsp/frame.h"
#include "clouds/dsp/fx/reverb.h"
#include "stmlib/dsp/filter.h"
#include "stmlib/dsp/dsp.h"
namespace {
const unsigned DELAY_LEN=16384u,CHUNK=32u;
struct TTGState {
  ttg_mutable_params p;
  stmlib::Svf f[2][2];
  clouds::Reverb rv;
  uint16_t rv_mem[16384];
  int16_t delay_mem[2][DELAY_LEN];
  float decim[2],delwet[2],lp[2];
  unsigned pos,phase;
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
void tick_delay(float *l,float *r){
  const float now[2]={*l,*r};
  if(!s.phase){s.decim[0]=now[0];s.decim[1]=now[1];s.phase=1;}
  else {
    unsigned len=(unsigned)limit(s.p.delay_ms*22.05f,2.0f,(float)DELAY_LEN-1);
    unsigned rd=(s.pos+DELAY_LEN-len)&(DELAY_LEN-1);
    float coef=0.01f+s.p.tone*0.58f;
    for(int c=0;c<2;c++){
      float v=s.delay_mem[c][rd]/32768.0f;
      s.lp[c]+=(v-s.lp[c])*coef;
      s.delwet[c]=s.lp[c];
      float w=(now[c]+s.decim[c])*0.5f+s.p.feedback*s.delwet[c];
      s.delay_mem[c][s.pos]=sample(stmlib::SoftClip(w*1.5f));
    }
    s.pos=(s.pos+1u)&(DELAY_LEN-1u);s.phase=0;
  }
  if(s.p.on[4]){
    *l=now[0]*(1-s.p.wet)+s.delwet[0]*s.p.wet;
    *r=now[1]*(1-s.p.wet)+s.delwet[1]*s.p.wet;
  }
}
}
extern "C" void ttg_mutable_init(void){
  std::memset(&s.p,0,sizeof(s.p));
  std::memset(s.rv_mem,0,sizeof(s.rv_mem));
  std::memset(s.delay_mem,0,sizeof(s.delay_mem));
  std::memset(s.decim,0,sizeof(s.decim));
  std::memset(s.delwet,0,sizeof(s.delwet));
  std::memset(s.lp,0,sizeof(s.lp));
  s.pos=s.phase=0;
  for(int i=0;i<2;i++)for(int c=0;c<2;c++)s.f[i][c].Init();
  s.rv.Init(s.rv_mem);
  ttg_mutable_params p={};
  p.gain=.25f;p.level=.8f;p.cutoff=.55f;p.resonance=.2f;
  p.delay_ms=260;p.feedback=.4f;p.wet=.25f;p.decay=.58f;
  p.diffusion=.625f;p.tone=.7f;
  for(int i=0;i<6;i++)p.on[i]=1;
  ttg_mutable_set_params(&p);
}
extern "C" void ttg_mutable_set_params(const ttg_mutable_params *p){
  if(!p)return;
  s.p=*p;s.p.gain=unit(p->gain);s.p.level=limit(p->level,0,1.5f);
  s.p.cutoff=unit(p->cutoff);s.p.resonance=unit(p->resonance);
  s.p.wet=unit(p->wet);s.p.feedback=limit(p->feedback,0,.9f);
  s.p.tone=unit(p->tone);s.p.decay=unit(p->decay);s.p.diffusion=unit(p->diffusion);
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
