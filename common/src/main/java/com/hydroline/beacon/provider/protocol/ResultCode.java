package com.hydroline.beacon.provider.protocol;

/**
 * Lightweight status enum mirrored on Bukkit side for quick parsing.
 */
public enum ResultCode {
    OK,
    BUSY,
    INVALID_ACTION,
    INVALID_PAYLOAD,
    NOT_READY,
    ERROR
}
