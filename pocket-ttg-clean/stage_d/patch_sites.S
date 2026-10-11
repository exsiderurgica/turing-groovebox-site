.section .patch_page2,"ax",@progbits
.balign 2
.global ttg_patch_page2
ttg_patch_page2:
    jal ttg_page2_hook
.section .patch_pre,"ax",@progbits
.balign 2
.global ttg_patch_pre
ttg_patch_pre:
    jal ttg_prev
.section .patch_next,"ax",@progbits
.balign 2
.global ttg_patch_next
ttg_patch_next:
    jal ttg_next
.section .patch_enc_down,"ax",@progbits
.balign 2
.global ttg_patch_enc_down
ttg_patch_enc_down:
    jal ttg_enc_down
.section .patch_enc_up,"ax",@progbits
.balign 2
.global ttg_patch_enc_up
ttg_patch_enc_up:
    jal ttg_enc_up
.section .patch_enc_press,"ax",@progbits
.balign 2
.global ttg_patch_enc_press
ttg_patch_enc_press:
    jal ttg_enc_press

/* Soft ABI wrapper: a0=slot, a1=param, a2=dirty, a3=IEEE754 bits.
   Stock setter 0x50348 forwards a0/a1/a2 and expects the value in fs0. */
.section .ttg_text,"ax",@progbits
.balign 2
.global ttg_set_param_raw
ttg_set_param_raw:
    fmtsr $a3, $fs0
    j 0x00050348
