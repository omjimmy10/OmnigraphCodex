import sys
import os
import numpy as np
import tempfile
import subprocess
from PyQt5.QtWidgets import (
    QApplication, QMainWindow, QPushButton, QLabel, QFileDialog, QVBoxLayout, QWidget,
    QGraphicsView, QGraphicsScene, QHBoxLayout, QMessageBox, QComboBox, QSlider, QStyle, QStyleOptionSlider,
    QDialog, QTextBrowser
)
from PyQt5.QtGui import QPixmap, QPainter, QColor, QBrush, QCursor, QIcon
from PyQt5.QtCore import Qt, QRectF, QTimer, QPointF
from PIL import Image
import wave
import pyaudio
import io

# Function to get the correct resource path (works for both .py and .exe)
def resource_path(relative_path):
    """ Get absolute path to resource, works for dev & PyInstaller-compiled app """
    if hasattr(sys, '_MEIPASS'):
        return os.path.join(sys._MEIPASS, relative_path)  # When running as an .exe
    return os.path.join(os.path.abspath("."), relative_path)  # When running as .py

# Define the resource path dynamically
RESOURCE_PATH = resource_path("Resources")

class ClickableSlider(QSlider):
    """
    A QSlider subclass that allows jumping to the clicked position.
    """
    def mousePressEvent(self, event):
        if event.button() == Qt.LeftButton:
            opt = QStyleOptionSlider()
            self.initStyleOption(opt)
            # Calculate the slider’s handle length
            slider_length = self.style().pixelMetric(QStyle.PM_SliderLength, opt, self)
            slider_min = self.rect().x()
            slider_max = self.rect().right() - slider_length + 1
            new_val = QStyle.sliderValueFromPosition(self.minimum(), self.maximum(),
                                                      event.x() - slider_min,
                                                      slider_max - slider_min)
            self.setValue(new_val)
        super().mousePressEvent(event)


class ZoomableGraphicsView(QGraphicsView):
    def __init__(self, parent=None):
        super().__init__(parent)
        self._zoom = 0
        self.setRenderHint(QPainter.Antialiasing)
        self.setRenderHint(QPainter.SmoothPixmapTransform)
        self.setDragMode(QGraphicsView.ScrollHandDrag)
        self.setTransformationAnchor(QGraphicsView.AnchorUnderMouse)
        self.setResizeAnchor(QGraphicsView.AnchorUnderMouse)

    def wheelEvent(self, event):
        zoom_factor = 1.25
        if event.angleDelta().y() > 0:
            self.scale(zoom_factor, zoom_factor)
            self._zoom += 1
        else:
            self.scale(1/zoom_factor, 1/zoom_factor)
            self._zoom -= 1


class AudioToImageConverter(QMainWindow):
    def __init__(self):
        super().__init__()

        # Add this line to prevent AttributeError
        self.is_playing = False  # Ensures toggle_playback() has access to this variable

        # Fix: Initialize cursor size
        self.cursor_size = 5  # Adjust as needed

        self.timer = QTimer()  # Timer for visualizer updates
        self.timer.timeout.connect(self.update_visualizer)

        try:
            self.p = pyaudio.PyAudio()
        except Exception as e:
            QMessageBox.critical(self, "Audio Error", f"Failed to initialize audio: {str(e)}")
            sys.exit(1)


        # Enable drag and drop on the main window (this drop area works near the buttons)
        self.setAcceptDrops(True)
        self.setup_ui()
        
        self.dark_mode = False  # Default to light mode
        self.dragging_slider = False
        
        # Variables to store the last file dropped or selected.
        self.last_audio_file = None
        self.last_image_file = None
        self.last_operation = None  # 'encode' or 'decode'
        
        self.encoding_method = "A"  # ✅ Initialize default encoding method

        # Set App Icon
        icon_path = os.path.join(RESOURCE_PATH, "Logo_Omnigraph.png")
        if os.path.exists(icon_path):
            self.setWindowIcon(QIcon(icon_path))

    def setup_ui(self):
        self.setWindowTitle("Omnigraph Codex")
        self.setGeometry(100, 100, 800, 600)

        # Main layout
        main_layout = QVBoxLayout()

        # **Top Bar Layout**
        top_bar_layout = QHBoxLayout()

        # "Read Me" Button (Where "Encoding Method" label was)
        self.read_me_btn = QPushButton("Read Me")
        self.read_me_btn.setFixedSize(80, 30)  # Fixes width & height
        self.read_me_btn.clicked.connect(self.show_read_me)
        top_bar_layout.addWidget(self.read_me_btn)

        # Stretch to push everything else to the right
        top_bar_layout.addStretch()

        # "Encoding Method" Label (Moved closer to dropdown)
        self.method_label = QLabel("Encoding Method:")
        top_bar_layout.addWidget(self.method_label)

        # Encoding Method Dropdown (Stays exactly in the same position & size)
        self.method_combo = QComboBox()
        self.method_combo.addItems(["A - Channel Multiplexing", "B - Pixel Interleaving", "C - Spectral Encoding"])
        self.method_combo.currentIndexChanged.connect(self.update_encoding_method)
        top_bar_layout.addWidget(self.method_combo)

        # "Toggle Dark Mode" (Ensures same position as before)
        self.dark_mode_btn = QPushButton("Toggle Dark Mode")
        self.dark_mode_btn.clicked.connect(self.toggle_dark_mode)
        top_bar_layout.addWidget(self.dark_mode_btn)

        main_layout.addLayout(top_bar_layout)

        # **Graphics View (Image Preview Area)**
        self.graphics_view = ZoomableGraphicsView(self)
        self.scene = QGraphicsScene()
        self.graphics_view.setScene(self.scene)
        self.graphics_view.setMinimumSize(400, 400)
        main_layout.addWidget(self.graphics_view)

        # **Bottom Control Buttons**
        control_layout = QHBoxLayout()

        self.info_label = QLabel("Ready")
        control_layout.addWidget(self.info_label)

        self.open_encode_btn = QPushButton("Encode Audio")
        self.open_encode_btn.clicked.connect(lambda: self.encode_file())
        control_layout.addWidget(self.open_encode_btn)

        self.open_decode_btn = QPushButton("Decode Image")
        self.open_decode_btn.clicked.connect(lambda: self.decode_file())
        control_layout.addWidget(self.open_decode_btn)

        self.play_btn = QPushButton("Play")
        self.play_btn.clicked.connect(self.toggle_playback)
        control_layout.addWidget(self.play_btn)

        self.save_btn = QPushButton("Save Output")
        self.save_btn.clicked.connect(self.save_output)
        self.save_btn.setEnabled(False)
        control_layout.addWidget(self.save_btn)

        main_layout.addLayout(control_layout)

        # **Drag and Drop Prompt**
        self.drop_prompt = QLabel("Drag and drop your file here")
        self.drop_prompt.setAlignment(Qt.AlignCenter)
        main_layout.addWidget(self.drop_prompt)

        # **Progress Slider**
        self.progress_slider = ClickableSlider(Qt.Horizontal)
        self.progress_slider.sliderPressed.connect(lambda: setattr(self, 'dragging_slider', True))
        self.progress_slider.sliderReleased.connect(self.slider_released)
        main_layout.addWidget(self.progress_slider)

        # **Finalizing Layout**
        container = QWidget()
        container.setLayout(main_layout)
        self.setCentralWidget(container)

    def show_read_me(self):
        """
        Displays the README using a QDialog with a background image while ensuring proper text layout.
        """
        bg_path = os.path.normpath(os.path.join(RESOURCE_PATH, "text_background.png"))

        # Increased height to fit all text without scrolling
        dialog_width = 525  # Keep same width
        dialog_height = 760  # Increased from 680 to remove scrolling

        # Create a fixed-size dialog
        dialog = QDialog(self)
        dialog.setWindowTitle("Omnigraph Codex - README")
        dialog.setFixedSize(dialog_width, dialog_height)  # Prevents resizing
        dialog.setWindowFlags(dialog.windowFlags() & ~Qt.WindowContextHelpButtonHint)


        # Create QLabel for background image
        bg_label = QLabel(dialog)
        bg_label.setGeometry(0, 0, dialog_width, dialog_height)

        if os.path.exists(bg_path):
            bg_pixmap = QPixmap(bg_path)
            bg_label.setPixmap(bg_pixmap)
            bg_label.setScaledContents(True)  # Ensures the image fits

        # Create a QTextBrowser for text (Non-scrollable, packed layout)
        text_browser = QTextBrowser(dialog)
        text_browser.setOpenExternalLinks(True)
        text_browser.setGeometry(25, 25, dialog_width - 50, dialog_height - 120)  # Adjusted size
        text_browser.setStyleSheet("""
            background: transparent;
            color: white;
            font-family: Arial;
            font-size: 14px;
            padding: 8px;
            border: none;
        """)
        text_browser.setVerticalScrollBarPolicy(Qt.ScrollBarAlwaysOff)  # No scrollbars
        text_browser.setHorizontalScrollBarPolicy(Qt.ScrollBarAlwaysOff)

        readme_text = """
        <html>
        <body>
            <h3>Omnigraph Codex</h3>
            Converts audio data into pixel values and vice-versa to generate unique images and reconstruct sound.<br><br>

            🔴 <b>Method A</b> – Stores different parts of the audio in Red, Green, and Blue channels sequentially.<br>
            🔵 <b>Method B</b> – Interweaves audio data into all channels in each pixel simultaneously.<br>
            🟢 <b>Method C</b> – Uses frequency spectrum analysis to distribute the audio across the image.<br><br>

            <h4>🎨 Why I Created This</h4>
            Omnigraph Codex was designed for:<br>
            ✅ Creating visually generative, abstract, and glitch art<br>
            ✅ Sampling images into abstract audio pieces for music producers<br>
            ✅ Exploring new forms of audiovisual transformation<br><br>

            <b>✨ Tip:</b> Different methods produce distinct visual and audio patterns! Experiment to discover new textures and sounds.<br><br>

            <h4>🌎 Join the Community!</h4>
            I want this codex to bring together a community of artists and technical creators.<br>
            If you have any interesting creations, please share them at:<br><br>

            📌 <a href='https://www.reddit.com/r/OmnigraphCodex'>r/OmnigraphCodex</a> (Reddit)<br><br>

            Let’s build something amazing together! 🎨🎶🚀<br><br>
            <b>📌 Developed by Om Chari and GPT</b>
        </body>
        </html>
        """

        text_browser.setHtml(readme_text)

        # OK Button to close the dialog
        ok_button = QPushButton("OK", dialog)
        ok_button.setGeometry(dialog_width // 2 - 50, dialog_height - 70, 100, 40)
        ok_button.clicked.connect(dialog.accept)

        # Ensure it does not have maximize/minimize options
        dialog.setWindowFlags(dialog.windowFlags() & ~Qt.WindowMinMaxButtonsHint)
        dialog.exec_()






    def toggle_dark_mode(self):
        if self.dark_mode:
            self.setStyleSheet("")
        else:
            self.setStyleSheet(
                """
                QMainWindow { background-color: #121212; color: #ffffff; }
                QPushButton { background-color: #333; color: #fff; border-radius: 5px; padding: 5px; }
                QPushButton:hover { background-color: #444; }
                QLabel { color: #ffffff; }
                QGraphicsView { background-color: #222; }
                """
            )
        self.dark_mode = not self.dark_mode

    def update_encoding_method(self):
        method_map = {
            "A - Channel Multiplexing": "A",
            "B - Pixel Interleaving": "B",
            "C - Spectral Encoding": "C"
        }
        
        selected_text = self.method_combo.currentText()  # Get full selected text
        self.encoding_method = method_map.get(selected_text, "A")  # Extract mapped value

        print(f"Encoding Method changed to {self.encoding_method}")  # Debugging

        # Reprocess stored file if available.
        if self.last_operation == 'encode' and self.last_audio_file:
            self.encode_file(use_last=True)
        elif self.last_operation == 'decode' and self.last_image_file:
            self.decode_file(use_last=True)


    def encode_file(self, use_last=False, file_path=None):
        try:
            if file_path is not None:
                self.last_audio_file = file_path
                self.last_operation = 'encode'
            elif not use_last:
                file_path, _ = QFileDialog.getOpenFileName(
                    self, "Open Audio File", "",
                    "Audio Files (*.wav *.mp3 *.ogg *.flac)"
                )
                if not file_path:
                    return
                self.last_audio_file = file_path
                self.last_operation = 'encode'
            elif use_last:
                file_path = self.last_audio_file

            if file_path:
                self.encoded_image = self.encode_audio_to_image(file_path)
                if self.encoded_image is not None:
                    self.display_preview(self.encoded_image)
                    self.output_type = 'image'
                    self.save_btn.setEnabled(True)
                    QMessageBox.information(self, "Success", "Encoding completed successfully!")
                    
                    # Auto-decode for playback
                    self.decoded_audio = self.decode_image_to_audio(self.encoded_image)
                    if self.decoded_audio is not None:
                        self.load_audio_for_playback()
        except Exception as e:
            QMessageBox.critical(self, "Error", f"Encoding failed: {str(e)}")

    def decode_file(self, use_last=False, file_path=None):
        try:
            if file_path is not None:
                self.last_image_file = file_path
                self.last_operation = 'decode'
            elif not use_last:
                file_path, _ = QFileDialog.getOpenFileName(
                    self, "Open Image File", "",
                    "Image Files (*.png *.jpg *.jpeg)"
                )
                if not file_path:
                    return
                self.last_image_file = file_path
                self.last_operation = 'decode'
            elif use_last:
                file_path = self.last_image_file

            if file_path:
                self.decoded_audio = self.decode_image_to_audio(file_path)
                if self.decoded_audio is not None:
                    self.load_audio_for_playback()
                    self.display_preview(file_path)
                    self.output_type = 'audio'
                    self.save_btn.setEnabled(True)
                    QMessageBox.information(self, "Success", "Decoding completed successfully!")
        except Exception as e:
            QMessageBox.critical(self, "Error", f"Decoding failed: {str(e)}")

    def save_output(self):
        if self.output_type == 'image' and self.encoded_image is not None:
            file_path, _ = QFileDialog.getSaveFileName(
                self, "Save Image", "",
                "PNG Files (*.png)"
            )
            if file_path:
                self.encoded_image.save(file_path, "PNG")
                QMessageBox.information(self, "Success", f"Image saved to {file_path}")
        elif self.output_type == 'audio' and self.decoded_audio is not None:
            file_path, _ = QFileDialog.getSaveFileName(
                self, "Save Audio", "",
                "WAV Files (*.wav)"
            )
            if file_path:
                with open(file_path, 'wb') as f:
                    f.write(self.decoded_audio.getvalue())
                QMessageBox.information(self, "Success", f"Audio saved to {file_path}")

    def display_preview(self, image_data):
        self.scene.clear()
        if isinstance(image_data, Image.Image):
            byte_arr = io.BytesIO()
            image_data.save(byte_arr, format='PNG')
            pixmap = QPixmap()
            pixmap.loadFromData(byte_arr.getvalue())
        else:
            pixmap = QPixmap(image_data)
        self.scene.addPixmap(pixmap)
        self.graphics_view.fitInView(QRectF(pixmap.rect()), Qt.KeepAspectRatio)
        self.image_size = (pixmap.width(), pixmap.height())
        self.viz_rect = self.scene.addRect(0, 0, self.cursor_size, self.cursor_size,
                                           brush=QBrush(QColor(255, 0, 0, 200)))
        self.viz_rect.setVisible(True)

    def slider_released(self):
        self.dragging_slider = False
        if self.audio_data:
            new_pos = self.progress_slider.value()
            self.current_position = new_pos
            if self.is_playing:
                self.stop_playback()
                self.start_playback()

    def toggle_playback(self):
        if not hasattr(self, 'audio_data') or not self.audio_data:
            QMessageBox.warning(self, "Playback", "No audio loaded or audio is empty!")
            return

        if self.is_playing:
            self.stop_playback()
        else:
            self.start_playback()


    def start_playback(self):
        if not hasattr(self, 'p') or self.p is None:
            QMessageBox.critical(self, "Playback Error", "Audio system is not initialized.")
            return

        try:
            self.stream = self.p.open(
                format=self.p.get_format_from_width(2),
                channels=1,
                rate=44100,
                output=True,
                stream_callback=self.audio_callback
            )
            
            if self.stream is None:
                QMessageBox.critical(self, "Playback Error", "Failed to create audio stream.")
                return

            self.is_playing = True
            self.play_btn.setText("Pause")
            self.viz_rect.setVisible(True)
            self.timer.start(50)  # Ensure timer is running
        except Exception as e:
            QMessageBox.critical(self, "Playback Error", f"Failed to start playback: {str(e)}")

    def stop_playback(self):
        if hasattr(self, 'timer'):
            self.timer.stop()

        self.is_playing = False
        self.play_btn.setText("Play")

        if hasattr(self, 'stream') and self.stream:
            self.stream.stop_stream()
            self.stream.close()
            self.stream = None  # Set to None to prevent invalid access

    def audio_callback(self, in_data, frame_count, time_info, status):
        try:
            # Debugging: Check if audio data exists
            if not hasattr(self, 'audio_data') or self.audio_data is None:
                print("[DEBUG] No audio data loaded. Returning silence.")
                return (b'\x00' * frame_count * 2, pyaudio.paComplete)  # Return silence
            
            total_samples = len(self.audio_data) // 2  # Convert byte count to sample count
            start = self.current_position
            end = min(start + frame_count, total_samples)  # Prevent index out-of-bounds
            
            # Debugging: Print frame positions
            print(f"[DEBUG] Playing from {start} to {end} / {total_samples}")

            if start >= total_samples:
                print("[DEBUG] Playback finished. Returning silence.")
                return (b'\x00' * frame_count * 2, pyaudio.paComplete)  # Stop playback safely

            data = self.audio_data[start * 2:end * 2]  # Extract correct audio segment
            self.current_position = end

            return (data, pyaudio.paContinue if end < total_samples else pyaudio.paComplete)

        except Exception as e:
            print(f"[ERROR] Audio callback failed: {e}")
            return (b'\x00' * frame_count * 2, pyaudio.paAbort)  # Abort playback if an error occurs


    def update_visualizer(self):
        if not self.audio_data or self.image_size[0] == 0:
            return

        if not self.dragging_slider:
            self.progress_slider.setValue(self.current_position)

        total_samples = len(self.audio_data) // 2  # Convert byte count to sample count

        if self.encoding_method == "A":
            # A should go through the whole image once per channel
            samples_per_channel = total_samples // 3  # Divide equally among R, G, B
            channel_index = self.current_position // samples_per_channel  # Determines which channel we are in
            channel_position = self.current_position % samples_per_channel  # Position within the current channel
            
            # Map position to pixel space
            current_pixel = channel_position
            row = current_pixel // self.image_size[0]
            col = current_pixel % self.image_size[0]

            # Set visualizer box color based on the channel
            if channel_index == 0:  # Red Channel
                box_color = QColor(255, 0, 0, 200)  # Red
            elif channel_index == 1:  # Green Channel
                box_color = QColor(0, 255, 0, 200)  # Green
            else:  # Blue Channel
                box_color = QColor(0, 0, 255, 200)  # Blue

            if row < self.image_size[1]:
                self.viz_rect.setPos(col - self.cursor_size // 2, row - self.cursor_size // 2)
                self.viz_rect.setBrush(QBrush(box_color))  # Change color dynamically

        elif self.encoding_method == "B":
            # B should go through the whole image only once, but slower (since it processes R, G, B sequentially per pixel)
            total_pixels = self.image_size[0] * self.image_size[1]  # Total number of pixels
            total_steps = total_pixels * 3  # Since it processes R, G, B separately per pixel

            # Map current position to pixel space
            pixel_index = self.current_position // 3  # Divide by 3 to slow down movement
            row = pixel_index // self.image_size[0]
            col = pixel_index % self.image_size[0]

            if row < self.image_size[1]:
                self.viz_rect.setPos(col - self.cursor_size // 2, row - self.cursor_size // 2)
                self.viz_rect.setBrush(QBrush(QColor(0, 0, 0, 200)))  # Always black for B


        elif self.encoding_method == "C":
            # C is already correct, goes through the whole image once
            current_pixel = self.current_position
            row = current_pixel // self.image_size[0]
            col = current_pixel % self.image_size[0]

            if row < self.image_size[1]:
                self.viz_rect.setPos(col - self.cursor_size // 2, row - self.cursor_size // 2)
                self.viz_rect.setBrush(QBrush(QColor(0, 0, 0, 200)))  # Always black for C




    def encode_audio_to_image(self, audio_path):
        temp_wav = tempfile.NamedTemporaryFile(suffix=".wav", delete=False)
        temp_wav.close()
        try:
            subprocess.run([
                "ffmpeg", "-y", "-i", audio_path,
                "-ac", "1", "-ar", "44100", "-c:a", "pcm_s16le",
                "-hide_banner", "-loglevel", "error", temp_wav.name
            ], check=True, stderr=subprocess.PIPE)
            with wave.open(temp_wav.name, 'rb') as wav_file:
                raw_data = wav_file.readframes(wav_file.getnframes())
            audio_data = np.frombuffer(raw_data, dtype=np.int16)
            audio_8bit = ((audio_data.astype(np.float32) + 32768) / 65535 * 255).astype(np.uint8)
            if self.encoding_method == "A":
                total = len(audio_8bit)
                split_points = [total // 3, 2 * total // 3]
                red = audio_8bit[:split_points[0]]
                green = audio_8bit[split_points[0]:split_points[1]]
                blue = audio_8bit[split_points[1]:]
                side = int(np.ceil(np.sqrt(len(red))))
                required = side ** 2
                red = np.pad(red, (0, required - len(red)), mode='constant')
                green = np.pad(green, (0, required - len(green)), mode='constant')
                blue = np.pad(blue, (0, required - len(blue)), mode='constant')
                rgb_array = np.zeros((side, side, 3), dtype=np.uint8)
                rgb_array[:, :, 0] = red.reshape((side, side))
                rgb_array[:, :, 1] = green.reshape((side, side))
                rgb_array[:, :, 2] = blue.reshape((side, side))
            elif self.encoding_method == "B":
                audio_8bit = audio_8bit[:len(audio_8bit) - (len(audio_8bit) % 3)]
                audio_8bit = audio_8bit.reshape(-1, 3)
                side = int(np.ceil(np.sqrt(len(audio_8bit))))
                required = side ** 2
                audio_8bit = np.pad(audio_8bit, ((0, required - len(audio_8bit)), (0, 0)), mode='constant')
                rgb_array = np.zeros((side, side, 3), dtype=np.uint8)
                rgb_array[:, :, 0] = audio_8bit[:, 0].reshape((side, side))
                rgb_array[:, :, 1] = audio_8bit[:, 2].reshape((side, side))
                rgb_array[:, :, 2] = audio_8bit[:, 1].reshape((side, side))
            elif self.encoding_method == "C":
                audio_float = audio_data.astype(np.float32) / 32768.0
                N = len(audio_float)
                Fs = 44100
                fft_data = np.fft.rfft(audio_float)
                low_cutoff = 1000
                mid_cutoff = 4000
                k_low = int(low_cutoff * N / Fs)
                k_mid = int(mid_cutoff * N / Fs)
                low_fft = fft_data.copy()
                low_fft[k_low:] = 0.0
                mid_fft = fft_data.copy()
                mid_fft[:k_low] = 0.0
                mid_fft[k_mid:] = 0.0
                high_fft = fft_data.copy()
                high_fft[:k_mid] = 0.0
                low_signal = np.fft.irfft(low_fft, n=N)
                mid_signal = np.fft.irfft(mid_fft, n=N)
                high_signal = np.fft.irfft(high_fft, n=N)
                def to_8bit(signal):
                    signal = (signal * 32768).astype(np.int16)
                    return ((signal.astype(np.float32) + 32768) / 65535 * 255).astype(np.uint8)
                low_8bit = to_8bit(low_signal)
                mid_8bit = to_8bit(mid_signal)
                high_8bit = to_8bit(high_signal)
                side = int(np.ceil(np.sqrt(N)))
                required = side ** 2
                red = np.pad(low_8bit, (0, required - N), mode='constant')
                green = np.pad(mid_8bit, (0, required - N), mode='constant')
                blue = np.pad(high_8bit, (0, required - N), mode='constant')
                rgb_array = np.zeros((side, side, 3), dtype=np.uint8)
                rgb_array[:, :, 0] = red.reshape((side, side))
                rgb_array[:, :, 1] = green.reshape((side, side))
                rgb_array[:, :, 2] = blue.reshape((side, side))
            img = Image.fromarray(rgb_array, 'RGB')
            return img
        except subprocess.CalledProcessError as e:
            QMessageBox.critical(self, "Encoding Error", f"FFmpeg failed: {e.stderr.decode()}")
            return None
        except Exception as e:
            QMessageBox.critical(self, "Error", f"Encoding failed: {str(e)}")
            return None
        finally:
            os.remove(temp_wav.name)

    def decode_image_to_audio(self, image_input):
        try:
            if isinstance(image_input, str):
                img = Image.open(image_input)
            else:
                img = image_input
            img = img.convert("RGB")
            rgb_array = np.array(img)

            if self.encoding_method == "A":
                # Extract channel data in correct sequence
                red_channel = rgb_array[:, :, 0].flatten()  # First 1/3rd of audio
                green_channel = rgb_array[:, :, 1].flatten()  # Second 1/3rd
                blue_channel = rgb_array[:, :, 2].flatten()  # Last 1/3rd
                
                # Ensure we reconstruct the original encoded sequence (not repeating)
                reconstructed_audio = np.concatenate([red_channel, green_channel, blue_channel])

                # Convert back to 16-bit audio
                audio_16bit = ((reconstructed_audio.astype(np.float32) / 255) * 65535 - 32768).astype(np.int16)

            elif self.encoding_method == "B":
                red = rgb_array[:, :, 0].flatten()
                blue = rgb_array[:, :, 2].flatten()
                green = rgb_array[:, :, 1].flatten()
                audio_8bit = np.stack([red, blue, green], axis=-1).flatten()
                audio_16bit = ((audio_8bit.astype(np.float32) / 255) * 65535 - 32768).astype(np.int16)

            elif self.encoding_method == "C":
                red = rgb_array[:, :, 0].flatten()
                green = rgb_array[:, :, 1].flatten()
                blue = rgb_array[:, :, 2].flatten()
                red_16 = ((red.astype(np.float32) / 255) * 65535 - 32768).astype(np.int16)
                green_16 = ((green.astype(np.float32) / 255) * 65535 - 32768).astype(np.int16)
                blue_16 = ((blue.astype(np.float32) / 255) * 65535 - 32768).astype(np.int16)
                audio_16bit = (red_16 * 0.6 + green_16 * 0.3 + blue_16 * 0.1).astype(np.int16)

            # Save and return decoded audio
            audio_buffer = io.BytesIO()
            with wave.open(audio_buffer, 'wb') as wav_file:
                wav_file.setnchannels(1)
                wav_file.setsampwidth(2)
                wav_file.setframerate(44100)
                wav_file.writeframes(audio_16bit.tobytes())
            audio_buffer.seek(0)
            return audio_buffer

        except Exception as e:
            QMessageBox.critical(self, "Error", f"Decoding failed: {str(e)}")
            return None



    def load_audio_for_playback(self):
        if self.decoded_audio:
            try:
                self.decoded_audio.seek(0)
                with wave.open(self.decoded_audio, 'rb') as wav_file:
                    self.audio_data = wav_file.readframes(wav_file.getnframes())
                self.progress_slider.setMaximum(len(self.audio_data) // 2)
                self.current_position = 0
            except Exception as e:
                QMessageBox.critical(self, "Error", f"Failed to load audio: {str(e)}")

    # --- Drag and Drop events on the main window (near the buttons) ---
    def dragEnterEvent(self, event):
        if event.mimeData().hasUrls():
            event.acceptProposedAction()

    def dropEvent(self, event):
        urls = event.mimeData().urls()
        if urls:
            file_path = urls[0].toLocalFile()
            if file_path.lower().endswith(('.png', '.jpg', '.jpeg')):
                self.decode_file(file_path=file_path)
            elif file_path.lower().endswith(('.wav', '.mp3', '.ogg', '.flac')):
                self.encode_file(file_path=file_path)
            else:
                QMessageBox.warning(self, "Unsupported file", "This file type is not supported")

    def closeEvent(self, event):
        """ Ensure all resources are cleaned up properly before closing the app. """
        try:
            # Stop playback properly
            if hasattr(self, 'stream') and self.stream is not None:
                self.stream.stop_stream()
                self.stream.close()

            # Terminate PyAudio safely
            if hasattr(self, 'p') and self.p is not None:
                self.p.terminate()
            
            # Clear buffers (helps if large memory is allocated)
            if hasattr(self, 'audio_data'):
                del self.audio_data

            # Stop visualizer timer if running
            if hasattr(self, 'timer') and self.timer.isActive():
                self.timer.stop()

            # Run garbage collection to free up memory immediately
            gc.collect()
            
        except Exception as e:
            print(f"Error during close: {e}")

        event.accept()


if __name__ == "__main__":
    app = QApplication(sys.argv)
    try:
        window = AudioToImageConverter()
        window.show()
        sys.exit(app.exec_())
    except Exception as e:
        QMessageBox.critical(None, "Fatal Error", f"Application failed: {str(e)}")
        sys.exit(1)


#I have no clue how to code, this is my first ever project which literally started and finished with the complete help of GPT,
#I only thought of the encoding laws and how it would work fundamentally, all the logic implementation was done by AI,
#I was not initially going to make this but asked another friend to do it, however never followed it up with me! So I took this into my own hands as I really wanted to see this completed.
#I had a great time learning the basics like running my code off of notepad XD, eventually lil more here and there.
#I did take help from friends here and there! Thank you Ayush_Madan and Shwetank_Anand.
#I HOPE YOU ALL HAVE FUN WITH THIS CODEX AND EXPAND ON IT EVEN MORE!!! ~ Om Chari (4E)