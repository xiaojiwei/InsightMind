package com.graphinsight.indicator.util;

import java.util.UUID;

/**
 * Class UuidUtil
 * Description: UuidUtil
 *
 * @Author: tongxuejie <tongxuejie@graphinsight.com>
 * Date 2024/2/23 10:00
 */
public class UuidUtil {
    /**
     * @return 长度36
     */
    public static String getUUID() {
        return UUID.randomUUID().toString();
    }

    /**
     * @return 长度32
     */
    public static String getUUID32() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }

    /**
     * @return 长度36
     */
    public static String getUUID(String name) {
        return UUID.fromString(name).toString();
    }

    /**
     * @return 长度32
     */
    public static String getUUID32(String name) {
        return UUID.fromString(name).toString().replaceAll("-", "");
    }

    /**
     * @return 长度36
     */
    public static String getUUID(byte[] name) {
        return UUID.nameUUIDFromBytes(name).toString();
    }

    /**
     * @return 长度32
     */
    public static String getUUID32(byte[] name) {
        return UUID.nameUUIDFromBytes(name).toString().replaceAll("-", "");
    }
}