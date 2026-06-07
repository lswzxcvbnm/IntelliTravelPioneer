package com.travel.intellitravel_pioneer.po.converter;

import com.travel.intellitravel_pioneer.util.JsonConverter;

import java.util.Map;

/**
 * JPA {@code AttributeConverter} that maps {@code Map<String, String>} ↔ JSON TEXT column.
 * Used for Skill references and scripts.
 */
public class StringMapJsonConverter extends JsonConverter<Map<String, String>> {
    public StringMapJsonConverter() {
        super((Class) Map.class);
    }
}
