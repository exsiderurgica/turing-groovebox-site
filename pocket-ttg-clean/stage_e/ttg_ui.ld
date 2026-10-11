ENTRY(ttg_page2_enter)
PROVIDE(STOCK_SET_PARAM = 0x00050348);
SECTIONS {
  .page_enter 0x00022e80 : SUBALIGN(2) { KEEP(*(.page_enter)) }
  .page_leave 0x000233e8 : SUBALIGN(2) { KEEP(*(.page_leave)) }
  .page_event 0x00023440 : SUBALIGN(2) { KEEP(*(.page_event)) }

  .patch_pre       0x00052e48 : SUBALIGN(2) { KEEP(*(.patch_pre)) }
  .patch_next      0x00052ee4 : SUBALIGN(2) { KEEP(*(.patch_next)) }
  .patch_enc_down  0x00053a54 : SUBALIGN(2) { KEEP(*(.patch_enc_down)) }
  .patch_enc_up    0x00053c88 : SUBALIGN(2) { KEEP(*(.patch_enc_up)) }
  .patch_enc_press 0x00053cd4 : SUBALIGN(2) { KEEP(*(.patch_enc_press)) }

  .ttg_aux  0x0010f314 : SUBALIGN(2) { KEEP(*(.ttg_aux)) KEEP(*(.ttg_rodata)) KEEP(*(.rodata*)) }
  .ttg_text 0x001101ae : SUBALIGN(2) { KEEP(*(.ttg_text)) KEEP(*(.text*)) }

  /DISCARD/ : { *(.comment) *(.note*) *(.eh_frame*) *(.data*) *(.bss*) }
}
ASSERT(SIZEOF(.page_enter)<=0x568,"replacement page enter exceeds stock page2_enter range")
ASSERT(SIZEOF(.page_leave)<=0x58,"replacement page leave exceeds stock page2_leave range")
ASSERT(SIZEOF(.page_event)<=0x230,"replacement page event exceeds stock page2_event range")
ASSERT(SIZEOF(.patch_pre)==4,"pre patch size")
ASSERT(SIZEOF(.patch_next)==4,"next patch size")
ASSERT(SIZEOF(.patch_enc_down)==4,"enc down patch size")
ASSERT(SIZEOF(.patch_enc_up)==4,"enc up patch size")
ASSERT(SIZEOF(.patch_enc_press)==4,"enc press patch size")
ASSERT(SIZEOF(.ttg_aux)<=0x342,"TTG aux+rodata exceeds audited old cave")
ASSERT(SIZEOF(.ttg_text)<=0x396,"TTG helper code exceeds audited large cave")
