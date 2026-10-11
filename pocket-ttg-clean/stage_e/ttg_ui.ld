ENTRY(ttg_page2_enter)
PROVIDE(STOCK_SET_PARAM = 0x00050348);
PROVIDE(STOCK_EVENT_PASS = 0x00052c44);
PROVIDE(STOCK_EVENT_DONE = 0x00052abc);
SECTIONS {
  .page_enter 0x00022e80 : SUBALIGN(2) { KEEP(*(.page_enter)) }
  .page_leave 0x000233e8 : SUBALIGN(2) { KEEP(*(.page_leave)) }
  .page_event 0x00023440 : SUBALIGN(2) { KEEP(*(.page_event)) }

  /* v0.7: exact stock event-code layer.  At 0x52c40 r0 is the 0x7Fxx event
     and the replaced stock instruction is `movi r2,#0x7f33`. */
  .patch_event_entry 0x00052c40 : SUBALIGN(2) { KEEP(*(.patch_event_entry)) }

  .ttg_aux  0x0010f314 : SUBALIGN(2) { KEEP(*(.ttg_aux)) KEEP(*(.ttg_rodata)) KEEP(*(.rodata*)) }
  .ttg_text 0x001101ae : SUBALIGN(2) { KEEP(*(.ttg_text)) KEEP(*(.text*)) }

  /DISCARD/ : {
    *(.patch_pre) *(.patch_next) *(.patch_enc_down) *(.patch_enc_up) *(.patch_enc_press)
    *(.comment) *(.note*) *(.eh_frame*) *(.data*) *(.bss*)
  }
}
ASSERT(SIZEOF(.page_enter)<=0x568,"replacement page enter exceeds stock page2_enter range")
ASSERT(SIZEOF(.page_leave)<=0x58,"replacement page leave exceeds stock page2_leave range")
ASSERT(SIZEOF(.page_event)<=0x230,"replacement page event exceeds stock page2_event range")
ASSERT(SIZEOF(.patch_event_entry)==4,"raw event entry patch size")
ASSERT(SIZEOF(.ttg_aux)<=0x342,"TTG aux+rodata exceeds audited old cave")
ASSERT(SIZEOF(.ttg_text)<=0x396,"TTG helper code exceeds audited large cave")
