/* MT4 P7.6.7 behavior regression: one original Mutable FxEngine
 * with two internal lines, no duplicate echo instance. MIT.
 */
#include "../src/mutable_chain.h"
#include <cassert>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <cstdint>
struct Window {double l,r;};
static Window windows[4];
static const uint32_t F=44100;
static void run(bool pingpong){
  ttg_mutable_init();
  ttg_mutable_params p={};
  p.level=1.0f;p.cutoff=.5f;
  p.delay_ms=250.0f;p.feedback=.9f;p.tone=1.0f;p.wet=1.0f;
  p.on[4]=1;p.pingpong=pingpong?1:0;
  ttg_mutable_set_params(&p);
  for(unsigned k=0;k<4;k++)windows[k]={0.0,0.0};
  static int16_t in[64],out[64];
  for(unsigned b=0;b<(F*12u/10u)/32u;b++){
    std::memset(in,0,sizeof(in));
    if(b==0)in[0]=in[1]=29000;
    ttg_mutable_process(in,out,32);
    for(unsigned i=0;i<32;i++){
      if(!pingpong)assert(out[2*i]==out[2*i+1]);
      uint32_t frame=b*32u+i;
      float t=(float)frame/(float)F;
      int j=(t>.22f&&t<.30f)?0:(t>.46f&&t<.55f)?1:
            (t>.71f&&t<.82f)?2:(t>.97f&&t<1.08f)?3:-1;
      if(j>=0){
        windows[j].l+=std::fabs((double)out[2*i]);
        windows[j].r+=std::fabs((double)out[2*i+1]);
      }
    }
  }
}
int main(){
  assert(ttg_mutable_static_bytes()<100000u);
  run(false);
  const double m1=windows[0].l,m2=windows[1].l;
  assert(m1>1000.0 && m2>100.0);
  for(unsigned k=0;k<4;k++)assert(windows[k].l==windows[k].r);
  run(true);
  assert(windows[0].l>1000.0&&windows[0].l>windows[0].r*12.0+10.0);
  assert(windows[1].r>100.0&&windows[1].r>windows[1].l*12.0+10.0);
  assert(windows[2].l>20.0&&windows[2].l>windows[2].r*12.0+10.0);
  assert(windows[3].r>5.0&&windows[3].r>windows[3].l*10.0+5.0);
  std::printf("PASS genuine Mutable FxEngine mono %.0f,%.0f; pingpong L/R/L/R %.0f,%.0f,%.0f,%.0f; RAM %zu bytes\n",
    m1,m2,windows[0].l,windows[1].r,windows[2].l,windows[3].r,
    ttg_mutable_static_bytes());
  return 0;
}
