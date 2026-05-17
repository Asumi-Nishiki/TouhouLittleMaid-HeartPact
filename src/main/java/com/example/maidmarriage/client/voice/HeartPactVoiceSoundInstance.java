package com.example.maidmarriage.client.voice;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.mojang.logging.LogUtils;
import java.util.concurrent.CompletableFuture;
import javax.sound.sampled.UnsupportedAudioFileException;
import net.minecraft.Util;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.sounds.SoundSource;
import org.slf4j.Logger;

/**
 * Bound playback instance for pre-generated Heart Pact voice lines.
 */
public final class HeartPactVoiceSoundInstance extends EntityBoundSoundInstance {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final byte[] audio;

    public HeartPactVoiceSoundInstance(EntityMaid maid, byte[] audio, float volume) {
        super(InitSounds.MAID_AI_CHAT.get(), SoundSource.NEUTRAL, volume, 1.0F, maid, maid.getId());
        this.audio = audio;
    }

    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary library, Sound sound, boolean looping) {
        return CompletableFuture.supplyAsync(() -> {
            if (audio == null || audio.length == 0) {
                LOGGER.warn("Heart Pact voice audio is empty, cannot play");
                return null;
            }
            try {
                return new HeartPactWavAudioStream(audio);
            } catch (UnsupportedAudioFileException exception) {
                LOGGER.warn("Heart Pact voice decode failed: unsupported WAV format, bytes={}", audio.length, exception);
            } catch (Exception exception) {
                LOGGER.error("Heart Pact voice decode failed, bytes={}", audio.length, exception);
            }
            return null;
        }, Util.backgroundExecutor());
    }
}
