.section .patch_event_entry,"ax",@progbits
.balign 2
.global ttg_patch_event_entry
ttg_patch_event_entry:
    j ttg_raw_event_gate

/* v0.7 VERIFIED HOOK LAYER
   This hook replaces stock runtime 0x52c40, exactly where r0 is already the
   real 0x7Fxx UI/hardware event code.  r1 must be preserved because stock uses
   it later; r2 is safe because the replaced instruction itself was
   `movi r2,#0x7f33`; r15 is condition scratch.

   Active test: the high byte of ST.ctx at ST+0x32 is nonzero while our page2
   is active.  Selected events are queued at 0x2001bb74 and jump directly to
   the stock handled-event continuation.  Unselected events restore the
   replaced r2 instruction and resume at 0x52c44. */
.section .ttg_text,"ax",@progbits
.balign 2
.global ttg_raw_event_gate
ttg_raw_event_gate:
    sethi $r15, #0x2001b
    ori   $r15, $r15, #0xb40
    lbi   $r2, [$r15 + #0x32]
    beqz  $r2, .Lpass

    movi  $r2, #0x7f36
    beq   $r0, $r2, .Lconsume
    movi  $r2, #0x7f37
    beq   $r0, $r2, .Lconsume
    movi  $r2, #0x7f5d
    beq   $r0, $r2, .Lconsume
    movi  $r2, #0x7f5e
    beq   $r0, $r2, .Lconsume
    movi  $r2, #0x7f46
    beq   $r0, $r2, .Lconsume

.Lpass:
    movi  $r2, #0x7f33
    j     STOCK_EVENT_PASS

.Lconsume:
    swi   $r0, [$r15 + #0x34]
    j     STOCK_EVENT_DONE
