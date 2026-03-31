package com.manhhavu.webrtc.audio;

public class ProcessorException extends RuntimeException {
    private final ErrorCode errorCode;

    public ProcessorException(ErrorCode errorCode) {
        super("WebRTC audio processing error: " + errorCode.name());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    static void checkResult(int result) {
        if (result < 0) {
            throw new ProcessorException(ErrorCode.fromCode(result));
        }
    }

    static void checkHandle(long handle) {
        if (handle <= 0) {
            throw new ProcessorException(ErrorCode.fromCode((int) handle));
        }
    }
}
