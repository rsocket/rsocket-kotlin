package io.rsocket.android.frame;

class Utils {
    static final int SHORT_BYTES = toBytes(Short.SIZE);
    static final int INTEGER_BYTES = toBytes(Integer.SIZE);
    static final int LONG_BYTES = toBytes(Long.SIZE);

    private static int toBytes(int size) {
        return size / Byte.SIZE;
    }
}
