package com.example.maidmarriage.client.voice;

import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 把预生成的 WAV 文件解码成 Minecraft 声音系统可读取的 PCM 流。
 *
 * <p>AstraTTS 通常输出 WAV。这里做一层格式转换，是为了兼容后端输出的采样率、
 * 声道数或 endian 不完全一致的情况，避免玩家生成了一批语音却因为格式细节播不出来。</p>
 */
public final class HeartPactWavAudioStream implements AudioStream {
    private final AudioInputStream stream;
    private final AudioFormat format;
    private final byte[] buffer;

    public HeartPactWavAudioStream(byte[] data) throws IOException, UnsupportedAudioFileException {
        AudioInputStream original = AudioSystem.getAudioInputStream(new ByteArrayInputStream(data));
        AudioFormat originalFormat = original.getFormat();
        AudioFormat targetFormat = originalFormat;
        if (originalFormat.getEncoding() != AudioFormat.Encoding.PCM_SIGNED
                || originalFormat.getSampleSizeInBits() != 16
                || originalFormat.isBigEndian()) {
            targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    originalFormat.getSampleRate(),
                    16,
                    originalFormat.getChannels(),
                    originalFormat.getChannels() * 2,
                    originalFormat.getSampleRate(),
                    false
            );
            this.stream = AudioSystem.getAudioInputStream(targetFormat, original);
        } else {
            this.stream = original;
        }
        this.format = this.stream.getFormat();
        this.buffer = new byte[Math.max(this.format.getFrameSize(), 4096)];
    }

    @Override
    public AudioFormat getFormat() {
        return format;
    }

    @Override
    public ByteBuffer read(int size) throws IOException {
        ByteBuffer byteBuffer = BufferUtils.createByteBuffer(size);
        while (byteBuffer.remaining() > 0) {
            int maxRead = Math.min(byteBuffer.remaining(), buffer.length);
            int count = this.stream.read(buffer, 0, maxRead);
            if (count == -1) {
                break;
            }
            byteBuffer.put(buffer, 0, count);
        }
        byteBuffer.flip();
        return byteBuffer;
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }
}
