/* Test-only: not for native build. */
#ifndef ADC_INTERFACE_H
#define ADC_INTERFACE_H
#include <stdint.h>
uint16_t AudioADC1DataGet(void *buffer,uint16_t len);
#endif
