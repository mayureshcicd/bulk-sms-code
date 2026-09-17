package com.sms.util;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ws.schild.jave.*;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;
import ws.schild.jave.Encoder;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.VideoAttributes;
import ws.schild.jave.info.MultimediaInfo;
import ws.schild.jave.info.VideoInfo;
import ws.schild.jave.info.VideoSize;

import java.io.File;
import java.nio.file.Files;
@Slf4j
@Service
public class VideoCompressor {

    public byte[] compressVideo(byte[] videoBytes) throws Exception {
        // Save input bytes to temp file
        File source = new File("/tmp/input_" + System.currentTimeMillis() + ".mp4");
        File target = new File("/tmp/output_" + System.currentTimeMillis() + ".mp4");

        try {
            Files.write(source.toPath(), videoBytes);

            // ✅ Get original video information
            MultimediaObject multimediaObject = new MultimediaObject(source);
            MultimediaInfo multimediaInfo = multimediaObject.getInfo();
            VideoInfo videoInfo = multimediaInfo.getVideo();

            int originalWidth = videoInfo.getSize().getWidth();
            int originalHeight = videoInfo.getSize().getHeight();
            long fileSizeMB = videoBytes.length / (1024 * 1024);

            // ✅ Calculate optimal size using your function
            VideoSize targetSize = calculateOptimalSize(originalWidth, originalHeight, fileSizeMB);

            // ✅ Calculate optimal bitrate
            int bitrate = calculateOptimalBitrate(targetSize, fileSizeMB);

            log.info("Original: {}x{} ({}MB) -> Target: {}x{} ({} kbps)",
                    originalWidth, originalHeight, fileSizeMB,
                    targetSize.getWidth(), targetSize.getHeight(), bitrate / 1000);

            // ✅ Video settings with calculated size
            VideoAttributes video = new VideoAttributes();
            video.setCodec("libx264");
            video.setBitRate(bitrate);
            video.setFrameRate(24);
            video.setSize(targetSize); // ✅ Use calculated size

            // Audio settings
            AudioAttributes audio = new AudioAttributes();
            audio.setCodec("aac");
            audio.setBitRate(128000);
            audio.setChannels(2);
            audio.setSamplingRate(44100);

            // Encoding settings
            EncodingAttributes attrs = new EncodingAttributes();
            attrs.setOutputFormat("mp4");
            attrs.setVideoAttributes(video);
            attrs.setAudioAttributes(audio);

            Encoder encoder = new Encoder();
            encoder.encode(multimediaObject, target, attrs);

            byte[] compressed = Files.readAllBytes(target.toPath());
            long compressedSizeMB = compressed.length / (1024 * 1024);
            log.info("Compressed: {}MB ({}% of original)",
                    compressedSizeMB, (compressedSizeMB * 100) / Math.max(fileSizeMB, 1));

            return compressed;

        } finally {
            // Cleanup temp files
            source.delete();
            target.delete();
        }
    }

    // ✅ Your calculateOptimalSize function
    private VideoSize calculateOptimalSize(int originalWidth, int originalHeight, long fileSizeMB) {
        // Define maximum dimensions (1080p)
        final int MAX_WIDTH = 1920;
        final int MAX_HEIGHT = 1080;

        // Define minimum dimensions (480p)
        final int MIN_WIDTH = 480;
        final int MIN_HEIGHT = 360;

        int targetWidth = originalWidth;
        int targetHeight = originalHeight;

        // Only scale if file is large OR resolution is too high
        boolean needsScaling = (fileSizeMB > 15) ||
                (originalWidth > MAX_WIDTH || originalHeight > MAX_HEIGHT);

        if (needsScaling) {
            double aspectRatio = (double) originalWidth / originalHeight;

            // Case 1: Original is 4K or higher
            if (originalWidth > MAX_WIDTH || originalHeight > MAX_HEIGHT) {
                if (aspectRatio > 1.78) { // Wider than 16:9 (e.g., 21:9)
                    targetWidth = MAX_WIDTH;
                    targetHeight = (int) (MAX_WIDTH / aspectRatio);
                } else if (aspectRatio < 1.33) { // Taller than 4:3 (e.g., 9:16)
                    targetHeight = MAX_HEIGHT;
                    targetWidth = (int) (MAX_HEIGHT * aspectRatio);
                } else { // Standard 16:9 or 4:3
                    if (originalWidth > originalHeight) {
                        targetWidth = MAX_WIDTH;
                        targetHeight = (int) (MAX_WIDTH / aspectRatio);
                    } else {
                        targetHeight = MAX_HEIGHT;
                        targetWidth = (int) (MAX_HEIGHT * aspectRatio);
                    }
                }
            }
            // Case 2: Original is already 1080p or lower but file is large
            else if (fileSizeMB > 15) {
                // Keep resolution, just reduce bitrate
                targetWidth = originalWidth;
                targetHeight = originalHeight;
            }

            // Ensure minimum resolution (don't go below 480p)
            if (targetWidth < MIN_WIDTH || targetHeight < MIN_HEIGHT) {
                double scaleUp = Math.max(
                        (double) MIN_WIDTH / targetWidth,
                        (double) MIN_HEIGHT / targetHeight
                );
                targetWidth = (int) (targetWidth * scaleUp);
                targetHeight = (int) (targetHeight * scaleUp);
            }

            // Round to even numbers (required for some codecs)
            targetWidth = (targetWidth / 2) * 2;
            targetHeight = (targetHeight / 2) * 2;

            // Ensure values are not zero
            targetWidth = Math.max(targetWidth, MIN_WIDTH);
            targetHeight = Math.max(targetHeight, MIN_HEIGHT);
        }

        log.info("Resolution scaling: {}x{} -> {}x{}",
                originalWidth, originalHeight, targetWidth, targetHeight);

        return new VideoSize(targetWidth, targetHeight);
    }

    // ✅ Helper method for bitrate calculation
    private int calculateOptimalBitrate(VideoSize size, long fileSizeMB) {
        int pixels = size.getWidth() * size.getHeight();
        int baseBitrate;

        if (pixels <= 480 * 854) {
            baseBitrate = 600000; // 480p
        } else if (pixels <= 720 * 1280) {
            baseBitrate = 1000000; // 720p
        } else if (pixels <= 1080 * 1920) {
            baseBitrate = 1500000; // 1080p
        } else {
            baseBitrate = 2500000; // 4K+
        }

        // Adjust based on file size
        if (fileSizeMB > 20) {
            baseBitrate = (int)(baseBitrate * 0.6);
        } else if (fileSizeMB > 15) {
            baseBitrate = (int)(baseBitrate * 0.8);
        }

        return baseBitrate;
    }
}
