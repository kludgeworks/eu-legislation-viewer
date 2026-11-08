// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.infra;

import static dev.groknull.kludgeworks.eu.legislation.viewer.content.infra.jooq.Tables.EDGE;
import static dev.groknull.kludgeworks.eu.legislation.viewer.content.infra.jooq.Tables.NODE;
import static dev.groknull.kludgeworks.eu.legislation.viewer.content.infra.jooq.Tables.OUTLINE;
import static dev.groknull.kludgeworks.eu.legislation.viewer.content.infra.jooq.Tables.OVERLAY;
import static dev.groknull.kludgeworks.eu.legislation.viewer.content.infra.jooq.Tables.ROOT_SEGMENT;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.jsonbGetElementAsText;
import static org.jooq.impl.DSL.list;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.table;
import static org.jooq.impl.DSL.val;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Segment;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.application.TreeStore;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Tree;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Tree.Edge;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Tree.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Tree.Node;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Tree.Overlay;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.DataType;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Record4;
import org.jooq.Table;
import org.jooq.impl.SQLDataType;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Keeps the trees in Postgres, and their segments by root, with jOOQ. The tables are in {@code
 * db/migration/content}.
 *
 * <ul>
 *   <li>The store removes duplicates. Every row has its hash as key, and is inserted only if the
 *       key is not there yet. Thus a node that many texts share is stored once. Writers that store
 *       the same nodes at the same time do not conflict.
 *   <li>The store keeps referential integrity. Foreign keys tie edges, overlays and segments to
 *       their nodes. A text is added in the transaction of the caller. Thus if a root is stored,
 *       its whole tree is stored.
 *   <li>The rows of each table go in one statement, as one array for each column. {@code unnest}
 *       turns the arrays back into rows.
 * </ul>
 */
@Repository
class JooqTreeStore implements TreeStore {
  private static final DataType<byte[][]> HASHES = SQLDataType.BLOB.array();

  /** The leaves that title a container. A skeleton keeps them, and {@code Outlines} reads them. */
  private static final List<String> TITLES = List.of("num", "heading");

  private final DSLContext db;
  private final JsonMapper json;

  JooqTreeStore(DSLContext db, JsonMapper json) {
    this.db = db;
    this.json = json;
  }

  @Override
  public Optional<Outline> outline(ContentHash root) {
    return db.select(OUTLINE.OUTLINE_)
        .from(OUTLINE)
        .where(OUTLINE.ROOT.eq(root))
        .fetchOptional()
        .map(row -> json.readValue(row.value1().data(), Outline.class));
  }

  @Override
  public void addOutline(ContentHash root, Outline outline) {
    db.insertInto(OUTLINE)
        .set(OUTLINE.ROOT, root)
        .set(OUTLINE.OUTLINE_, JSONB.valueOf(json.writeValueAsString(outline)))
        .onConflictDoNothing()
        .execute();
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

  /** Returns every node under a root, with its children and their overlays. */
  @Override
  public Optional<Tree> tree(ContentHash root) {
    return tree(root, null);
  }

  /**
   * Returns the containers under a root and the leaves that title them, with all their children and
   * their overlays. The walk follows an edge only to a container, or to a num or heading. It finds
   * these by the first element of the JSON. Thus the other blocks of the text are never read.
   */
  @Override
  public Optional<Tree> skeleton(ContentHash root) {
    var child = NODE.as("child");
    return tree(
        root, child.KIND.eq(Kind.CONTAINER).or(jsonbGetElementAsText(child.CONTENT, 0).in(TITLES)));
  }

  /**
   * Returns the nodes under a root, each with all its children and their overlays. If {@code kept}
   * is null, it returns every node. If not, it follows only the edges whose child node is {@code
   * kept}.
   *
   * <p>One join gives one row for each child, or one row for a node without children. It does not
   * query the children of each node on its own, because Postgres then ran thousands of queries for
   * a long text.
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
    var rows = new LinkedHashMap<ContentHash, Record4<ContentHash, Kind, JSONB, Long>>();
    var children = new HashMap<ContentHash, List<Edge>>();
    var overlays = new HashMap<ContentHash, Overlay>();
    for (var row :
        db.withRecursive(under)
            .select(
                NODE.HASH,
                NODE.KIND,
                NODE.CONTENT,
                NODE.WEIGHT,
                EDGE.CHILD,
                EDGE.OVERLAY,
                OVERLAY.FACTS)
            .from(under)
            .join(NODE)
            .on(NODE.HASH.eq(hash))
            .leftJoin(EDGE)
            .on(EDGE.PARENT.eq(NODE.HASH))
            .leftJoin(OVERLAY)
            .on(OVERLAY.HASH.eq(EDGE.OVERLAY))
            .orderBy(NODE.HASH, EDGE.SEQ)
            .fetch()) {
      rows.putIfAbsent(row.value1(), row.into(NODE.HASH, NODE.KIND, NODE.CONTENT, NODE.WEIGHT));
      var child = row.value5();
      if (child == null) {
        continue;
      }
      var overlay = row.value6();
      children.computeIfAbsent(row.value1(), h -> new ArrayList<>()).add(new Edge(child, overlay));
      if (overlay != null) {
        overlays.putIfAbsent(overlay, new Overlay(overlay, bytes(row.value7())));
      }
    }
    var nodes = new HashMap<ContentHash, Node>();
    rows.forEach(
        (at, row) ->
            nodes.put(
                at,
                new Node(
                    at,
                    row.value2(),
                    bytes(row.value3()),
                    row.value4(),
                    List.copyOf(children.getOrDefault(at, List.of())))));
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
   * Inserts the nodes of the tree that are not stored yet.
   *
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
   * Turns arrays of equal length, one for each column, into rows. It uses the {@code unnest} of
   * Postgres on several arrays, because {@code DSL.unnest} of jOOQ takes only one. The columns are
   * {@code c1}, {@code c2} and so on.
   */
  private static Table<?> unnest(Field<?>... columns) {
    var names = new String[columns.length];
    for (var i = 0; i < columns.length; i++) {
      names[i] = "c" + (i + 1);
    }
    return table("unnest({0})", list(columns)).as("row", names);
  }

  /** Returns a column of the unnested rows, with the type of the table column that it goes into. */
  private static <T> Field<T> column(Table<?> rows, int index, Field<T> into) {
    return column(rows, index, into.getDataType());
  }

  private static <T> Field<T> column(Table<?> rows, int index, DataType<T> type) {
    return field(name(rows.getName(), "c" + index), type);
  }

  private static byte[] bytes(@Nullable JSONB json) {
    if (json == null) {
      throw new IllegalArgumentException("json is required");
    }
    return json.data().getBytes(StandardCharsets.UTF_8);
  }
}
