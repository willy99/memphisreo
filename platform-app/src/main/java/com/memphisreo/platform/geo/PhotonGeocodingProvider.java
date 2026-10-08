package com.memphisreo.platform.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Photon (komoot) — геокодер на даних OSM, створений для автодоповнення.
 * Публічний інстанс — лише для розробки (без гарантій і з тротлінгом);
 * у проді — власний інстанс з екстрактом України/ЄС (той самий API).
 * Збій провайдера не ламає форму: повертаємо порожній результат, агент
 * вводить адресу вручну.
 */
@Component
public class PhotonGeocodingProvider implements GeocodingProvider {

    private static final Logger log = LoggerFactory.getLogger(PhotonGeocodingProvider.class);
    private static final int CACHE_SIZE = 2000;

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String userAgent;
    private final Map<String, Object> cache = Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Object> eldest) {
            return size() > CACHE_SIZE;
        }
    });

    public PhotonGeocodingProvider(ObjectMapper objectMapper,
                                   @Value("${memphisreo.geo.photon-url}") String baseUrl,
                                   @Value("${memphisreo.geo.user-agent}") String userAgent) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.userAgent = userAgent;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<GeoPlace> search(String query, String countryCode, Double biasLatitude, Double biasLongitude) {
        String q = query == null ? "" : query.trim();
        if (q.length() < 3) {
            return List.of();
        }
        StringBuilder url = new StringBuilder(baseUrl).append("/api/?limit=10&q=").append(encode(q));
        if (biasLatitude != null && biasLongitude != null) {
            url.append("&lat=").append(round(biasLatitude)).append("&lon=").append(round(biasLongitude));
        }
        String cacheKey = "s|" + url + "|" + countryCode;
        Object cached = cache.get(cacheKey);
        if (cached != null) {
            return (List<GeoPlace>) cached;
        }
        // Кілька POI в одному будинку (банкомат, офіс…) — одна адреса для агента.
        Map<String, GeoPlace> unique = new LinkedHashMap<>();
        fetch(url.toString()).stream()
                .filter(p -> countryCode == null || countryCode.equalsIgnoreCase(p.countryCode()))
                .forEach(p -> unique.putIfAbsent(p.street() + "|" + p.houseNumber() + "|" + p.city() + "|" + p.district(), p));
        List<GeoPlace> places = unique.values().stream().limit(6).toList();
        cache.put(cacheKey, places);
        return places;
    }

    @Override
    public Optional<GeoPlace> reverse(double latitude, double longitude) {
        // ~1 м точності достатньо для кешу; координати пін-а все одно зберігаються точно.
        String url = baseUrl + "/reverse?limit=1&lat=" + round(latitude) + "&lon=" + round(longitude);
        String cacheKey = "r|" + url;
        Object cached = cache.get(cacheKey);
        if (cached instanceof GeoPlace place) {
            return Optional.of(place);
        }
        Optional<GeoPlace> place = fetch(url).stream().findFirst();
        place.ifPresent(p -> cache.put(cacheKey, p));
        return place;
    }

    private List<GeoPlace> fetch(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(4))
                    .header("User-Agent", userAgent)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Photon повернув {} для {}", response.statusCode(), url);
                return List.of();
            }
            return parse(objectMapper.readTree(response.body()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (Exception e) {
            log.warn("Геокодування недоступне ({}): {}", url, e.toString());
            return List.of();
        }
    }

    static List<GeoPlace> parse(JsonNode root) {
        List<GeoPlace> places = new ArrayList<>();
        for (JsonNode feature : root.path("features")) {
            JsonNode p = feature.path("properties");
            JsonNode coordinates = feature.path("geometry").path("coordinates");
            if (coordinates.size() < 2) {
                continue;
            }
            String street = text(p, "street");
            String house = text(p, "housenumber");
            String name = text(p, "name");
            String city = firstNonNull(text(p, "city"), text(p, "town"), text(p, "village"));
            // Вулиця без "street" у Photon — сама вулиця (osm_key=highway): назва в "name".
            if (street == null && "highway".equals(text(p, "osm_key"))) {
                street = name;
                name = null;
            }
            String district = firstNonNull(text(p, "district"), text(p, "locality"));
            places.add(new GeoPlace(
                    label(name, street, house, city),
                    street, house, city, district,
                    firstNonNull(text(p, "state"), text(p, "county")),
                    text(p, "postcode"),
                    Optional.ofNullable(text(p, "countrycode")).map(c -> c.toUpperCase(Locale.ROOT)).orElse(null),
                    coordinates.get(1).asDouble(),
                    coordinates.get(0).asDouble()));
        }
        return places;
    }

    private static String label(String name, String street, String house, String city) {
        List<String> parts = new ArrayList<>();
        if (name != null && !name.equals(street) && !name.equals(city)) {
            parts.add(name);
        }
        if (street != null) {
            parts.add(house != null ? street + ", " + house : street);
        }
        if (city != null) {
            parts.add(city);
        }
        return String.join(", ", parts);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    private static String firstNonNull(String... values) {
        for (String v : values) {
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static String round(double value) {
        return String.format(Locale.ROOT, "%.5f", value);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
