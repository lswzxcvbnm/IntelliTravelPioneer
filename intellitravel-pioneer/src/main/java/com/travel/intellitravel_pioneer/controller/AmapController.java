package com.travel.intellitravel_pioneer.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.travel.intellitravel_pioneer.service.AmapService;
import com.travel.intellitravel_pioneer.util.TokenUtil;
import com.travel.intellitravel_pioneer.exception.IntellitravelException;
import com.travel.intellitravel_pioneer.po.UserPO;
import com.travel.intellitravel_pioneer.vo.common.Response;
import com.travel.intellitravel_pioneer.vo.trip.Location;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/amap")
public class AmapController {

    private final AmapService amapService;
    private final ObjectMapper objectMapper;
    private final TokenUtil tokenUtil;
    private final Map<Integer, String> geocodeCityCache = new ConcurrentHashMap<>();

    public AmapController(AmapService amapService, ObjectMapper objectMapper, TokenUtil tokenUtil) {
        this.amapService = amapService;
        this.objectMapper = objectMapper;
        this.tokenUtil = tokenUtil;
    }

    private void validateToken(String token) {
        if (token == null || token.isEmpty()) {
            throw new IntellitravelException("Token is missing");
        }
        try {
            UserPO user = tokenUtil.getUser(token);
            if (user == null) {
                throw new IntellitravelException("Invalid token");
            }
        } catch (Exception e) {
            throw new IntellitravelException("Invalid token format");
        }
    }

    /**
     * 获取高德天气信息
     *
     * @param city       城市 adcode (例如：110000)
     * @param extensions 可选值: base (实况), all (预报)
     * @return 天气信息的 JsonNode
     */
    @GetMapping("/weather")
    public Response<Object> getWeather( // 修改这里：JsonNode -> Object
            @RequestHeader(value = "token") String token,
            @RequestParam String city,
            @RequestParam(defaultValue = "base") String extensions) {
        JsonNode weatherInfo = amapService.getWeatherInfo(city, extensions);
        if (weatherInfo != null) {
            Object result = objectMapper.convertValue(weatherInfo, Object.class);
            return Response.buildSuccess(result);
        } else {
            return Response.buildFailure("获取天气信息失败", "500");
        }
    }

    /**
     * 关键字搜索 POI (例如：搜“故宫”)
     * 
     * @param keywords 关键字
     * @param city     城市 (可选)
     * @param types    类型 (可选)
     * @param page     页码 (默认1)
     */
    @GetMapping("/poi/text")
    public Response<Object> searchPoiText(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam String keywords,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String types,
            @RequestParam(defaultValue = "1") int page) {

        JsonNode pois = amapService.searchPoisByKeyword(keywords, types, city, page);

        if (pois != null) {
            // 同样强制转换为 List/Map，避免 Jackson 序列化异常
            Object result = objectMapper.convertValue(pois, Object.class);
            return Response.buildSuccess(result);
        } else {
            // 如果没搜到或报错，通常返回空列表比报错好，视业务而定，这里保持一致
            return Response.buildFailure("POI搜索失败", "500");
        }
    }

    /**
     * 周边搜索 POI (例如：搜附近的“厕所”)
     * 
     * @param location 中心点 "经度,纬度"
     * @param keywords 关键字 (可选)
     * @param radius   半径 (默认3000米)
     */
    @GetMapping("/poi/around")
    public Response<Object> searchPoiAround(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam String location,
            @RequestParam(required = false) String keywords,
            @RequestParam(required = false) String types,
            @RequestParam(defaultValue = "3000") Integer radius) {

        JsonNode pois = amapService.searchPoisAround(location, keywords, types, radius);

        if (pois != null) {
            Object result = objectMapper.convertValue(pois, Object.class);
            return Response.buildSuccess(result);
        } else {
            return Response.buildFailure("周边搜索失败", "500");
        }
    }

    /**
     * 地理编码 (地址 -> 经纬度)
     * 
     * @param address 地址 (例如 "北京市朝阳区阜通东大街6号")
     * @param city    目标城市(可选)，用于过滤掉非目标城市结果
     */
    @GetMapping("/geocode")
    public Response<Object> getGeocode(
            @RequestHeader(value = "token") String token,
            @RequestParam String address,
            @RequestParam(required = false) String city) {
        validateToken(token);
        UserPO user = tokenUtil.getUser(token);

        String effectiveCity = city;
        if (effectiveCity != null && !effectiveCity.trim().isEmpty()) {
            effectiveCity = effectiveCity.trim();
            geocodeCityCache.put(user.getId(), effectiveCity);
        } else {
            effectiveCity = geocodeCityCache.get(user.getId());
        }

        System.out.println("User " + user.getId() + " requested geocode for address '" + address
                + "' with effective city '" + effectiveCity + "'");
        // 调用 Service 获取完整 Geocode 信息，指定城市时仅返回目标城市相关结果
        JsonNode geocodeInfo = amapService.getGeocodeInfo(address, effectiveCity);

        if (geocodeInfo != null) {
            // 同样强制转换为 Map，避免 Jackson 序列化异常
            System.out.println("Raw geocode info for address '" + address + "' with city '" + effectiveCity + "': "
                    + geocodeInfo.toString());
            Object result = objectMapper.convertValue(geocodeInfo, Object.class);
            System.out.println(
                    "Geocode result for address '" + address + "' with city '" + effectiveCity + "': " + result);
            return Response.buildSuccess(result);
        } else {
            return Response.buildFailure("地理编码失败或地址不存在", "500");
        }
    }

    /**
     * 获取静态地图图片流 (直接在浏览器显示图片)
     * 
     * @param location 中心点 "经度,纬度"
     * @param zoom     缩放级别 (默认15)
     * @param labels   标记点坐标列表 (例如 "116.397026,39.918058;116.397428,39.90923")
     */
    @GetMapping("/static-map/image")
    public ResponseEntity<byte[]> getStaticMapImage(
            @RequestParam(required = false) String token, // 对于图片流，通常使用query param传递token比较方便，或者不做鉴权。这里根据需求加上。
            @RequestParam String location,
            @RequestParam(required = false, defaultValue = "15") Integer zoom,
            @RequestParam(required = false) String labels) {

        // 注意：静态资源请求头可能不方便带token，通常允许query param传递
        // 如果严格要求鉴权:
        // validateToken(token);
        // 但前端img src一般不方便设置header。这里暂时可选鉴权，或者依赖query param。
        // 根据“改为强制要求token吧”的需求，这里加上校验。

        if (token == null || token.isEmpty()) {
            // 图片接口返回401或错误图
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            validateToken(token);
        } catch (IntellitravelException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        byte[] imageBytes = amapService.getStaticMapImageBytes(location, zoom, labels);

        if (imageBytes != null && imageBytes.length > 0) {
            HttpHeaders headers = new HttpHeaders();
            // 告诉前端/浏览器，返回的是一张 PNG 图片
            headers.setContentType(MediaType.IMAGE_PNG);
            return new ResponseEntity<>(imageBytes, headers, HttpStatus.OK);
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 获取交通路径规划 (驾车 / 步行 / 公交 / 骑行)
     * 
     * @param origin      起点 "lon,lat"
     * @param destination 终点 "lon,lat"
     * @param type        方式 (walking, transit, driving, bicycling)
     * @param city        城市 (公交必填)
     */
    @GetMapping("/route")
    public Response<Object> getRoute(
            @RequestHeader(value = "token") String token,
            @RequestParam String origin,
            @RequestParam String destination,
            @RequestParam String type,
            @RequestParam(required = false) String city) {

        validateToken(token);

        JsonNode route = amapService.getRoutePlanning(origin, destination, type, city);

        if (route != null) {
            Object result = objectMapper.convertValue(route, Object.class);
            return Response.buildSuccess(result);
        } else {
            return Response.buildFailure("路径规划失败 (请检查参数或API额度)", "500");
        }
    }

    /**
     * 获取两个点之间用时最短的交通方式及路径
     * 
     * @param origin      起点 "lon,lat"
     * @param destination 终点 "lon,lat"
     * @param city        公交需要的城市，可选
     */
    @GetMapping("/route/fastest")
    public Response<Object> getFastestRoute(
            @RequestHeader(value = "token") String token,
            @RequestParam String origin,
            @RequestParam String destination,
            @RequestParam(required = false) String city) {
        validateToken(token);
        JsonNode result = amapService.getFastestRoute(origin, destination, city);
        if (result != null) {
            Object obj = objectMapper.convertValue(result, Object.class);
            return Response.buildSuccess(obj);
        } else {
            return Response.buildFailure("无法计算最快方式", "500");
        }
    }

    /**
     * 生成并返回静态地图的轨迹截图 (需传入路经点列表)
     *
     * @param trajectory 路径点列表，JSON 数组格式，示例：
     *                   ```
     *                   [
     *                   {"lng":116.390291,"lat":39.897445},
     *                   {"lng":116.397428,"lat":39.90923},
     *                   {"lng":116.403963,"lat":39.91409}
     *                   ]
     *                   ```
     *                   每个元素为经纬度对象，字段名必须为 `lng` 和 `lat`，Content-Type:
     *                   application/json。
     */
    @PostMapping("/static-map/trajectory")
    public ResponseEntity<byte[]> getTrajectoryStaticMap(
            @RequestHeader(value = "token", required = false) String tokenHeader,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "token", required = false) String queryToken,
            @RequestBody java.util.List<Location> trajectory) {

        // 优先级：tokenHeader -> Authorization(Bearer) -> queryToken
        String token = null;
        if (tokenHeader != null && !tokenHeader.isEmpty()) {
            token = tokenHeader;
        } else if (authHeader != null && !authHeader.isEmpty()) {
            if (authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            } else {
                token = authHeader; // 允许直接传 raw token
            }
        } else {
            token = queryToken;
        }

        if (token == null || token.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            validateToken(token);
        } catch (IntellitravelException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 1. 生成 URL
        String mapUrl = amapService.generateTrajectoryMapUrl(trajectory);
        if (mapUrl == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }

        // 2. 下载图片并返回流 (复用 getStaticMapImageBytes 类似的逻辑，或者直接在这里下载)
        // 由于 AmapService 里只有 getStaticMapImageBytes 是根据 param 拼接 URL 再下载，
        // 而 generateTrajectoryMapUrl 只返回 URL 字符串。
        // 我们可以简单在一个 Controller 里实现下载，或者在 Service 加一个 public byte[] download(String
        // url)。
        // 这里为了方便，直接在 Service 加一个通用下载方法最好，但既然不能改 Service 太多，我就在这里写简易下载。

        try {
            java.net.URL url = java.net.URI.create(mapUrl).toURL();
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (conn.getResponseCode() == 200) {
                java.io.InputStream is = conn.getInputStream();
                java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                int nRead;
                byte[] data = new byte[1024];
                while ((nRead = is.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                }
                buffer.flush();
                byte[] imageBytes = buffer.toByteArray();

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.IMAGE_PNG);
                return new ResponseEntity<>(imageBytes, headers, HttpStatus.OK);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
}
