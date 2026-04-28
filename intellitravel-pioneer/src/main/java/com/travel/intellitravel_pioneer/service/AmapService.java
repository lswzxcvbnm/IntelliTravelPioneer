package com.travel.intellitravel_pioneer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.travel.intellitravel_pioneer.configure.AmapConfig;
import com.travel.intellitravel_pioneer.vo.trip.Location;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.util.Arrays;

@Service
public class AmapService {

    private static final Logger logger = LoggerFactory.getLogger(AmapService.class);
    
    private final AmapConfig amapConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AmapService(AmapConfig amapConfig, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.amapConfig = amapConfig;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public String geocode(String address) {
        JsonNode geocodeInfo = getGeocodeInfo(address);
        if (geocodeInfo == null || geocodeInfo.isNull() || !geocodeInfo.isArray() || geocodeInfo.size() == 0) {
            return null;
        }
        return geocodeInfo.get(0).path("location").asText(null);
    }

    public JsonNode getGeocodeInfo(String address) {
        return getGeocodeInfo(address, null);
    }

    public JsonNode getGeocodeInfo(String address, String targetCity) {
        if (address == null || address.trim().isEmpty()) {
            return null;
        }

        String trimmedAddress = address.trim();

        // 景点类关键词优先走 POI 搜索，提升命中率。
        if (isScenicKeyword(trimmedAddress)) {
            JsonNode poiFirst = tryResolveFromPoi(trimmedAddress, targetCity, "景点关键词优先POI");
            if (poiFirst != null) {
                return poiFirst;
            }
        }

        try {
            String baseUrl = amapConfig.getGeocodeUrl();
            if (baseUrl == null || baseUrl.isEmpty()) {
                baseUrl = "https://restapi.amap.com/v3/geocode/geo"; // 地理编码的默认URL
            }

            // 使用 UriComponentsBuilder 完美处理中文地址
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl)
                    .queryParam("key", amapConfig.getKey())
                    .queryParam("address", trimmedAddress);

                if (targetCity != null && !targetCity.trim().isEmpty()) {
                builder.queryParam("city", targetCity.trim())
                    .queryParam("citylimit", true);
                }

            URI uri = builder.build().encode().toUri();
            
            logger.info("调用高德 Geocode 接口 URL: {}", uri.toString());
            String response = restTemplate.getForObject(uri, String.class);
            logger.info("高德 Geocode 原始响应: {}", response);

            if (response != null) {
                JsonNode root = objectMapper.readTree(response);
                if ("1".equals(root.path("status").asText())) {
                    JsonNode geocodes = root.path("geocodes");
                    // 指定目标城市时，仅保留城市相关地址，避免返回跨城市结果。
                    return filterGeocodesByCity(geocodes, targetCity);
                } else {
                    logger.warn("高德地理编码接口返回错误: {}", root.path("info").asText());
                    if ("30001".equals(root.path("infocode").asText())) {
                        JsonNode fallback = tryResolveFromPoi(trimmedAddress, targetCity, "Geocode返回30001降级POI");
                        if (fallback != null) {
                            return fallback;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("地理编码请求异常", e);
        }
        return null;
    }

    private JsonNode tryResolveFromPoi(String keywords, String targetCity, String scene) {
        JsonNode pois = searchPoisByKeyword(keywords, null, targetCity, 1);
        if (pois == null || !pois.isArray() || pois.size() == 0) {
            return null;
        }

        JsonNode firstPoi = pois.get(0);
        String location = firstPoi.path("location").asText("");
        if (location.isEmpty()) {
            return null;
        }

        ObjectNode geocodeLike = objectMapper.createObjectNode();
        geocodeLike.put("location", location);
        geocodeLike.put("formatted_address", buildPoiFormattedAddress(firstPoi));
        geocodeLike.put("province", firstPoi.path("pname").asText(""));
        geocodeLike.put("city", firstPoi.path("cityname").asText(""));
        geocodeLike.put("district", firstPoi.path("adname").asText(""));
        geocodeLike.put("level", "兴趣点");
        geocodeLike.put("fallback_source", "poi");

        ArrayNode geocodes = objectMapper.createArrayNode();
        geocodes.add(geocodeLike);
        logger.info("{}，使用POI首条结果兜底坐标: {}", scene, location);
        return geocodes;
    }

    private String buildPoiFormattedAddress(JsonNode poi) {
        String[] parts = {
                poi.path("pname").asText(""),
                poi.path("cityname").asText(""),
                poi.path("adname").asText(""),
                poi.path("address").asText("")
        };

        StringBuilder builder = new StringBuilder();
        String lastAdded = "";
        for (String part : parts) {
            if (part == null) {
                continue;
            }
            String trimmed = part.trim();
            if (trimmed.isEmpty() || trimmed.equals(lastAdded)) {
                continue;
            }
            builder.append(trimmed);
            lastAdded = trimmed;
        }

        String fullAddress = builder.toString();
        String poiName = poi.path("name").asText("");
        if (!poiName.isEmpty() && !fullAddress.contains(poiName)) {
            if (!fullAddress.isEmpty()) {
                fullAddress += " ";
            }
            fullAddress += poiName;
        }

        if (!fullAddress.isEmpty()) {
            return fullAddress;
        }
        return poiName;
    }

    private boolean isScenicKeyword(String keyword) {
        if (keyword == null) {
            return false;
        }
        String normalized = keyword.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return false;
        }

        List<String> scenicTokens = Arrays.asList(
                "景区", "景点", "公园", "博物馆", "遗址", "古镇", "古城", "名胜", "寺", "塔", "山", "湖", "兵马俑"
        );
        for (String token : scenicTokens) {
            if (normalized.contains(token)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取天气信息
     *
     * @param adcode 城市编码 (adcode)
     * @param extensions 可选值: base (实况), all (预报)
     * @return 天气信息的 JsonNode (lives 或 forecasts 节点)，如果失败返回 null
     */
    public JsonNode getWeatherInfo(String adcode, String extensions) {
        if (adcode == null || adcode.trim().isEmpty()) {
            return null;
        }

        try {
            String baseUrl = amapConfig.getWeatherUrl();
            // 如果没配置 url，给个默认值防止空指针，方便直接使用
            if (baseUrl == null || baseUrl.isEmpty()) {
                baseUrl = "https://restapi.amap.com/v3/weather/weatherInfo";
            }
            String key = amapConfig.getKey();
            
            // 简单的字符串拼接
            String ext = (extensions != null && !extensions.isEmpty()) ? extensions : "base";
            String url = String.format("%s?city=%s&key=%s&extensions=%s", baseUrl, adcode, key, ext);
            
            logger.debug("请求高德天气接口: {}", url);

            String response = restTemplate.getForObject(url, String.class);

            if (response != null) {
                JsonNode root = objectMapper.readTree(response);
                if ("1".equals(root.path("status").asText())) {
                     if ("all".equals(ext)) {
                         return root.path("forecasts");
                     } else {
                         return root.path("lives");
                     }
                } else {
                    logger.warn("高德天气接口返回错误: {}", root.path("info").asText());
                }
            }
        } catch (Exception e) {
            logger.error("获取天气信息失败", e);
        }
        return null; 
    }
        // ...existing code... (geocode 和 getWeatherInfo 方法保持不变)

    /**
     * 关键字搜索 POI (例如：搜索"故宫"，或者搜索"加油站" 限定城市)
     * 
     * @param keywords 关键字 (必填)
     * @param types POI类型编码 (可选，例如: 010000 汽车服务)
     * @param city 城市名称或adcode (可选)
     * @param page 页码 (默认1)
     * @return POI 列表 JsonNode
     */
    public JsonNode searchPoisByKeyword(String keywords, String types, String city, int page) {
        if (keywords == null || keywords.trim().isEmpty()) {
            return null;
        }

        try {
            String baseUrl = amapConfig.getPlaceTextUrl();
            if (baseUrl == null || baseUrl.isEmpty()) {
                baseUrl = "https://restapi.amap.com/v3/place/text";
            }

            // 使用 Spring 官方的 UriComponentsBuilder，杜绝所有奇葩转码问题
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl)
                    .queryParam("key", amapConfig.getKey())
                    .queryParam("keywords", keywords.trim())
                    .queryParam("offset", 20)
                    .queryParam("page", page > 0 ? page : 1)
                    .queryParam("extensions", "all");

            if (types != null && !types.trim().isEmpty()) {
                builder.queryParam("types", types.trim());
            }
            if (city != null && !city.trim().isEmpty()) {
                builder.queryParam("city", city.trim());
                // 开启严格城市限制，避免出现不相关城市的 POI。
                builder.queryParam("citylimit", true);
            }

            // 构建最终的 URI 对象 (这能避免 RestTemplate 对传入的 String 强行二次转码)
            URI uri = builder.build().encode().toUri();
            return fetchAndExtractPois(uri);
        } catch (Exception e) {
            logger.error("POI关键字搜索失败", e);
        }
        return null;
    }

    private JsonNode filterGeocodesByCity(JsonNode geocodes, String targetCity) {
        if (geocodes == null || geocodes.isNull() || !geocodes.isArray()) {
            return geocodes;
        }
        if (targetCity == null || targetCity.trim().isEmpty()) {
            return geocodes;
        }

        String normalizedTarget = normalizeCity(targetCity);
        ArrayNode filtered = objectMapper.createArrayNode();

        for (JsonNode geocode : geocodes) {
            String city = normalizeCity(geocode.path("city").asText(""));
            String district = normalizeCity(geocode.path("district").asText(""));
            String province = normalizeCity(geocode.path("province").asText(""));
            String formattedAddress = normalizeCity(geocode.path("formatted_address").asText(""));

            if (city.contains(normalizedTarget)
                    || district.contains(normalizedTarget)
                    || province.contains(normalizedTarget)
                    || formattedAddress.contains(normalizedTarget)) {
                filtered.add(geocode);
            }
        }
        return filtered;
    }

    private String normalizeCity(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("市", "")
                .replace("省", "")
                .replace("特别行政区", "")
                .trim()
                .toLowerCase();
    }

    /**
     * 周边搜索 POI (例如：搜索当前位置附近的"厕所"或"充电站")
     * 
     * @param location 中心点坐标 "经度,纬度" (必填)
     * @param keywords 关键字 (可选)
     * @param types POI类型编码 (可选)
     * @param radius 搜索半径 (米，默认3000)
     * @return POI 列表 JsonNode
     */
    public JsonNode searchPoisAround(String location, String keywords, String types, Integer radius) {
        if (location == null || location.trim().isEmpty()) {
            return null;
        }

        try {
            String baseUrl = amapConfig.getPlaceAroundUrl();
            if (baseUrl == null || baseUrl.isEmpty()) {
                baseUrl = "https://restapi.amap.com/v3/place/around";
            }

            int r = (radius != null && radius > 0) ? radius : 3000;
            if (r > 50000) r = 50000;

            // 使用 UriComponentsBuilder
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl)
                    .queryParam("key", amapConfig.getKey())
                    .queryParam("location", location.trim())
                    .queryParam("radius", r)
                    .queryParam("offset", 20)
                    .queryParam("page", 1)
                    .queryParam("extensions", "all");

            if (keywords != null && !keywords.trim().isEmpty()) {
                builder.queryParam("keywords", keywords.trim());
            }
            if (types != null && !types.trim().isEmpty()) {
                builder.queryParam("types", types.trim());
            }

            // 构建安全的 URI 对象
            URI uri = builder.build().encode().toUri();
            return fetchAndExtractPois(uri);
        } catch (Exception e) {
            logger.error("POI周边搜索失败", e);
        }
        return null;
    }

    // 辅助方法：发送请求并提取 pois 节点
    private JsonNode fetchAndExtractPois(URI uri) {
        try {
            // 绝杀点：传入 URI 对象而不是 String，打断 RestTemplate 的不可控二次编码
            String response = restTemplate.getForObject(uri, String.class);

            if (response != null) {
                JsonNode root = objectMapper.readTree(response);
                if ("1".equals(root.path("status").asText())) {
                    return root.path("pois");
                } else {
                    logger.warn("高德POI接口返回错误: {}", root.path("info").asText());
                }
            }
        } catch (Exception e) {
            logger.error("请求高德API异常", e);
        }
        return null;
    }


    /**
     * 生成带标签的静态地图图片 URL
     * 
     * @param location 中心点 "经度,纬度"
     * @param zoom 缩放级别 (1-17)
     * @param labels 标签列表 (格式: "经度,纬度,标签名;经度,纬度,标签名")
     * @return 静态地图图片的完整 URL
     */
    public String generateStaticMapUrl(String centerLocation, Integer zoom, String labels) {
        try {
            String baseUrl = amapConfig.getStaticMapUrl();
            if (baseUrl == null || baseUrl.isEmpty()) {
                baseUrl = "https://restapi.amap.com/v3/staticmap";
            }

            StringBuilder urlBuilder = new StringBuilder(baseUrl)
                    .append("?key=").append(amapConfig.getKey())
                    .append("&size=750*400")
                    .append("&scale=2");

            boolean hasMarkers = false;
            if (labels != null && !labels.trim().isEmpty()) {
                StringBuilder markers = new StringBuilder();
                String[] arr = labels.split(";");
                for (int i = 0; i < arr.length; i++) {
                    String[] point = arr[i].split(",");
                    if (point.length >= 2) {
                        if (markers.length() > 0) markers.append("|");
                        String lng = point[0].trim();
                        String lat = point[1].trim();
                        String tag = (point.length >= 3 && !point[2].trim().isEmpty())
                                ? point[2].trim()
                                : String.valueOf((char) ('A' + (i % 26)));
                        
                        // 组装标记点，格式: mid,0xFF0000,A:116.39,39.90
                        markers.append("mid,0xFF0000,").append(tag).append(":").append(lng).append(",").append(lat);
                    }
                }
                if (markers.length() > 0) {
                    urlBuilder.append("&markers=").append(markers.toString());
                    hasMarkers = true;
                }
            }

            if (!hasMarkers && centerLocation != null && !centerLocation.trim().isEmpty()) {
                // 未传labels时，按高德markers规范标出目标点，避免前端额外拼接参数。
                urlBuilder.append("&markers=mid,0xFF0000,T:").append(centerLocation.trim());
                hasMarkers = true;
            }

            if (centerLocation != null && !centerLocation.trim().isEmpty()) {
                urlBuilder.append("&location=").append(centerLocation.trim());
            } else if (!hasMarkers) {
                urlBuilder.append("&location=116.397428,39.90923");
            }

            if (zoom != null && zoom > 0) {
                urlBuilder.append("&zoom=").append(zoom);
            }

            String finalUrl = urlBuilder.toString();
            logger.info("高德极简直拼URL: {}", finalUrl);
            return finalUrl;

        } catch (Exception e) {
            logger.error("生成静态地图URL失败", e);
            return null;
        }
    }

    /**
     * 生成轨迹图 URL（路径 + 起终点标记）（文具盒AI生成）
     */
    public String generateTrajectoryMapUrl(List<Location> trajectory) {
        if (trajectory == null || trajectory.isEmpty()) {
            return null;
        }
        try {
            String baseUrl = amapConfig.getStaticMapUrl();
            if (baseUrl == null || baseUrl.isEmpty()) {
                baseUrl = "https://restapi.amap.com/v3/staticmap";
            }

            List<Location> validTrajectory = new ArrayList<>();
            for (Location location : trajectory) {
                if (location == null || location.getLng() == null || location.getLat() == null) {
                    continue;
                }
                validTrajectory.add(location);
            }
            if (validTrajectory.isEmpty()) {
                return null;
            }
            if (validTrajectory.size() == 1) {
                Location point = validTrajectory.get(0);
                String centerLocation = point.getLng() + "," + point.getLat();
                return generateStaticMapUrl(centerLocation, 16, null);
            }

            Location start = validTrajectory.get(0);
            Location end = validTrajectory.get(validTrajectory.size() - 1);

            StringBuilder pathBuilder = new StringBuilder();
            for (Location location : validTrajectory) {
                if (pathBuilder.length() > 0) {
                    pathBuilder.append(";");
                }
                pathBuilder.append(location.getLng()).append(",").append(location.getLat());
            }

            if (pathBuilder.length() == 0) {
                return null;
            }

            StringBuilder markers = new StringBuilder();
            if (start != null && start.getLng() != null && start.getLat() != null) {
                markers.append("mid,0x00AA00,S:")
                        .append(start.getLng()).append(",").append(start.getLat());
            }
            if (end != null && end.getLng() != null && end.getLat() != null) {
                if (markers.length() > 0) {
                    // 使用 %7C 替代 |，因为 | 是 URL 非法字符，会导致 URI.create 报错
                    markers.append("%7C");
                }
                markers.append("mid,0xDD0000,E:")
                        .append(end.getLng()).append(",").append(end.getLat());
            }

            // 改用 StringBuilder 手动拼接，避免 UriComponentsBuilder 自动编码逗号和冒号
            StringBuilder urlBuilder = new StringBuilder(baseUrl);
            urlBuilder.append("?key=").append(amapConfig.getKey())
                      .append("&size=750*400")
                      .append("&scale=2")
                      .append("&traffic=0");
            
            // 拼接 paths
            // 格式参考：weight,color,transparency,fillcolor,fillTransparency:location...
            // 使用 8,0x3366FF,1,,: 确保正确解析
            urlBuilder.append("&paths=8,0x3366FF,1,,:").append(pathBuilder);
            
            if (markers.length() > 0) {
                urlBuilder.append("&markers=").append(markers);
            }
            
            String finalUrl = urlBuilder.toString();
            logger.info("生成轨迹静态地图URL: {}", finalUrl);

            return finalUrl;
        } catch (Exception e) {
            logger.error("生成轨迹图URL失败", e);
            return null;
        }
    }

    /**
     * 获取静态地图的图片字节流
     */
    @SuppressWarnings("deprecation")
    public byte[] getStaticMapImageBytes(String centerLocation, int zoom, String labels) {
        String urlString = generateStaticMapUrl(centerLocation, zoom, labels);
        if (urlString == null) {
            return null;
        }

        try {
            // 绝杀：放弃 RestTemplate/URI，改用原生 HttpURLConnection，避开强制转码报错
            URL amapUrl = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) amapUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            // 获取图片流
            try (InputStream in = connection.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                logger.info("成功获取高德静态地图图片流，大小: {} bytes", out.size());
                return out.toByteArray();
            }
        } catch (Exception e) {
            logger.error("获取高德静态地图图片原生请求失败", e);
            return null;
        }
    }

    public JsonNode getRoutePlanning(String origin, String destination, String type, String city) {
        String url;
        
        switch (type.toLowerCase()) {
            case "walking":
            case "walk":
                url = "https://restapi.amap.com/v3/direction/walking?key=" + amapConfig.getKey() +
                      "&origin=" + origin + "&destination=" + destination;
                return getV3(url);

            case "transit":
            case "bus":
                if (city == null || city.isEmpty()) {
                    return null; 
                }
                // default strategy=0: speed priority
                url = "https://restapi.amap.com/v3/direction/transit/integrated?key=" + amapConfig.getKey() +
                      "&origin=" + origin + "&destination=" + destination + 
                      "&city=" + city + "&strategy=0";
                return getV3(url);

            case "driving":
            case "car":
                // 10: avoid congestion
                url = "https://restapi.amap.com/v3/direction/driving?key=" + amapConfig.getKey() +
                      "&origin=" + origin + "&destination=" + destination + 
                      "&strategy=10";
                return getV3(url);

            case "bicycling":
            case "bike":
                return getBicycling(origin, destination);
                
            default:
                return null;
        }
    }

    private JsonNode getV3(String url) {
        try {
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            if (root.has("status") && "1".equals(root.get("status").asText())) {
               return root.path("route");
            }
            logger.error("Route planning V3 failed: {}", response);
        } catch (Exception e) {
            logger.error("Error calling Amap route planning V3: ", e);
        }
        return null;
    }

    private JsonNode getBicycling(String origin, String destination) {
        // V4 API
        String url = "https://restapi.amap.com/v4/direction/bicycling?key=" + amapConfig.getKey() + 
                     "&origin=" + origin + "&destination=" + destination;
        try {
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
             if (root.has("errcode") && root.get("errcode").asInt() == 0) {
                return root.path("data");
            }
            logger.error("Route planning V4 failed: {}", response);
        } catch (Exception e) {
             logger.error("Error calling Amap route planning V4: ", e);
        }
        return null;
    }

    private JsonNode getRawRoute(String origin, String destination, String type, String city) {
        // reuse getRoutePlanning but return full root for duration extraction
        switch (type.toLowerCase()) {
            case "walking":
            case "walk":
                return getV3Raw("https://restapi.amap.com/v3/direction/walking?key=" + amapConfig.getKey()
                        + "&origin=" + origin + "&destination=" + destination);
            case "transit":
            case "bus":
                if (city == null || city.isEmpty()) return null;
                return getV3Raw("https://restapi.amap.com/v3/direction/transit/integrated?key=" + amapConfig.getKey()
                        + "&origin=" + origin + "&destination=" + destination + "&city=" + city + "&strategy=0");
            case "driving":
            case "car":
                return getV3Raw("https://restapi.amap.com/v3/direction/driving?key=" + amapConfig.getKey()
                        + "&origin=" + origin + "&destination=" + destination + "&strategy=10");
            case "bicycling":
            case "bike":
                return getV4Raw("https://restapi.amap.com/v4/direction/bicycling?key=" + amapConfig.getKey()
                        + "&origin=" + origin + "&destination=" + destination);
            default:
                return null;
        }
    }

    private JsonNode getV3Raw(String url) {
        try {
            String response = restTemplate.getForObject(url, String.class);
            return objectMapper.readTree(response);
        } catch (Exception e) {
            logger.error("Error calling AmapV3 raw: ", e);
        }
        return null;
    }

    private JsonNode getV4Raw(String url) {
        try {
            String response = restTemplate.getForObject(url, String.class);
            return objectMapper.readTree(response);
        } catch (Exception e) {
            logger.error("Error calling AmapV4 raw: ", e);
        }
        return null;
    }

    public Integer estimateTravelTime(String origin, String destination, String type, String city) {
        JsonNode root = getRawRoute(origin, destination, type, city);
        if (root == null) return null;
        switch (type.toLowerCase()) {
            case "walking":
            case "walk":
            case "driving":
            case "car":
                if (root.has("route") && root.path("route").has("paths")
                        && root.path("route").path("paths").isArray()
                        && root.path("route").path("paths").size() > 0) {
                    return root.path("route").path("paths").get(0).path("duration").asInt(-1);
                }
                break;
            case "transit":
            case "bus":
                if (root.has("route") && root.path("route").has("transits")
                        && root.path("route").path("transits").isArray()
                        && root.path("route").path("transits").size() > 0) {
                    return root.path("route").path("transits").get(0).path("duration").asInt(-1);
                }
                break;
            case "bicycling":
            case "bike":
                if (root.has("data") && root.path("data").has("paths")
                        && root.path("data").path("paths").isArray()
                        && root.path("data").path("paths").size() > 0) {
                    return root.path("data").path("paths").get(0).path("duration").asInt(-1);
                }
                break;
        }
        return null;
    }

    public JsonNode getFastestRoute(String origin, String destination, String city) {
        String[] modes = {"walking", "driving", "transit", "bicycling"};
        String bestMode = null;
        Integer bestTime = null;
        for (String m : modes) {
            Integer t = estimateTravelTime(origin, destination, m, city);
            if (t != null && t >= 0) {
                if (bestTime == null || t < bestTime) {
                    bestTime = t;
                    bestMode = m;
                }
            }
        }
        if (bestMode == null) return null;
        JsonNode route = getRoutePlanning(origin, destination, bestMode, city);
        ObjectNode result = objectMapper.createObjectNode();
        result.put("mode", bestMode);
        result.put("duration", bestTime);
        result.set("route", route);
        return result;
    }

}