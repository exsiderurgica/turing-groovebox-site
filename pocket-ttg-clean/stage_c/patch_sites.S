.section .patch_page2,"ax",@progbits
.balign 2
.global ttg_patch_page2
.type ttg_patch_page2,@function
ttg_patch_page2:
    jal ttg_page2_hook
.size ttg_patch_page2, .-ttg_patch_page2

.section .patch_pre,"ax",@progbits
.balign 2
.global ttg_patch_pre
.type ttg_patch_pre,@function
ttg_patch_pre:
    jal ttg_prev
.size ttg_patch_pre, .-ttg_patch_pre

.section .patch_next,"ax",@progbits
.balign 2
.global ttg_patch_next
.type ttg_patch_next,@function
ttg_patch_next:
    jal ttg_next
.size ttg_patch_next, .-ttg_patch_next
