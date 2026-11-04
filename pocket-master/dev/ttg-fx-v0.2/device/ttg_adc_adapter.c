/* GPL-3.0-only when linked to Molecular's GPL device HAL.
 * Uses an existing MVsilicon SDK ADC1 circular DMA API. Hardware input NOT proven. */
#include <stdint.h>
#include "adc_interface.h"
unsigned ttg_adc_read(void *ctx,uint32_t *frames,unsigned capacity){
 (void)ctx;
 if(!frames||capacity>32u)return 0;
 return (unsigned)AudioADC1DataGet(frames,(uint16_t)capacity);
}
