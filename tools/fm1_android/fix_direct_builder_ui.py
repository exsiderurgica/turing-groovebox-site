from pathlib import Path
p=Path('tools/fm1_android/build_direct.py')
s=p.read_text()
a=s.index("old='backupButton = button")
b=s.index("a=s.index('              private void prepareBackup(){')",a)
newblock=r'''new='        backupButton = button("2 · SCEGLI A2.1 APP"); backupButton.setOnClickListener(v -> chooseApp());\n        LinearLayout.LayoutParams bp2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); bp2.topMargin=dp(8); root.addView(backupButton,bp2);\n        flashButton=button("3 · SCRIVI A2.1 PERF"); flashButton.setEnabled(false); flashButton.setOnClickListener(v -> confirmFlash());\n        LinearLayout.LayoutParams bp3 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); bp3.topMargin=dp(8); root.addView(flashButton,bp3);'
u0=s.index('        backupButton = button("2 · BACKUP FLASH READ-ONLY");')
u1=s.index('\n\n        progress =',u0)
s=s[:u0]+new+s[u1:]
'''
s=s[:a]+newblock+s[b:]
p.write_text(s)
