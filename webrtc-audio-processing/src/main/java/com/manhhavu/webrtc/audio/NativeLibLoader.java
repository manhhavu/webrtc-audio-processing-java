package com.manhhavu.webrtc.audio;

import com.sun.jna.Native;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class NativeLibLoader {
    private static volatile NativeLib instance;

    static NativeLib load() {
        if (instance == null) {
            synchronized (NativeLibLoader.class) {
                if (instance == null) {
                    instance = doLoad();
                }
            }
        }
        return instance;
    }

    private static NativeLib doLoad() {
        String platform = detectPlatform();
        String libName = libFileName();
        String resourcePath = "/native/" + platform + "/" + libName;

        // Try classpath extraction first
        try (InputStream is = NativeLibLoader.class.getResourceAsStream(resourcePath)) {
            if (is != null) {
                Path tempDir = Files.createTempDirectory("webrtc-apm-");
                Path tempLib = tempDir.resolve(libName);
                Files.copy(is, tempLib, StandardCopyOption.REPLACE_EXISTING);
                tempLib.toFile().deleteOnExit();
                tempDir.toFile().deleteOnExit();
                return Native.load(tempLib.toAbsolutePath().toString(), NativeLib.class);
            }
        } catch (IOException e) {
            // Fall through to java.library.path
        }

        // Fallback: try system library path
        try {
            return Native.load("apm", NativeLib.class);
        } catch (UnsatisfiedLinkError e) {
            throw new UnsatisfiedLinkError(
                    "Failed to load native library 'apm'. " +
                    "Platform: " + platform + ". " +
                    "Tried classpath resource: " + resourcePath + " and java.library.path. " +
                    "Ensure webrtc-audio-processing-natives is on the classpath for your platform, " +
                    "or set java.library.path to the directory containing " + libName + ".");
        }
    }

    private static String detectPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "");
        if (os.contains("mac")) {
            return "aarch64".equals(arch) ? "macos-aarch64" : "macos-x86_64";
        } else if (os.contains("linux")) {
            return "amd64".equals(arch) || "x86_64".equals(arch) ? "linux-x86_64" : "linux-aarch64";
        }
        throw new UnsupportedOperationException("Unsupported platform: " + os + " " + arch);
    }

    private static String libFileName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("mac") ? "libapm.dylib" : "libapm.so";
    }
}
