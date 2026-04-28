package com.travel.intellitravel_pioneer.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.io.IOException;

public abstract class JsonConverter<T> implements AttributeConverter<T, String> {

    private static final Logger log = LoggerFactory.getLogger(JsonConverter.class);
    private static final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private final Class<T> clazz;

    public JsonConverter(Class<T> clazz) {
        this.clazz = clazz;
    }

    @Override
    public String convertToDatabaseColumn(T attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            log.error("Error converting object to JSON string", e);
            return null;
        }
    }

    @Override
    public T convertToEntityAttribute(String dbData) {
        if (!StringUtils.hasText(dbData)) {
            return null;
        }
        try {
            return objectMapper.readValue(dbData, clazz);
        } catch (IOException e) {
            log.error("Error converting JSON string to object", e);
            return null;
        }
    }
}
