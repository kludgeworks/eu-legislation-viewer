package com.kludgeworks.eu.legislation.cli.backup;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Service
class CellarService2 {

    private final RestClient restClient;
    private static final String BASE_URI = "https://publications.europa.eu/resource/cellar";

    CellarService2(RestClient restClient) {
        this.restClient = restClient;
    }


    Notice getNoticeById(String psId) {
        URI uri = UriComponentsBuilder
                .fromUriString(BASE_URI)
                .path("/{ps-Id}")
                .queryParam("language", "{lang}")
                .encode()
                .buildAndExpand(psId, "eng")
                .toUri();

        return restClient
                .get()
                .uri(uri)
                .header("Accept", "application/xml;notice=tree")
                .retrieve().body(Notice.class);
    }

    Notice theNotice() {
        return restClient
                .get()
                .uri("http://publications.europa.eu/resource/cellar/ebb1a9b2-a87f-11e5-b528-01aa75ed71a1/xml/tree?decoding=en")
                .header("Accept", "application/xml;notice=tree")
                .retrieve()
                .body(Notice.class);
//                .toEntity(ResponseEntity.);
    }

}
