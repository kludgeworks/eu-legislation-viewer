// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.infra;

import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.infra.jooq.Tables.COMPARISON;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.infra.jooq.Tables.COMPARISON_WHOLE;
import static org.jooq.impl.DSL.currentInstant;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparison;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparison.Side;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Context;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.DifferenceBuilder;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.application.ComparisonStore;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Keeps the comparisons in Postgres, in the table {@code comparison}, with jOOQ. Jackson writes
 * each comparison as JSON bytes ({@link Kept}), and Postgres compresses them. Each difference is
 * kept as {@link Difference#kept} gives it. The whole unit of each difference that this cuts down
 * is a row of {@code comparison_whole}, by its eId.
 */
@Repository
class JooqComparisonStore implements ComparisonStore {
  private final DSLContext db;
  private final JsonMapper json;

  JooqComparisonStore(DSLContext db, JsonMapper json) {
    this.db = db;
    this.json = json;
  }

  @Override
  public Optional<Comparison> find(ContentHash from, ContentHash to) {
    return db.select(COMPARISON.KEPT, COMPARISON.WITHOUT_PREAMBLE)
        .from(COMPARISON)
        .where(COMPARISON.FROM_ROOT.eq(from), COMPARISON.TO_ROOT.eq(to))
        .fetchOptional()
        .map(
            row ->
                json.readValue(row.value1(), Kept.class)
                    .comparison(
                        from, to, row.value2() == null ? null : Side.valueOf(row.value2())));
  }

  @Override
  public Optional<Element> whole(ContentHash from, ContentHash to, String eId) {
    return db.select(COMPARISON_WHOLE.SHOWN)
        .from(COMPARISON_WHOLE)
        .where(
            COMPARISON_WHOLE.FROM_ROOT.eq(from),
            COMPARISON_WHOLE.TO_ROOT.eq(to),
            COMPARISON_WHOLE.E_ID.eq(eId))
        .fetchOptional()
        .map(row -> json.readValue(row.value1(), Element.class));
  }

  @Override
  @Transactional
  public Comparison add(Comparison comparison) {
    var differences = new ArrayList<Difference>();
    var cut = new ArrayList<Difference>();
    for (var each : comparison.differences()) {
      var kept = each.kept();
      differences.add(kept);
      if (!kept.equals(each)) {
        cut.add(each);
      }
    }
    var stored =
        new Comparison(
            comparison.from(),
            comparison.to(),
            differences,
            comparison.closing(),
            comparison.withoutPreamble());
    var withoutPreamble = stored.withoutPreamble();
    var added =
        db.insertInto(COMPARISON)
            .set(COMPARISON.FROM_ROOT, stored.from())
            .set(COMPARISON.TO_ROOT, stored.to())
            .set(COMPARISON.KEPT, json.writeValueAsBytes(Kept.of(stored)))
            .set(
                COMPARISON.WITHOUT_PREAMBLE,
                withoutPreamble == null ? null : withoutPreamble.name())
            .set(COMPARISON.COMPARED_AT, currentInstant())
            .onConflictDoNothing()
            .execute();
    // Another caller kept it first, with its whole units
    if (added == 0) {
      return stored;
    }
    for (var each : cut) {
      db.insertInto(COMPARISON_WHOLE)
          .set(COMPARISON_WHOLE.FROM_ROOT, stored.from())
          .set(COMPARISON_WHOLE.TO_ROOT, stored.to())
          .set(COMPARISON_WHOLE.E_ID, each.eId())
          .set(COMPARISON_WHOLE.SHOWN, json.writeValueAsBytes(each.shown()))
          .execute();
    }
    return stored;
  }

  /**
   * A comparison as it is kept, with each unit's opening once. A difference's lead and a context's
   * opening are both the opening of a unit, and the differences in one unit all have its opening as
   * their lead. So the openings are kept by the eId of their unit, and each difference and context
   * names its opening's unit instead of holding the opening.
   *
   * @param openings the openings, by the eId of their unit
   * @param differences the differences, without their leads and the openings of their contexts
   * @param closing the closing, without the openings of its contexts
   */
  record Kept(
      Map<String, Element> openings, List<KeptDifference> differences, List<KeptContext> closing) {

    static Kept of(Comparison comparison) {
      var openings = new Openings();
      var differences =
          comparison.differences().stream()
              .map(
                  each -> {
                    var lead = openings.name(each.parent(), each.lead());
                    return new KeptDifference(
                        DifferenceBuilder.builder(each)
                            .lead(lead == null ? each.lead() : null)
                            .context(List.of())
                            .build(),
                        lead,
                        each.context().stream().map(openings::kept).toList());
                  })
              .toList();
      var closing = comparison.closing().stream().map(openings::kept).toList();
      return new Kept(openings.byUnit, differences, closing);
    }

    Comparison comparison(ContentHash from, ContentHash to, @Nullable Side withoutPreamble) {
      var differences =
          this.differences.stream()
              .map(
                  each -> {
                    var difference = each.difference();
                    return DifferenceBuilder.builder(difference)
                        .lead(each.lead() == null ? difference.lead() : openings.get(each.lead()))
                        .context(each.context().stream().map(this::context).toList())
                        .build();
                  })
              .toList();
      return new Comparison(
          from, to, differences, closing.stream().map(this::context).toList(), withoutPreamble);
    }

    private Context context(KeptContext kept) {
      var context = kept.context();
      return kept.opening() == null
          ? context
          : new Context(context.units(), context.parent(), openings.get(kept.opening()));
    }
  }

  /**
   * A difference as it is kept.
   *
   * @param difference the difference, without its context, and without its lead if {@code lead}
   *     names it
   * @param lead the eId of the unit whose opening is its lead, or null if the difference holds its
   *     lead itself, or has none
   * @param context its context
   */
  record KeptDifference(Difference difference, @Nullable String lead, List<KeptContext> context) {}

  /**
   * A context as it is kept.
   *
   * @param context the context, without its opening if {@code opening} names it
   * @param opening the eId of the unit whose opening it is, or null if the context holds its
   *     opening itself, or has none
   */
  record KeptContext(Context context, @Nullable String opening) {}

  /** The openings of a comparison, by the eId of their unit, as {@link Kept#of} collects them. */
  private static final class Openings {
    private final Map<String, Element> byUnit = new HashMap<>();

    /**
     * Returns {@code unit}, under which the openings keep {@code opening}. Returns null if there is
     * no opening, or if the openings have another opening for the unit: then whoever has it holds
     * it.
     */
    @Nullable String name(@Nullable String unit, @Nullable Element opening) {
      if (unit == null || opening == null) {
        return null;
      }
      var known = byUnit.putIfAbsent(unit, opening);
      return known == null || Objects.equals(known, opening) ? unit : null;
    }

    KeptContext kept(Context context) {
      var opening =
          context.units().size() == 1
              ? name(context.units().getFirst().eId(), context.opening())
              : null;
      return new KeptContext(
          opening == null ? context : new Context(context.units(), context.parent(), null),
          opening);
    }
  }
}
