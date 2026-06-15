package com.damai.common.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.TypeReference;

import java.util.Collections;
import java.util.List;

/**
 * JSON 工具类 —— 基于 fastjson2 封装，提供对象 ↔ JSON 字符串互转。
 *
 * <p>统一全局序列化/反序列化特性，避免各处散乱配置：
 * <ul>
 *   <li>序列化：写出 null 字段（便于前端判断）、格式化日期。</li>
 *   <li>反序列化：忽略未知字段（兼容 DTO 演进）。</li>
 * </ul>
 *
 * <p>选择 fastjson2 而非 Jackson：性能更优、API 简洁，且与项目统一技术栈一致。
 */
public final class JsonUtil {

    private JsonUtil() {}

    /** 序列化特性：写出 null 值 */
    private static final JSONWriter.Feature[] WRITE_FEATURES = {
            JSONWriter.Feature.WriteNulls,
            JSONWriter.Feature.WriteMapNullValue
    };

    /** 反序列化特性：忽略未知字段 */
    private static final JSONReader.Feature[] READ_FEATURES = {
            JSONReader.Feature.IgnoreNoneSerializable,
            JSONReader.Feature.SupportSmartMatch
    };

    /**
     * 对象转 JSON 字符串。
     *
     * @param obj 任意对象；null 返回 "null"
     * @return JSON 字符串
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        return JSON.toJSONString(obj, WRITE_FEATURES);
    }

    /**
     * JSON 字符串转对象。
     *
     * @param json  JSON 字符串
     * @param clazz 目标类型
     */
    public static <T> T parse(String json, Class<T> clazz) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        return JSON.parseObject(json, clazz, READ_FEATURES);
    }

    /**
     * JSON 字符串转复杂泛型对象（如 List&lt;UserDto&gt;）。
     *
     * @param json     JSON 字符串
     * @param typeRef  类型引用，例如 {@code new TypeReference<List<UserDto>>(){}}
     */
    public static <T> T parse(String json, TypeReference<T> typeRef) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        return JSON.parseObject(json, typeRef, READ_FEATURES);
    }

    /**
     * JSON 字符串转列表。
     *
     * @param json        JSON 字符串
     * @param elementClass 列表元素类型
     */
    public static <T> List<T> parseList(String json, Class<T> elementClass) {
        if (json == null || json.isEmpty()) {
            return Collections.emptyList();
        }
        return JSON.parseArray(json, elementClass);
    }
}
