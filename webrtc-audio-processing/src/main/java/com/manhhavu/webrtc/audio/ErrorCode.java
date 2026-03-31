package com.manhhavu.webrtc.audio;

public enum ErrorCode {
    UNSPECIFIED(-1),
    INITIALIZATION_FAILED(-2),
    UNSUPPORTED_COMPONENT(-3),
    UNSUPPORTED_FUNCTION(-4),
    NULL_POINTER(-5),
    BAD_PARAMETER(-6),
    BAD_SAMPLE_RATE(-7),
    BAD_DATA_LENGTH(-8),
    BAD_NUMBER_CHANNELS(-9),
    FILE_ERROR(-10),
    STREAM_PARAMETER_NOT_SET(-11),
    NOT_ENABLED(-12);

    private final int code;

    ErrorCode(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static ErrorCode fromCode(int code) {
        for (ErrorCode ec : values()) {
            if (ec.code == code) return ec;
        }
        return UNSPECIFIED;
    }
}
