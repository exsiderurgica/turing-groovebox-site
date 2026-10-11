.section .patch_event_entry,"ax",@progbits
.balign 2
.global ttg_patch_event_entry
ttg_patch_event_entry:
    j ttg_raw_event_gate

/* Runs in the stock global event dispatcher immediately after the event code
   has been loaded in r0.  It is deliberately a leaf: no call, no stack touch,
   no lp change.  r1 is scratch at the patched site and r15 is condition scratch.
   If TTG page2 is active (ctx high byte != 0), selected events are deposited at
   0x2001bb74 and replaced by stock's harmless 0x7b0e event. */
.section .ttg_text,"ax",@progbits
.balign 2
.global ttg_raw_event_gate
ttg_raw_event_gate:
    sethi $r1, #0x2001
    ori   $r1, $r1, #0xbb40
    lbi   $r15, [$r1 + #0x32]
    beqz  $r15, .Lpass

    movi  $r15, #0x7f36
    beq   $r0, $r15, .Lconsume
    movi  $r15, #0x7f37
    beq   $r0, $r15, .Lconsume
    movi  $r15, #0x7f5d
    beq   $r0, $r15, .Lconsume
    movi  $r15, #0x7f5e
    beq   $r0, $r15, .Lconsume
    movi  $r15, #0x7f46
    beq   $r0, $r15, .Lconsume

.Lpass:
    movi  $r1, #0x7805
    j     0x00052b04

.Lconsume:
    swi   $r0, [$r1 + #0x34]
    movi  $r0, #0x7b0e
    movi  $r1, #0x7805
    j     0x00052b04
