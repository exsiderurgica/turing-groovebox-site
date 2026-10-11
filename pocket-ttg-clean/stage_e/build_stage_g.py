#!/usr/bin/env python3
"""v0.8 builder.

Reuses the already audited stock-rooted v0.7 builder, but moves the only global
input patch from the page-state-dependent 0x52c40 site to the universal
post-dequeue site at 0x52b00.  All other guards, region isolation checks, CRC
recalculation and DSP/UI construction remain identical.
"""
import json, sys
from pathlib import Path
import build_stage_f as base

# Exact stock V1.3.3 instruction immediately after:
#   0x52afc: lhi r0,[sp+8]   ; dequeued event/message code
#   0x52b00: movi r1,#0x7805
base.RAW_GATE_RT = 0x52b00
base.RAW_GATE_OLD = bytes.fromhex('44107805')


def manifest_path(argv):
    if '--manifest' not in argv:
        raise SystemExit('--manifest required')
    i=argv.index('--manifest')
    return Path(argv[i+1])


def main():
    mp=manifest_path(sys.argv)
    base.main()
    m=json.loads(mp.read_text())
    m['name']='Pocket TTG FX ROOT UI UNIVERSAL INPUT v0.8'
    m['event_intercept_runtime']='0x52b00'
    m['event_intercept_verified_post_dequeue']=True
    m.pop('event_intercept_verified_r0_7fxx',None)
    m['stock_pass_runtime']='0x52b04'
    m['stock_handled_runtime']='0x52abc'
    m['consumed_events']={
        '0x7f36':'MSG_PRE / previous TTG page',
        '0x7f37':'MSG_NEXT / next TTG page',
        '0x7f5d':'MSG_MAIN_VOL_UP / encoder clockwise',
        '0x7f5e':'MSG_MAIN_VOL_DW / encoder counterclockwise',
        '0x7f46':'menu key / long VALUE owned by TTG edit-toggle; stock menu suppressed'
    }
    m['input_strategy']='universal global message hook before Sonicake page/state filtering; direct TTG action calls in main GUI event task'
    m['short_encoder_press']='not yet statically identified; v0.8 uses verified long VALUE/menu event as edit/toggle while suppressing stock menu'
    m['old_downstream_patches_removed']=True
    m['controls']={
        'left/PRE':'previous block',
        'right/NEXT':'next block',
        'encoder_rotate':'select/edit',
        'VALUE long press':'edit/toggle or bypass; Sonicake menu suppressed'
    }
    # Correct the inherited patch evidence label/runtime after the guarded build.
    for p in m.get('patches',[]):
        if p.get('runtime')=='0x52b00' and p.get('stock')=='44107805':
            p['name']='UNIVERSAL POST-DEQUEUE EVENT GATE'
            p.pop('r0_is_0x7Fxx_here',None)
            p['r0_is_dequeued_message_here']=True
            p['stock_resume']='0x52b04'
            p['handled_continue']='0x52abc'
    mp.write_text(json.dumps(m,indent=2)+'\n')
    print('V0.8 manifest normalized',m['output_sha256'])

if __name__=='__main__':
    main()
