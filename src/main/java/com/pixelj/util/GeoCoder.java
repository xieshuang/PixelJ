package com.pixelj.util;

import java.util.*;

/**
 * 离线反向地理编码器。
 * 根据 GPS 坐标返回最近的城市名称（精度：市一级）。
 */
public class GeoCoder {

    private static final double KM_TO_DEGREE = 1.0 / 111.0;

    private static final Map<String, CityData> CITY_DATABASE = new LinkedHashMap<>();

    static {
        addCity("北京", 39.9042, 116.4074);
        addCity("上海", 31.2304, 121.4737);
        addCity("天津", 39.3434, 117.3616);
        addCity("重庆", 29.5630, 106.5516);
        addCity("合肥", 31.8206, 117.2272);
        addCity("南京", 32.0603, 118.7969);
        addCity("杭州", 30.2741, 120.1551);
        addCity("福州", 26.0753, 119.2965);
        addCity("厦门", 24.4798, 118.0894);
        addCity("南昌", 28.6829, 115.8579);
        addCity("济南", 36.6512, 117.1201);
        addCity("青岛", 36.0671, 120.3826);
        addCity("郑州", 34.7466, 113.6253);
        addCity("武汉", 30.5928, 114.3055);
        addCity("长沙", 28.2282, 112.9388);
        addCity("广州", 23.1291, 113.2644);
        addCity("深圳", 22.5431, 114.0579);
        addCity("南宁", 22.8170, 108.3665);
        addCity("海口", 20.0444, 110.1999);
        addCity("成都", 30.5728, 104.0668);
        addCity("贵阳", 26.6470, 106.6302);
        addCity("昆明", 25.0406, 102.7129);
        addCity("拉萨", 29.6500, 91.1000);
        addCity("西安", 34.3416, 108.9398);
        addCity("兰州", 36.0611, 103.8343);
        addCity("西宁", 36.6232, 101.7782);
        addCity("银川", 38.4680, 106.2731);
        addCity("乌鲁木齐", 43.8256, 87.6168);
        addCity("哈尔滨", 45.8038, 126.5340);
        addCity("长春", 43.8171, 125.3235);
        addCity("沈阳", 41.8057, 123.4328);
        addCity("大连", 38.9140, 121.6147);
        addCity("石家庄", 38.0428, 114.5149);
        addCity("太原", 37.8706, 112.5489);
        addCity("呼和浩特", 40.8414, 111.7519);
        addCity("苏州", 31.2989, 120.5853);
        addCity("无锡", 31.4912, 120.3119);
        addCity("宁波", 29.8683, 121.5440);
        addCity("温州", 28.0006, 120.6998);
        addCity("泉州", 24.8739, 118.6758);
        addCity("汕头", 23.3540, 116.6820);
        addCity("珠海", 22.2765, 113.5767);
        addCity("佛山", 23.0218, 113.1220);
        addCity("东莞", 23.0469, 113.7633);
        addCity("中山", 22.5176, 113.3926);
        addCity("三亚", 18.2528, 109.5119);
        addCity("桂林", 25.2744, 110.2990);
        addCity("大理", 25.6065, 100.2676);
        addCity("丽江", 26.8721, 100.2289);
        addCity("西安", 34.3416, 108.9398);
        addCity("延安", 36.5853, 109.4898);
        addCity("洛阳", 34.6197, 112.4540);
        addCity("开封", 34.7971, 114.3074);
        addCity("平遥", 37.2106, 112.3515);
        addCity("黄山", 29.7145, 118.3380);
        addCity("张家界", 29.1170, 110.4790);
        addCity("凤凰", 27.9475, 109.5995);
        addCity("厦门", 24.4798, 118.0894);
        addCity("鼓浪屿", 24.4469, 118.0679);
        addCity("庐山", 29.5293, 115.9878);
        addCity("泰山", 36.1948, 117.0574);
        addCity("黄山", 29.7145, 118.3380);
        addCity("九华山", 30.4564, 117.4799);
        addCity("峨眉山", 29.5253, 103.3358);
        addCity("青城山", 30.8879, 103.4973);
        addCity("武夷山", 27.7186, 117.9818);
        addCity("井冈山", 26.5753, 114.2734);
        addCity("丹霞山", 24.9618, 113.9356);
    }

    private static void addCity(String name, double lat, double lon) {
        CITY_DATABASE.put(name, new CityData(name, lat, lon));
    }

    /**
     * 根据 GPS 坐标获取最近的城市名称。
     *
     * @param latitude  纬度
     * @param longitude 经度
     * @return 城市名称，如果未找到则返回 null
     */
    public String reverseGeocode(double latitude, double longitude) {
        if (latitude == 0 && longitude == 0) {
            return null;
        }

        String nearestCity = null;
        double minDistance = Double.MAX_VALUE;

        for (CityData city : CITY_DATABASE.values()) {
            double dist = distance(latitude, longitude, city.latitude, city.longitude);
            if (dist < minDistance) {
                minDistance = dist;
                nearestCity = city.name;
            }
        }

        if (minDistance < 2.0) {
            return nearestCity;
        }

        return nearestCity + "附近";
    }

    private double distance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;
        return Math.sqrt(dLat * dLat + dLon * dLon) / KM_TO_DEGREE;
    }

    private static class CityData {
        final String name;
        final double latitude;
        final double longitude;

        CityData(String name, double latitude, double longitude) {
            this.name = name;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }
}
