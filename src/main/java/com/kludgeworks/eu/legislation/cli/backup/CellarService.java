package com.kludgeworks.eu.legislation.cli.backup;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * Retrieves artifacts from <a href="https://op.europa.eu/en/web/cellar">Cellar</a> - the common data repository of the Publications Office of the European Union.
 */
@HttpExchange("https://publications.europa.eu/resource/cellar")
public interface CellarService {

    @GetExchange("/{psId}?language=eng")
    Notice getNoticeById(@PathVariable("psId") String psId, @RequestHeader(defaultValue = "application/xml;notice=tree") String accept);

    default Notice getNoticeById(@PathVariable("psId") String psId) {
        return getNoticeById(psId, null);
    }
}
