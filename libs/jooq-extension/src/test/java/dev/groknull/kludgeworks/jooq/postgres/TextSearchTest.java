// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.jooq.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.Test;

/**
 * Tests the SQL that the operators render. A filter must be the operator, so that Postgres can use
 * a GIN index, and a configuration must be a literal.
 */
class TextSearchTest {
  private static final DSLContext POSTGRES = DSL.using(SQLDialect.POSTGRES);
  private static final Field<TsVector> WORDS = DSL.field(DSL.name("words"), TsVector.DATA_TYPE);
  private static final Field<String> TITLE = DSL.field(DSL.name("title"), SQLDataType.CLOB);

  @Test
  void matchesWithTheOperatorAndAConfigurationThatIsALiteral() {
    var query = TextSearch.plainToTsQuery("english", DSL.val("aviation security"));
    assertThat(POSTGRES.renderInlined(TextSearch.matches(WORDS, query)))
        .isEqualTo("(\"words\" @@ plainto_tsquery('english'::regconfig, 'aviation security'))");
  }

  @Test
  void joinsTwoQueriesSoThatEitherMatches() {
    var one = TextSearch.plainToTsQuery("english", DSL.val("gdpr"));
    var other = TextSearch.plainToTsQuery("english", DSL.val("2016/679"));
    assertThat(POSTGRES.renderInlined(TextSearch.either(one, other)))
        .isEqualTo(
            "(plainto_tsquery('english'::regconfig, 'gdpr')"
                + " || plainto_tsquery('english'::regconfig, '2016/679'))");
  }

  @Test
  void ranksWithTheWeightsInPostgresOrder() {
    var query = TextSearch.plainToTsQuery("simple", DSL.val("reach"));
    var weights = new RankWeights(0.05f, 0.15f, 0.6f, 1.0f);
    assertThat(POSTGRES.renderInlined(TextSearch.rankCd(weights, WORDS, query, 32)))
        .isEqualTo(
            "ts_rank_cd('{0.05,0.15,0.6,1.0}'::float4[], \"words\","
                + " plainto_tsquery('simple'::regconfig, 'reach'), 32)");
  }

  @Test
  void filtersLikeWordsWithTheOperator() {
    assertThat(POSTGRES.renderInlined(TextSearch.wordSimilar(DSL.val("aviaton"), TITLE)))
        .isEqualTo("('aviaton' <% \"title\")");
  }

  @Test
  void readsAValueAsPostgresWritesIt() {
    assertThat(TsVector.of("'aviat':1A")).isEqualTo(new TsVector("'aviat':1A"));
    assertThat(TsQuery.of("'aviat' & 'rule'").text()).isEqualTo("'aviat' & 'rule'");
  }
}
