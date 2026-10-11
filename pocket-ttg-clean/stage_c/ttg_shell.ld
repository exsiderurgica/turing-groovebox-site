ENTRY(ttg_page2_hook)
SECTIONS
{
  /* Region b is loaded by Ghidra/runtime at 0x10000. */
  .patch_page2 0x000235b6 : SUBALIGN(2) { KEEP(*(.patch_page2)) }
  .patch_pre   0x00052e48 : SUBALIGN(2) { KEEP(*(.patch_pre)) }
  .patch_next  0x00052ee4 : SUBALIGN(2) { KEEP(*(.patch_next)) }

  /* Verified all-zero stock cave: raw b+0xff313..0xff655,
   * runtime 0x10f313..0x10f655. Start aligned at 0x10f314. */
  .ttg_hook 0x0010f314 : SUBALIGN(2)
  {
    KEEP(*(.ttg_text))
    KEEP(*(.ttg_rodata))
    KEEP(*(.rodata*))
  }

  /DISCARD/ : { *(.comment) *(.note*) *(.eh_frame*) *(.data*) *(.bss*) }
}
ASSERT(SIZEOF(.patch_page2) == 4, "page2 patch must be one 32-bit JAL")
ASSERT(SIZEOF(.patch_pre)   == 4, "PRE patch must be one 32-bit JAL")
ASSERT(SIZEOF(.patch_next)  == 4, "NEXT patch must be one 32-bit JAL")
ASSERT(SIZEOF(.ttg_hook) <= 0x342, "TTG shell exceeds verified aligned zero cave")
