// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.acts.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Version;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.hateoas.Link;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;

/**
 * What every resource of an act needs, for any module's controller: the act, and one of its
 * versions, each 404 if there's none; and the answer, cacheable as long as the viewer keeps an act
 * (viewer.acts.cache-for), as a timeline changes only when Cellar publishes a consolidation.
 */
@Component
public class ActResources {
  private final Acts acts;
  private final CacheControl cacheControl;

  /**
   * @param cacheFor how long an act is kept, and so may be cached (viewer.acts.cache-for)
   */
  ActResources(Acts acts, @Value("${viewer.acts.cache-for}") Duration cacheFor) {
    this.acts = acts;
    this.cacheControl = CacheControl.maxAge(cacheFor).cachePublic();
  }

  /** The act {@code id}; 404 if Cellar has none. */
  public Act act(ActIdentifier id) {
    return acts.find(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No act " + id));
  }

  /** The version of {@code act} that starts on {@code date}; 404 if none does. */
  public Version version(Act act, LocalDate date) {
    return act.timeline()
        .startingOn(date)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No version of "
                        + act.id()
                        + " starts on "
                        + date
                        + "; versions?on="
                        + date
                        + " gives the one that applies"));
  }

  /** 200 with {@code body}, cacheable as long as the act is kept. */
  public <T> ResponseEntity<T> ok(T body) {
    return ResponseEntity.ok().cacheControl(cacheControl).body(body);
  }

  /** 200 with {@code body}, its entity tag {@code etag}, cacheable as long as the act is kept. */
  public <T> ResponseEntity<T> ok(String etag, T body) {
    return ResponseEntity.ok().eTag(etag).cacheControl(cacheControl).body(body);
  }

  /**
   * 304 if {@code request} has the representation {@code etag} tags (If-None-Match), with what a
   * 200 would carry (RFC 9110: 304 Not Modified): checked before the representation is made, for
   * one that costs to make; none if it hasn't.
   */
  public Optional<ResponseEntity<Void>> notModified(WebRequest request, String etag) {
    if (!request.checkNotModified(etag)) {
      return Optional.empty();
    }
    return Optional.of(
        ResponseEntity.status(HttpStatus.NOT_MODIFIED)
            .eTag(etag)
            .cacheControl(cacheControl)
            .build());
  }

  /** 303 to {@code link}'s resource, a lookup's answer, cacheable as long as the act is kept. */
  public ResponseEntity<Void> seeOther(Link link) {
    return ResponseEntity.status(HttpStatus.SEE_OTHER)
        .location(link.toUri())
        .cacheControl(cacheControl)
        .build();
  }
}
