package com.travel.intellitravel_pioneer.po.converter;

import com.travel.intellitravel_pioneer.util.JsonConverter;
import com.travel.intellitravel_pioneer.vo.trip.UserRealTimeStatus;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class UserRealTimeStatusJsonConverter extends JsonConverter<UserRealTimeStatus> {
    public UserRealTimeStatusJsonConverter() {
        super(UserRealTimeStatus.class);
    }
}
