package com.memphisreo.platform.geo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PhotonGeocodingProviderTest {

    @Test
    void parsesHouseAndStreetResults() throws Exception {
        String json = """
                {"features":[
                  {"properties":{"osm_key":"building","type":"house","housenumber":"22","street":"вулиця Хрещатик",
                    "district":"Центр","city":"Київ","state":"Київ","postcode":"01001","countrycode":"UA"},
                   "geometry":{"coordinates":[30.5230925,50.4498465]}},
                  {"properties":{"osm_key":"highway","name":"вулиця Січових Стрільців","city":"Київ","countrycode":"UA"},
                   "geometry":{"coordinates":[30.49,50.45]}}
                ]}""";

        List<GeoPlace> places = PhotonGeocodingProvider.parse(new ObjectMapper().readTree(json));

        assertThat(places.get(0)).isEqualTo(new GeoPlace("вулиця Хрещатик, 22, Київ", "вулиця Хрещатик", "22",
                "Київ", "Центр", "Київ", "01001", "UA", 50.4498465, 30.5230925));
        assertThat(places.get(1).street()).isEqualTo("вулиця Січових Стрільців");
        assertThat(places.get(1).label()).isEqualTo("вулиця Січових Стрільців, Київ");
    }
}
