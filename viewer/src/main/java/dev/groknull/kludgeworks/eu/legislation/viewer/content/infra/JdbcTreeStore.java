// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.infra;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Segment;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.application.TreeStore;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Tree;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Tree.Edge;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Tree.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Tree.Node;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Tree.Overlay;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * The trees in Postgres (db/migration/content), and their segments by root.
 *
 * <p>Deduplication is the store's: every row is keyed by its hash, and inserted unless the key is
 * there, so a node shared by many texts is stored once, and writers storing the same nodes at once
 * don't conflict. Referential integrity too: foreign keys hold edges, overlays and segments to
 * their nodes, and a text is added in its caller's transaction, so a root stored means its whole
 * tree is. Each table's rows go in one statement, as arrays a column each, which {@code unnest}
 * turns back into rows; hashes as hex, JSON as text, each cast by Postgres.
 */
@Repository
class JdbcTreeStore implements TreeStore {
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private static final String INSERT_NODES =
      """
      insert into node (hash, kind, content, weight, child_count)
      select decode(hash, 'hex'), kind, content::jsonb, weight, child_count
      from unnest(cast(:hashes as text[]), cast(:kinds as text[]), cast(:contents as text[]),
          cast(:weights as bigint[]), cast(:childCounts as int[]))
        as row (hash, kind, content, weight, child_count)
      on conflict do nothing
      """;

  private static final String INSERT_OVERLAYS =
      """
      insert into overlay (hash, facts)
      select decode(hash, 'hex'), facts::jsonb
      from unnest(cast(:hashes as text[]), cast(:facts as text[])) as row (hash, facts)
      on conflict do nothing
      """;

  private static final String INSERT_EDGES =
      """
      insert into edge (parent, seq, child, overlay, offset_weight)
      select decode(parent, 'hex'), seq, decode(child, 'hex'), decode(overlay, 'hex'), offset_weight
      from unnest(cast(:parents as text[]), cast(:seqs as int[]), cast(:children as text[]),
          cast(:overlays as text[]), cast(:offsets as bigint[]))
        as row (parent, seq, child, overlay, offset_weight)
      on conflict do nothing
      """;

  private static final String INSERT_SEGMENTS =
      """
      insert into root_segment (root, ordinal, hash, parent, from_seq, to_seq, offset_weight, weight)
      select :root, ordinal, decode(hash, 'hex'), decode(parent, 'hex'), from_seq, to_seq,
          offset_weight, weight
      from unnest(cast(:ordinals as int[]), cast(:hashes as text[]), cast(:parents as text[]),
          cast(:starts as int[]), cast(:ends as int[]), cast(:offsets as bigint[]),
          cast(:weights as bigint[]))
        as row (ordinal, hash, parent, from_seq, to_seq, offset_weight, weight)
      on conflict do nothing
      """;

  private static final String STORED = "select exists (select 1 from node where hash = :root)";

  private static final String SEGMENTS =
      """
      select ordinal, hash, parent, from_seq, to_seq, offset_weight, weight
      from root_segment where root = :root order by ordinal
      """;

  /** Every node under a root, with its children and their overlays. */
  private static final String TREE =
      """
      with recursive tree(hash) as (
        select cast(:root as bytea)
        union
        select e.child from tree join edge e on e.parent = tree.hash
      )
      select n.hash, n.kind, n.content::text as content, n.weight,
        (select coalesce(jsonb_agg(jsonb_build_array(
              encode(e.child, 'hex'), encode(e.overlay, 'hex'), o.facts) order by e.seq), '[]')
          from edge e left join overlay o on o.hash = e.overlay
          where e.parent = n.hash)::text as children
      from tree join node n on n.hash = tree.hash
      """;

  private final JdbcClient jdbc;

  JdbcTreeStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean contains(ContentHash root) {
    return jdbc.sql(STORED).param("root", root.bytes()).query(Boolean.class).single();
  }

  @Override
  public int add(Tree tree, List<Segment> segments) {
    var nodes = insertNodes(tree);
    insertOverlays(tree);
    insertEdges(tree);
    insertSegments(tree.root(), segments);
    return nodes;
  }

  @Override
  public Optional<Tree> tree(ContentHash root) {
    var nodes = new HashMap<ContentHash, Node>();
    var overlays = new HashMap<ContentHash, Overlay>();
    for (var row : jdbc.sql(TREE).param("root", root.bytes()).query(JdbcTreeStore::row).list()) {
      nodes.put(row.node().hash(), row.node());
      row.overlays().forEach(o -> overlays.putIfAbsent(o.hash(), o));
    }
    return nodes.containsKey(root)
        ? Optional.of(new Tree(root, nodes, overlays))
        : Optional.empty();
  }

  @Override
  public List<Segment> segments(ContentHash root) {
    return jdbc.sql(SEGMENTS)
        .param("root", root.bytes())
        .query(
            (rs, i) ->
                new Segment(
                    rs.getInt("ordinal"),
                    ContentHash.of(rs.getBytes("hash")),
                    ContentHash.of(rs.getBytes("parent")),
                    rs.getInt("from_seq"),
                    rs.getInt("to_seq"),
                    rs.getLong("offset_weight"),
                    rs.getLong("weight")))
        .list();
  }

  /** A node read back, with the overlays on the edges to its children. */
  private record Row(Node node, List<Overlay> overlays) {}

  private static Row row(ResultSet rs, int i) throws SQLException {
    var edges = new ArrayList<Edge>();
    var overlays = new ArrayList<Overlay>();
    for (var child : (List<?>) JSON.readValue(rs.getBytes("children"), Object.class)) {
      var c = (List<?>) child;
      var overlay = c.get(1) instanceof String hex ? new ContentHash(hex) : null;
      edges.add(new Edge(new ContentHash((String) c.getFirst()), overlay));
      if (overlay != null) {
        overlays.add(
            new Overlay(overlay, JSON.writeValueAsBytes(Objects.requireNonNull(c.get(2)))));
      }
    }
    var hash = ContentHash.of(rs.getBytes("hash"));
    var node =
        new Node(
            hash,
            Kind.valueOf(rs.getString("kind").toUpperCase(Locale.ROOT)),
            rs.getString("content").getBytes(StandardCharsets.UTF_8),
            rs.getLong("weight"),
            List.copyOf(edges));
    return new Row(node, overlays);
  }

  // ---- writing

  /**
   * @return how many nodes were new
   */
  private int insertNodes(Tree tree) {
    var nodes = List.copyOf(tree.nodes().values());
    var hashes = new String[nodes.size()];
    var kinds = new String[nodes.size()];
    var contents = new String[nodes.size()];
    var weights = new long[nodes.size()];
    var childCounts = new int[nodes.size()];
    for (var i = 0; i < nodes.size(); i++) {
      var node = nodes.get(i);
      hashes[i] = node.hash().hex();
      kinds[i] = node.kind().name().toLowerCase(Locale.ROOT);
      contents[i] = new String(node.content(), StandardCharsets.UTF_8);
      weights[i] = node.weight();
      childCounts[i] = node.children().size();
    }
    return jdbc.sql(INSERT_NODES)
        .param("hashes", hashes)
        .param("kinds", kinds)
        .param("contents", contents)
        .param("weights", weights)
        .param("childCounts", childCounts)
        .update();
  }

  private void insertOverlays(Tree tree) {
    var overlays = List.copyOf(tree.overlays().values());
    var hashes = new String[overlays.size()];
    var facts = new String[overlays.size()];
    for (var i = 0; i < overlays.size(); i++) {
      hashes[i] = overlays.get(i).hash().hex();
      facts[i] = new String(overlays.get(i).facts(), StandardCharsets.UTF_8);
    }
    jdbc.sql(INSERT_OVERLAYS).param("hashes", hashes).param("facts", facts).update();
  }

  private void insertEdges(Tree tree) {
    var count = tree.nodes().values().stream().mapToInt(n -> n.children().size()).sum();
    var parents = new String[count];
    var seqs = new int[count];
    var children = new String[count];
    @Nullable String[] overlays = new String[count];
    var offsets = new long[count];
    var i = 0;
    for (var node : tree.nodes().values()) {
      var offset = 0L;
      for (var seq = 0; seq < node.children().size(); seq++, i++) {
        var edge = node.children().get(seq);
        var overlay = edge.overlay();
        parents[i] = node.hash().hex();
        seqs[i] = seq;
        children[i] = edge.child().hex();
        overlays[i] = overlay == null ? null : overlay.hex();
        offsets[i] = offset;
        offset += Objects.requireNonNull(tree.nodes().get(edge.child())).weight();
      }
    }
    jdbc.sql(INSERT_EDGES)
        .param("parents", parents)
        .param("seqs", seqs)
        .param("children", children)
        .param("overlays", overlays)
        .param("offsets", offsets)
        .update();
  }

  private void insertSegments(ContentHash root, List<Segment> segments) {
    var ordinals = new int[segments.size()];
    var hashes = new String[segments.size()];
    var parents = new String[segments.size()];
    var starts = new int[segments.size()];
    var ends = new int[segments.size()];
    var offsets = new long[segments.size()];
    var weights = new long[segments.size()];
    for (var i = 0; i < segments.size(); i++) {
      var segment = segments.get(i);
      ordinals[i] = segment.ordinal();
      hashes[i] = segment.hash().hex();
      parents[i] = segment.parent().hex();
      starts[i] = segment.from();
      ends[i] = segment.to();
      offsets[i] = segment.offset();
      weights[i] = segment.weight();
    }
    jdbc.sql(INSERT_SEGMENTS)
        .param("root", root.bytes())
        .param("ordinals", ordinals)
        .param("hashes", hashes)
        .param("parents", parents)
        .param("starts", starts)
        .param("ends", ends)
        .param("offsets", offsets)
        .param("weights", weights)
        .update();
  }
}
