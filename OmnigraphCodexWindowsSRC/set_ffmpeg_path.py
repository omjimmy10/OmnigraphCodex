import os, sys
if hasattr(sys, '_MEIPASS'):
    # This assumes that you added ffmpeg.exe under Resources/ffmpeg
    ffmpeg_folder = os.path.join(sys._MEIPASS, "Resources", "ffmpeg")
    os.environ["PATH"] = ffmpeg_folder + os.pathsep + os.environ.get("PATH", "")
