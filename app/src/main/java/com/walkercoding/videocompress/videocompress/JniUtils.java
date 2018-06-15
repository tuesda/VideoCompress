package com.walkercoding.videocompress.videocompress;

import java.nio.ByteBuffer;

public class JniUtils {
    public native static int convertVideoFrame(ByteBuffer src, ByteBuffer dest, int destFormat, int width, int height, int padding, int swap);
}