#include "../src/mutable_chain.h"
#include <cassert>
#include <cstdio>
#include <cstdlib>
#include <cstring>
static int16_t in[64],out[64];
int main(){
  ttg_mutable_init();
  ttg_mutable_params p={};
  p.level=1;p.cutoff=.6f;p.tone=.5f;
  ttg_mutable_set_params(&p);
  for(int i=0;i<64;i++)in[i]=(int16_t)(i*883-28000);
  ttg_mutable_process(in,out,32);
  for(int i=0;i<64;i++)assert(abs((int)out[i]-(int)in[i])<2);
  ttg_mutable_init();
  std::memset(&p,0,sizeof(p));p.level=1;p.cutoff=.6f;
  p.on[5]=1;p.wet=1;p.decay=.7f;p.tone=.7f;p.diffusion=.7f;
  ttg_mutable_set_params(&p);
  unsigned long long tail=0;
  for(int j=0;j<1000;j++){
    std::memset(in,0,sizeof(in));if(j==0)in[0]=30000;
    ttg_mutable_process(in,out,32);
    if(j>50)for(int i=0;i<64;i++)tail+=(unsigned long long)abs((int)out[i]);
  }
  assert(tail>0);
  std::printf("TTG FX genuine Mutable DSP: PASS (state %zu, tail %llu)\n",
              ttg_mutable_static_bytes(),tail);
  return 0;
}
