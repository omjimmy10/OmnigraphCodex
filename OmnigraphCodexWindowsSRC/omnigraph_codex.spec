# -*- mode: python ; coding: utf-8 -*-


a = Analysis(
    ['omnigraph_codex.py'],
    pathex=[],
    binaries=[('Resources\\ffmpeg\\ffmpeg-7.1-essentials_build\\bin\\ffmpeg.exe', 'Resources\\ffmpeg')],
    datas=[('Resources\\text_background.png', 'Resources'), ('Resources\\Logo_Omnigraph.png', 'Resources'), ('Resources\\Logo_Omnigraph.ico', 'Resources')],
    hiddenimports=[],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=['set_ffmpeg_path.py'],
    excludes=[],
    noarchive=False,
    optimize=0,
)
pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.datas,
    [],
    name='omnigraph_codex',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    upx_exclude=[],
    runtime_tmpdir=None,
    console=False,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
    icon=['Resources\\Logo_Omnigraph.ico'],
)

