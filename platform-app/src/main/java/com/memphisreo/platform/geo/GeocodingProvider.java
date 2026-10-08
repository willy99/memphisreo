package com.memphisreo.platform.geo;

import java.util.List;
import java.util.Optional;

/**
 * Точка заміни провайдера геокодування: локально — публічний Photon,
 * у проді — self-hosted Photon або комерційний (Visicom/Google) без змін у фронті.
 * Провайдер викликається лише з бекенда: ключі, кеш і ліміти — тут.
 */
public interface GeocodingProvider {

    List<GeoPlace> search(String query, String countryCode, Double biasLatitude, Double biasLongitude);

    Optional<GeoPlace> reverse(double latitude, double longitude);
}
