.section .patch_event_entry,"ax",@progbits
.balign 2
.global ttg_patch_event_entry
ttg_patch_event_entry:
    j ttg_raw_event_gate

/* v0.8 UNIVERSAL EVENT GATE
   Stock V1.3.3 runtime 0x52afc loads the dequeued message code into r0.
   We replace the immediately following instruction at 0x52b00, before any
   Sonicake page/state filtering.  Therefore PRE/NEXT and MAIN_VOL events are
   visible even when our custom page2 root is active.

   If TTG page2 is inactive, restore the displaced stock instruction
       movi r1,#0x7805
   and resume at 0x52b04.

   If TTG page2 is active, selected hardware events call our already compiled
   TTG actions directly in the same main GUI/event task and are then consumed
   by jumping to the stock next-message continuation at 0x52abc. */
.section .ttg_text,"ax",@progbits
.balign 2
.global ttg_raw_event_gate
ttg_raw_event_gate:
    sethi $r15, #0x2001b
    ori   $r15, $r15, #0xb40
    /* ST.ctx occupies offsets 0x2f..0x32.  Its high byte is non-zero while
       our page is active and is cleared by ttg_page2_leave(). */
    lbi   $r2, [$r15 + #0x32]
    beqz  $r2, .Lpass

    movi  $r2, #0x7f36
    beq   $r0, $r2, .Lprev
    movi  $r2, #0x7f37
    beq   $r0, $r2, .Lnext
    movi  $r2, #0x7f5d
    beq   $r0, $r2, .Lup
    movi  $r2, #0x7f5e
    beq   $r0, $r2, .Ldown
    movi  $r2, #0x7f46
    beq   $r0, $r2, .Lpress

.Lpass:
    movi  $r1, #0x7805
    j     STOCK_EVENT_PASS

.Lprev:
    jal   ttg_prev
    j     STOCK_EVENT_DONE
.Lnext:
    jal   ttg_next
    j     STOCK_EVENT_DONE
.Lup:
    jal   ttg_enc_up
    j     STOCK_EVENT_DONE
.Ldown:
    jal   ttg_enc_down
    j     STOCK_EVENT_DONE
.Lpress:
    /* 0x7f46 is the verified long VALUE/menu event.  Owning it suppresses
       the Sonicake Effects/Shutdown menu and gives TTG an edit/toggle input.
       The separate short-click code is still being reverse engineered. */
    jal   ttg_enc_press
    j     STOCK_EVENT_DONE
