// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.infra;

import static dev.groknull.kludgeworks.eu.legislation.viewer.content.infra.jooq.Tables.EDGE;
import static dev.groknull.kludgeworks.eu.legislation.viewer.content.infra.jooq.Tables.NODE;
import static dev.groknull.kludgeworks.eu.legislation.viewer.content.infra.jooq.Tables.OVERLAY;
import static dev.groknull.kludgeworks.eu.legislation.viewer.content.infra.jooq.Tables.ROOT_SEGMENT;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.jsonbGetElementAsText;
import static org.jooq.impl.DSL.list;
import static org.jooq.impl.DSL.multiset;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.table;
import static org.jooq.impl.DSL.val;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Segment;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.application.TreeStore;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Tree;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Tree.Edge;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Tree.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Tree.Node;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Tree.Overlay;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.DataType;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Table;
import org.jooq.impl.SQLDataType;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

/**
 * The trees in Postgres (db/migration/content), and their segments by root, with jOOQ.
 *
 * <p>Deduplication is the store's: every row is keyed by its hash, and inserted unless the key is
 * there, so a node shared by many texts is stored once, and writers storing the same nodes at once
 * don't conflict. Referential integrity too: foreign keys hold edges, overlays and segments to
 * their nodes, and a text is added in its caller's transaction, so a root stored means its whole
 * tree is. Each table's rows go in one statement, as arrays a column each, which {@code unnest}
 * turns back into rows.
 */
@Repository
class JooqTreeStore implements TreeStore {
  private static final DataType<byte[][]> HASHES = SQLDataType.BLOB.array();

  /** The leaves that title a container, which a skeleton keeps: Outlines reads them. */
  private static final List<String> TITLES = List.of("num", "heading");

  private final DSLContext db;

  JooqTreeStore(DSLContext db) {
    this.db = db;
  }

  @Override
  public boolean contains(ContentHash root) {
    return db.fetchExists(NODE, NODE.HASH.eq(root));
  }

  @Override
  public int add(Tree tree, List<Segment> segments) {
    var nodes = insertNodes(tree);
    insertOverlays(tree);
    insertEdges(tree);
    insertSegments(tree.root(), segments);
    return nodes;
  }

  /** Every node under a root, with its children and their overlays. */
  @Override
  public Optional<Tree> tree(ContentHash root) {
    return tree(root, null);
  }

  /**
   * The containers under a root and the leaves that title them, with all their children and their
   * overlays: the walk follows an edge only to a container or to a num or heading, which it tells
   * by the JSON's first element, so the text's other blocks are never read.
   */
  @Override
  public Optional<Tree> skeleton(ContentHash root) {
    var child = NODE.as("child");
    return tree(
        root, child.KIND.eq(Kind.CONTAINER).or(jsonbGetElementAsText(child.CONTENT, 0).in(TITLES)));
  }

  /**
   * The nodes under a root, each with all its children and their overlays: every node, or those
   * that edges lead to whose node (child) is {@code kept}.
   */
  private Optional<Tree> tree(ContentHash root, @Nullable Condition kept) {
    var tree = name("tree").fields("hash");
    var hash = field(name("tree", "hash"), NODE.HASH.getDataType());
    var down = select(EDGE.CHILD).from(table(name("tree"))).join(EDGE).on(EDGE.PARENT.eq(hash));
    var to = NODE.as("child");
    var under =
        tree.as(
            select(val(root, NODE.HASH))
                .union(kept == null ? down : down.join(to).on(to.HASH.eq(EDGE.CHILD)).where(kept)));
    var children =
        multiset(
                select(EDGE.CHILD, EDGE.OVERLAY, OVERLAY.FACTS)
                    .from(EDGE)
                    .leftJoin(OVERLAY)
                    .on(OVERLAY.HASH.eq(EDGE.OVERLAY))
                    .where(EDGE.PARENT.eq(NODE.HASH))
                    .orderBy(EDGE.SEQ))
            .as("children");
    var nodes = new HashMap<ContentHash, Node>();
    var overlays = new HashMap<ContentHash, Overlay>();
    for (var row :
        db.withRecursive(under)
            .select(NODE.HASH, NODE.KIND, NODE.CONTENT, NODE.WEIGHT, children)
            .from(under)
            .join(NODE)
            .on(NODE.HASH.eq(hash))
            .fetch()) {
      var edges = row.value5().map(child -> new Edge(child.value1(), child.value2()));
      for (var child : row.value5()) {
        var overlay = child.value2();
        if (overlay != null) {
          overlays.putIfAbsent(
              overlay, new Overlay(overlay, bytes(Objects.requireNonNull(child.value3()))));
        }
      }
      nodes.put(
          row.value1(),
          new Node(row.value1(), row.value2(), bytes(row.value3()), row.value4(), edges));
    }
    return nodes.containsKey(root)
        ? Optional.of(new Tree(root, nodes, overlays))
        : Optional.empty();
  }

  @Override
  public List<Segment> segments(ContentHash root) {
    return db.selectFrom(ROOT_SEGMENT)
        .where(ROOT_SEGMENT.ROOT.eq(root))
        .orderBy(ROOT_SEGMENT.ORDINAL)
        .fetch(
            row ->
                new Segment(
                    row.getOrdinal(),
                    row.getHash(),
                    row.getParent(),
                    row.getFromSeq(),
                    row.getToSeq(),
                    row.getOffsetWeight(),
                    row.getWeight()));
  }

  // ---- writing

  /**
   * @return how many nodes were new
   */
  private int insertNodes(Tree tree) {
    var nodes = List.copyOf(tree.nodes().values());
    var hashes = new byte[nodes.size()][];
    var kinds = new String[nodes.size()];
    var contents = new String[nodes.size()];
    var weights = new Long[nodes.size()];
    var childCounts = new Integer[nodes.size()];
    for (var i = 0; i < nodes.size(); i++) {
      var node = nodes.get(i);
      hashes[i] = node.hash().bytes();
      kinds[i] = node.kind().name();
      contents[i] = new String(node.content(), StandardCharsets.UTF_8);
      weights[i] = node.weight();
      childCounts[i] = node.children().size();
    }
    var rows =
        unnest(val(hashes, HASHES), val(kinds), val(contents), val(weights), val(childCounts));
    return db.insertInto(NODE, NODE.HASH, NODE.KIND, NODE.CONTENT, NODE.WEIGHT, NODE.CHILD_COUNT)
        .select(
            select(
                    column(rows, 1, NODE.HASH),
                    column(rows, 2, NODE.KIND),
                    column(rows, 3, SQLDataType.CLOB).cast(NODE.CONTENT.getDataType()),
                    column(rows, 4, NODE.WEIGHT),
                    column(rows, 5, NODE.CHILD_COUNT))
                .from(rows))
        .onConflictDoNothing()
        .execute();
  }

  private void insertOverlays(Tree tree) {
    var overlays = List.copyOf(tree.overlays().values());
    var hashes = new byte[overlays.size()][];
    var facts = new String[overlays.size()];
    for (var i = 0; i < overlays.size(); i++) {
      hashes[i] = overlays.get(i).hash().bytes();
      facts[i] = new String(overlays.get(i).facts(), StandardCharsets.UTF_8);
    }
    var rows = unnest(val(hashes, HASHES), val(facts));
    db.insertInto(OVERLAY, OVERLAY.HASH, OVERLAY.FACTS)
        .select(
            select(
                    column(rows, 1, OVERLAY.HASH),
                    column(rows, 2, SQLDataType.CLOB).cast(OVERLAY.FACTS.getDataType()))
                .from(rows))
        .onConflictDoNothing()
        .execute();
  }

  private void insertEdges(Tree tree) {
    var count = tree.nodes().values().stream().mapToInt(n -> n.children().size()).sum();
    var parents = new byte[count][];
    var seqs = new Integer[count];
    var children = new byte[count][];
    byte[] @Nullable [] overlays = new byte[count][];
    var offsets = new Long[count];
    var i = 0;
    for (var node : tree.nodes().values()) {
      var offset = 0L;
      for (var seq = 0; seq < node.children().size(); seq++, i++) {
        var edge = node.children().get(seq);
        var overlay = edge.overlay();
        parents[i] = node.hash().bytes();
        seqs[i] = seq;
        children[i] = edge.child().bytes();
        overlays[i] = overlay == null ? null : overlay.bytes();
        offsets[i] = offset;
        offset += Objects.requireNonNull(tree.nodes().get(edge.child())).weight();
      }
    }
    var rows =
        unnest(
            val(parents, HASHES),
            val(seqs),
            val(children, HASHES),
            val(overlays, HASHES),
            val(offsets));
    db.insertInto(EDGE, EDGE.PARENT, EDGE.SEQ, EDGE.CHILD, EDGE.OVERLAY, EDGE.OFFSET_WEIGHT)
        .select(
            select(
                    column(rows, 1, EDGE.PARENT),
                    column(rows, 2, EDGE.SEQ),
                    column(rows, 3, EDGE.CHILD),
                    column(rows, 4, EDGE.OVERLAY),
                    column(rows, 5, EDGE.OFFSET_WEIGHT))
                .from(rows))
        .onConflictDoNothing()
        .execute();
  }

  private void insertSegments(ContentHash root, List<Segment> segments) {
    var ordinals = new Integer[segments.size()];
    var hashes = new byte[segments.size()][];
    var parents = new byte[segments.size()][];
    var starts = new Integer[segments.size()];
    var ends = new Integer[segments.size()];
    var offsets = new Long[segments.size()];
    var weights = new Long[segments.size()];
    for (var i = 0; i < segments.size(); i++) {
      var segment = segments.get(i);
      ordinals[i] = segment.ordinal();
      hashes[i] = segment.hash().bytes();
      parents[i] = segment.parent().bytes();
      starts[i] = segment.from();
      ends[i] = segment.to();
      offsets[i] = segment.offset();
      weights[i] = segment.weight();
    }
    var rows =
        unnest(
            val(ordinals),
            val(hashes, HASHES),
            val(parents, HASHES),
            val(starts),
            val(ends),
            val(offsets),
            val(weights));
    var s = ROOT_SEGMENT;
    db.insertInto(
            s, s.ROOT, s.ORDINAL, s.HASH, s.PARENT, s.FROM_SEQ, s.TO_SEQ, s.OFFSET_WEIGHT, s.WEIGHT)
        .select(
            select(
                    val(root, s.ROOT),
                    column(rows, 1, s.ORDINAL),
                    column(rows, 2, s.HASH),
                    column(rows, 3, s.PARENT),
                    column(rows, 4, s.FROM_SEQ),
                    column(rows, 5, s.TO_SEQ),
                    column(rows, 6, s.OFFSET_WEIGHT),
                    column(rows, 7, s.WEIGHT))
                .from(rows))
        .onConflictDoNothing()
        .execute();
  }

  /**
   * Arrays of equal length, a column each, as rows: Postgres's {@code unnest} of several arrays
   * (jOOQ's DSL.unnest takes one), its columns c1, c2 …
   */
  private static Table<?> unnest(Field<?>... columns) {
    var names = new String[columns.length];
    for (var i = 0; i < columns.length; i++) {
      names[i] = "c" + (i + 1);
    }
    return table("unnest({0})", list(columns)).as("row", names);
  }

  /** A column of the unnested rows, as the table column it goes into. */
  private static <T> Field<T> column(Table<?> rows, int index, Field<T> into) {
    return column(rows, index, into.getDataType());
  }

  private static <T> Field<T> column(Table<?> rows, int index, DataType<T> type) {
    return field(name(rows.getName(), "c" + index), type);
  }

  private static byte[] bytes(@Nullable JSONB json) {
    return Objects.requireNonNull(json).data().getBytes(StandardCharsets.UTF_8);
  }
}
