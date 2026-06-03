package com.ewallet.common;

public final class MdcConstants {

    public static final String REQUEST_ID = "requestId";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private MdcConstants() {
    }
}
