// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.acts.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Version;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.LinkRelation;

/**
 * The links that step through an act's timeline: RFC 5829's predecessor-version and
 * successor-version either side of a version and its latest-version, and RFC 8288's first. Each
 * version is linked as the resource has it: as a version, or as its text in a language (on the
 * timeline of the versions in that language, Timeline.in).
 */
public final class TimelineLinks {
  /**
   * RFC 5829's successor-version. Not IanaLinkRelations.SUCCESSOR_VERSION: Spring HATEOAS (3.1.2)
   * spells it "successor-versions", a relation no registry has.
   */
  public static final LinkRelation SUCCESSOR_VERSION = LinkRelation.of("successor-version");

  private TimelineLinks() {}

  /** The first version and the latest, each linked by {@code to}. */
  public static List<Link> ends(Timeline timeline, BiFunction<Version, LinkRelation, Link> to) {
    return List.of(
        to.apply(timeline.first(), IanaLinkRelations.FIRST),
        to.apply(timeline.latest(), IanaLinkRelations.LATEST_VERSION));
  }

  /**
   * The versions either side of {@code at}, where it has them, then the first and the latest, each
   * linked by {@code to}.
   */
  public static List<Link> around(
      Timeline timeline, Version at, BiFunction<Version, LinkRelation, Link> to) {
    var links = new ArrayList<Link>();
    timeline
        .predecessor(at)
        .ifPresent(v -> links.add(to.apply(v, IanaLinkRelations.PREDECESSOR_VERSION)));
    timeline.successor(at).ifPresent(v -> links.add(to.apply(v, SUCCESSOR_VERSION)));
    links.addAll(ends(timeline, to));
    return links;
  }
}
