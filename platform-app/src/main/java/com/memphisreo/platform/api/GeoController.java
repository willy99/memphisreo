package com.memphisreo.platform.api;

import com.memphisreo.platform.geo.GeoPlace;
import com.memphisreo.platform.geo.GeocodingProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Проксі геокодування для форми об'єкта: фронт не знає провайдера й не має його ключів. */
@RestController
@RequestMapping("/api/geo")
public class GeoController {

    private final GeocodingProvider geocodingProvider;

    public GeoController(GeocodingProvider geocodingProvider) {
        this.geocodingProvider = geocodingProvider;
    }

    @GetMapping("/search")
    public ResponseEntity<List<GeoPlace>> search(@RequestParam String q,
                                                 @RequestParam(defaultValue = "UA") String country,
                                                 @RequestParam(required = false) Double lat,
                                                 @RequestParam(required = false) Double lon) {
        return ResponseEntity.ok(geocodingProvider.search(q, country, lat, lon));
    }

    @GetMapping("/reverse")
    public ResponseEntity<GeoPlace> reverse(@RequestParam double lat, @RequestParam double lon) {
        return geocodingProvider.reverse(lat, lon)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
