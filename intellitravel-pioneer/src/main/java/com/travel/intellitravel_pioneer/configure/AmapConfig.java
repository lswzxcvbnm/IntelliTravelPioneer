package com.travel.intellitravel_pioneer.configure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "amap")
public class AmapConfig {
    private String key;
    // 注意：Spring Boot 绑定属性时，属性名 "geocode-url" 会自动映射到驼峰命名的 "geocodeUrl"
    private String geocodeUrl; 
    private String weatherUrl;
    private String placeTextUrl;
    private String placeAroundUrl;
    private String staticMapUrl; 

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getGeocodeUrl() {
        return geocodeUrl;
    }

    public void setGeocodeUrl(String geocodeUrl) {
        this.geocodeUrl = geocodeUrl;
    }

    public String getWeatherUrl() {
        return weatherUrl;
    }

    public void setWeatherUrl(String weatherUrl) {
        this.weatherUrl = weatherUrl;
    }

    // 新增 getter/setter
    public String getPlaceTextUrl() {
        return placeTextUrl;
    }

    public void setPlaceTextUrl(String placeTextUrl) {
        this.placeTextUrl = placeTextUrl;
    }

    public String getPlaceAroundUrl() {
        return placeAroundUrl;
    }

    public void setPlaceAroundUrl(String placeAroundUrl) {
        this.placeAroundUrl = placeAroundUrl;
    }
    public String getStaticMapUrl() {
        return staticMapUrl;
    }

    public void setStaticMapUrl(String staticMapUrl) {
        this.staticMapUrl = staticMapUrl;
    }
}