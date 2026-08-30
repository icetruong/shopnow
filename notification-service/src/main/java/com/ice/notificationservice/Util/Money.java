package com.ice.notificationservice.Util;

public final class Money {

    private Money() {}

    /** 448200 -> "448,200". null -> "0". Dùng cho template. */
    public static String vnd(Long amount) {
        return String.format("%,d", amount == null ? 0L : amount);
    }
}
