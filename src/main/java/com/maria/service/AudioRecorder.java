package com.maria.service;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Captura audio del micrófono y lo guarda como archivo WAV temporal. */
public final class AudioRecorder {

    private static final AudioFormat FORMAT =
            new AudioFormat(16_000, 16, 1, true, false);
    private static final int MAX_SEGUNDOS_GRABACION = 120;
    private static final int MAX_BYTES_CAPTURA =
            (int) (FORMAT.getSampleRate() * FORMAT.getFrameSize() * MAX_SEGUNDOS_GRABACION);

    private final ByteArrayOutputStream captured = new ByteArrayOutputStream();

    private TargetDataLine line;
    private Thread reader;
    private volatile boolean recording;

    /** Inicia la captura de audio desde el micrófono predeterminado. */
    public synchronized void start() throws LineUnavailableException {
        if (recording) {
            return;
        }

        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        if (!AudioSystem.isLineSupported(info)) {
            throw new LineUnavailableException(
                    "No se detectó un micrófono compatible con grabación PCM a 16 kHz."
            );
        }

        TargetDataLine nuevaLinea = (TargetDataLine) AudioSystem.getLine(info);
        try {
            nuevaLinea.open(FORMAT);
            captured.reset();
            line = nuevaLinea;
            recording = true;
            line.start();

            reader = new Thread(() -> {
                byte[] buffer = new byte[4096];

                while (recording) {
                    int count = line.read(buffer, 0, buffer.length);
                    if (count > 0) {
                        synchronized (captured) {
                            if (captured.size() + count > MAX_BYTES_CAPTURA) {
                                recording = false;
                                break;
                            }
                            captured.write(buffer, 0, count);
                        }
                    }
                }
            }, "maria-microphone-capture");

            reader.setDaemon(true);
            reader.start();
        } catch (RuntimeException | LineUnavailableException error) {
            recording = false;
            nuevaLinea.close();
            line = null;
            throw error;
        }
    }

    /** Detiene la captura y escribe los datos PCM en un archivo WAV temporal. */
    public synchronized Path stopToWav() throws IOException, InterruptedException {
        if (!recording && line == null) {
            throw new IllegalStateException("No hay una grabación activa.");
        }

        recording = false;
        if (line != null) {
            line.stop();
            line.close();
            line = null;
        }

        if (reader != null) {
            reader.join(2_000);
            reader = null;
        }

        byte[] pcm;
        synchronized (captured) {
            pcm = captured.toByteArray();
            captured.reset();
        }

        if (pcm.length < FORMAT.getFrameSize() * 10) {
            throw new IllegalStateException(
                    "La grabación quedó vacía o fue demasiado corta."
            );
        }

        Path wav = Files.createTempFile("maria-voz-", ".wav");
        wav.toFile().deleteOnExit();
        long frames = pcm.length / FORMAT.getFrameSize();

        try (AudioInputStream audio = new AudioInputStream(
                new ByteArrayInputStream(pcm),
                FORMAT,
                frames
        )) {
            AudioSystem.write(audio, AudioFileFormat.Type.WAVE, wav.toFile());
        } catch (IOException | RuntimeException error) {
            Files.deleteIfExists(wav);
            throw error;
        }

        return wav;
    }

    /** Indica si la captura de audio está en curso. */
    public synchronized boolean isRecording() {
        return recording;
    }
}