// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate.Provision;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.WhenAnActApplies.EnactedDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.CellarException;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.ContentStreams;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.FormexFiles;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.FormexManifestation;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * Reads an act's enacted entry-into-force or application article from its English Formex in Cellar
 * when Cellar's stored date and its publication-anchored clause contradict one another, and
 * classifies standard EU legal formulas against {@link EnactedDate}.
 */
@Component
class EnactedDates implements CellarDates.EnactedDateLookup {
  private static final Logger LOG = LoggerFactory.getLogger(EnactedDates.class);
  private static final long MAX_XML_BYTES = 4L * 1024 * 1024;
  private static final DateTimeFormatter FORMEX_DATE = DateTimeFormatter.BASIC_ISO_DATE;

  private static final Pattern WHITESPACE = Pattern.compile("\\s+");
  private static final Pattern ARTICLE_HEADING =
      Pattern.compile("Article\\s+([0-9]+[a-zA-Z]?)", Pattern.CASE_INSENSITIVE);
  private static final Pattern SENTENCE_SPLIT =
      Pattern.compile("(?<!\\bNo)(?<!\\bArt)\\.(?:\\s+|$)");
  private static final Pattern DATE_MARKER = Pattern.compile("\u0000(\\d{4}-\\d{2}-\\d{2})\u0000");

  private static final String PUBLICATION_PREFIX =
      "\\A\\s*(?:(?:as\\s+from|from|on)\\s+)?(?:the\\s+)?";
  private static final Pattern DAY_OF_PUB =
      Pattern.compile(
          PUBLICATION_PREFIX + "day\\s+of\\s+its\\s+publication\\b", Pattern.CASE_INSENSITIVE);
  private static final Pattern DAY_FOLLOWING =
      Pattern.compile(
          PUBLICATION_PREFIX + "day\\s+following(?:\\s+that\\s+of)?\\s+its\\s+publication\\b",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern NTH_DAY_FOLLOWING =
      Pattern.compile(
          PUBLICATION_PREFIX
              + "([a-z0-9-]+)\\s+day\\s+following(?:\\s+that\\s+of)?\\s+its\\s+publication\\b",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern NUMERIC_ORDINAL = Pattern.compile("^(\\d+)(?:st|nd|rd|th)?$");

  private static final Pattern ENTRY_INTO_FORCE_VERB =
      Pattern.compile("\\b(?:shall\\s+)?enters?\\s+into\\s+force\\b", Pattern.CASE_INSENSITIVE);
  private static final Pattern APPLICATION_VERB =
      Pattern.compile(
          "\\b(?:shall\\s+)?appl(?:y|ies)\\b|\\b(?:shall\\s+be|be|is)\\s+applicable\\b"
              + "|\\bapplicable\\s+from\\b",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern TAKING_EFFECT_VERB =
      Pattern.compile("\\b(?:shall\\s+)?takes?\\s+effect\\b", Pattern.CASE_INSENSITIVE);
  private static final Pattern PROVISION_SUBJECT =
      Pattern.compile(
          "\\b(?:articles?|paragraphs?|subparagraphs?|points?|indents?|annex(?:es)?|chapters?"
              + "|sections?|titles?|parts?)\\b",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern PART_SUBJECT_START =
      Pattern.compile(
          "^(?:however,\\s+)?(?:the\\s+(?:first|second|third|fourth|fifth)\\s+)?"
              + "(?:articles?|paragraphs?|subparagraphs?|points?|indents?|annex(?:es)?|chapters?"
              + "|sections?|titles?|parts?)\\b",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern ACT_SUBJECT =
      Pattern.compile(
          "\\b(?:this\\s+(?:regulation|directive|decision|act)|it)\\b", Pattern.CASE_INSENSITIVE);

  private static final Pattern EXPLICIT_ENTRY_INTO_FORCE =
      Pattern.compile(
          "\\b(?:enter|enters)\\s+into\\s+force"
              + "(?:\\s+(?:retroactively|with\\s+(?:retroactive\\s+)?effect))?"
              + "\\s+(?:as\\s+from|from|on)\\s+\u0000",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern EXPLICIT_APPLICATION =
      Pattern.compile(
          "\\b(?:apply|applies|applicable)"
              + "(?:\\s+(?:retroactively|with\\s+(?:retroactive\\s+)?effect))?"
              + "\\s+(?:as\\s+from|from|on)\\s+\u0000",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern EXPLICIT_TAKING_EFFECT =
      Pattern.compile(
          "\\b(?:take|takes)\\s+effect"
              + "(?:\\s+(?:retroactively|with\\s+(?:retroactive\\s+)?effect))?"
              + "\\s+(?:as\\s+from|from|on)\\s+\u0000",
          Pattern.CASE_INSENSITIVE);

  private static final Map<String, Integer> ORDINALS =
      Map.ofEntries(
          Map.entry("first", 1),
          Map.entry("second", 2),
          Map.entry("third", 3),
          Map.entry("fourth", 4),
          Map.entry("fifth", 5),
          Map.entry("sixth", 6),
          Map.entry("seventh", 7),
          Map.entry("eighth", 8),
          Map.entry("ninth", 9),
          Map.entry("tenth", 10),
          Map.entry("eleventh", 11),
          Map.entry("twelfth", 12),
          Map.entry("thirteenth", 13),
          Map.entry("fourteenth", 14),
          Map.entry("fifteenth", 15),
          Map.entry("sixteenth", 16),
          Map.entry("seventeenth", 17),
          Map.entry("eighteenth", 18),
          Map.entry("nineteenth", 19),
          Map.entry("twentieth", 20),
          Map.entry("twenty-first", 21),
          Map.entry("twenty-second", 22),
          Map.entry("twenty-third", 23),
          Map.entry("twenty-fourth", 24),
          Map.entry("twenty-fifth", 25),
          Map.entry("twenty-sixth", 26),
          Map.entry("twenty-seventh", 27),
          Map.entry("twenty-eighth", 28),
          Map.entry("twenty-ninth", 29),
          Map.entry("thirtieth", 30),
          Map.entry("thirty-first", 31));

  private final FormexFiles formexFiles;
  private final ContentStreams streams;

  EnactedDates(FormexFiles formexFiles, ContentStreams streams) {
    this.formexFiles = formexFiles;
    this.streams = streams;
  }

  @FunctionalInterface
  interface ManifestationLookup {
    Optional<FormexManifestation> find();
  }

  interface XmlReader {
    <T> T read(URI uri, Function<InputStream, T> reader);
  }

  @Override
  public List<Article> read(ActIdentifier act) {
    return lookup(
        act,
        () -> formexFiles.manifestation(act.celex(), "ENG"),
        new XmlReader() {
          @Override
          public <T> T read(URI uri, Function<InputStream, T> reader) {
            return streams.read(uri, MAX_XML_BYTES, reader);
          }
        });
  }

  static List<Article> lookup(
      ActIdentifier act, ManifestationLookup manifestations, XmlReader xml) {
    try {
      var manifest = manifestations.find();
      if (manifest.isEmpty()) {
        LOG.debug("{}: no English Formex manifestation in Cellar", act);
        return List.of();
      }
      return readArticles(act, manifest.get(), xml);
    } catch (CellarException | RestClientException | UncheckedIOException e) {
      LOG.info("{}: could not read enacted text ({})", act, e.getMessage());
      return List.of();
    }
  }

  private static List<Article> readArticles(
      ActIdentifier act, FormexManifestation manifest, XmlReader xml) {
    var main = mainFile(act, manifest, xml);
    if (main.isPresent()) {
      return xml.read(main.get().uri(), stream -> parseArticles(act, stream));
    }
    for (var file : manifest.files()) {
      var lower = file.name().toLowerCase(Locale.ROOT);
      if (lower.endsWith(".xml") && !lower.endsWith(".doc.xml") && !lower.endsWith(".toc.xml")) {
        var found = xml.read(file.uri(), stream -> parseArticles(act, stream));
        if (!found.isEmpty()) {
          return found;
        }
      }
    }
    return List.of();
  }

  private static Optional<FormexManifestation.File> mainFile(
      ActIdentifier act, FormexManifestation manifest, XmlReader xml) {
    for (var file : manifest.files()) {
      if (file.name().toLowerCase(Locale.ROOT).endsWith(".doc.xml")) {
        var ref = xml.read(file.uri(), stream -> parseMainXmlRef(act, stream));
        if (ref.isPresent()) {
          return manifest.file(ref.get());
        }
      }
    }
    return Optional.empty();
  }

  static Optional<String> parseMainXmlRef(ActIdentifier act, InputStream stream) {
    try {
      var reader = newReader(readAll(act, stream));
      boolean inMainPub = false;
      while (reader.hasNext()) {
        int event = reader.next();
        if (event == XMLStreamConstants.START_ELEMENT) {
          var name = reader.getLocalName();
          if ("DOC.MAIN.PUB".equals(name)) {
            inMainPub = true;
          } else if (inMainPub && "REF.PHYS".equals(name)) {
            var type = reader.getAttributeValue(null, "TYPE");
            var file = reader.getAttributeValue(null, "FILE");
            if ("DOC.XML".equals(type) && file != null) {
              return Optional.of(file);
            }
          }
        } else if (event == XMLStreamConstants.END_ELEMENT
            && "DOC.MAIN.PUB".equals(reader.getLocalName())) {
          inMainPub = false;
        }
      }
      return Optional.empty();
    } catch (XMLStreamException e) {
      throw new CellarException("Unreadable Formex descriptor for " + act, e);
    }
  }

  static List<Article> parseArticles(ActIdentifier act, InputStream stream) {
    try {
      var reader = newReader(readAll(act, stream));
      var parser = new ArticleParser();
      while (reader.hasNext()) {
        switch (reader.next()) {
          case XMLStreamConstants.START_ELEMENT -> parser.onStart(reader);
          case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA ->
              parser.onText(reader.getText());
          case XMLStreamConstants.END_ELEMENT -> parser.onEnd(reader.getLocalName());
          default -> {}
        }
      }
      return List.copyOf(parser.articles);
    } catch (XMLStreamException e) {
      throw new CellarException("Unreadable Formex article XML for " + act, e);
    }
  }

  private static final class ArticleParser {
    final List<Article> articles = new ArrayList<>();
    private int quotDepth;
    private boolean inEnactingTerms;
    private boolean inArticle;
    private boolean inTitle;
    private boolean inParagraph;
    private boolean inParagraphNo;
    private boolean inAlinea;
    private final List<String> articleNumberParts = new ArrayList<>();
    private final List<String> paragraphNumberParts = new ArrayList<>();
    private final List<String> sentenceParts = new ArrayList<>();
    private final List<LocalDate> paragraphDates = new ArrayList<>();
    private final List<Paragraph> paragraphs = new ArrayList<>();

    void onStart(XMLStreamReader reader) {
      var name = reader.getLocalName();
      if ("QUOT.S".equals(name)) {
        quotDepth++;
        return;
      }
      if (quotDepth > 0) {
        return;
      }
      switch (name) {
        case "ENACTING.TERMS" -> inEnactingTerms = true;
        case "ARTICLE" -> {
          if (inEnactingTerms) {
            inArticle = true;
            articleNumberParts.clear();
            paragraphs.clear();
          }
        }
        case "TI.ART" -> inTitle = inArticle;
        case "PARAG" -> startParagraph();
        case "NO.PARAG" -> inParagraphNo = inParagraph;
        case "ALINEA" -> startAlinea();
        case "DATE" -> recordDate(reader.getAttributeValue(null, "ISO"));
        default -> {}
      }
    }

    private void startParagraph() {
      if (!inArticle) {
        return;
      }
      inParagraph = true;
      paragraphNumberParts.clear();
      sentenceParts.clear();
      paragraphDates.clear();
    }

    private void startAlinea() {
      if (!inArticle) {
        return;
      }
      inAlinea = true;
      if (!inParagraph) {
        sentenceParts.clear();
        paragraphDates.clear();
      }
    }

    private void recordDate(@Nullable String iso) {
      if (!inParagraph && !inAlinea) {
        return;
      }
      parseFormexDate(iso)
          .ifPresent(d -> {
            sentenceParts.add(" \u0000" + d + "\u0000 ");
            paragraphDates.add(d);
          });
    }

    void onText(String text) {
      if (quotDepth > 0) {
        return;
      }
      if (inTitle) {
        articleNumberParts.add(text);
      } else if (inParagraphNo) {
        paragraphNumberParts.add(text);
      } else if (inAlinea || (inParagraph && !inParagraphNo)) {
        sentenceParts.add(text);
      }
    }

    void onEnd(String name) {
      if ("QUOT.S".equals(name)) {
        if (quotDepth > 0) {
          quotDepth--;
        }
        return;
      }
      if (quotDepth > 0) {
        return;
      }
      switch (name) {
        case "TI.ART" -> inTitle = false;
        case "NO.PARAG" -> inParagraphNo = false;
        case "ALINEA" -> {
          if (!inParagraph && inAlinea) {
            paragraphs.add(
                new Paragraph(null, String.join("", sentenceParts), List.copyOf(paragraphDates)));
          }
          inAlinea = false;
        }
        case "PARAG" -> {
          if (inParagraph) {
            paragraphs.add(
                new Paragraph(
                    cleanParagraphNumber(String.join("", paragraphNumberParts)),
                    String.join("", sentenceParts),
                    List.copyOf(paragraphDates)));
            inParagraph = false;
          }
        }
        case "ARTICLE" -> {
          if (inArticle) {
            articles.add(
                new Article(
                    cleanArticleNumber(String.join("", articleNumberParts)),
                    List.copyOf(paragraphs)));
            inArticle = false;
          }
        }
        case "ENACTING.TERMS" -> inEnactingTerms = false;
        default -> {}
      }
    }
  }

  static EnactedDate classifyArticles(
      List<Article> articles, Kind kind, List<Provision> provisions, boolean forPart) {
    if (articles.isEmpty()) {
      return new EnactedDate.Unknown();
    }
    var candidates = selectParagraphs(articles, provisions);
    var resolved = new ArrayList<EnactedDate>();
    for (var paragraph : candidates) {
      for (var sentence : splitSentences(paragraph.rawWithDateMarkers())) {
        if (matchesKind(sentence.cleanText(), kind)
            && matchesSubject(sentence.cleanText(), kind, forPart)) {
          var classified = classifySentence(sentence, kind);
          if (classified instanceof EnactedDate.Unknown) {
            return new EnactedDate.Unknown();
          }
          if (!resolved.contains(classified)) {
            resolved.add(classified);
          }
        }
      }
    }
    return resolved.size() == 1 ? resolved.getFirst() : new EnactedDate.Unknown();
  }

  private static List<Paragraph> selectParagraphs(
      List<Article> articles, List<Provision> provisions) {
    var articleProvisions =
        provisions.stream()
            .filter(p -> p.type() == Provision.Type.ARTICLE && p.number() != null)
            .toList();
    if (articleProvisions.isEmpty()) {
      return articles.stream().flatMap(a -> a.paragraphs().stream()).toList();
    }
    var selected = new ArrayList<Paragraph>();
    for (var provision : articleProvisions) {
      var parts = splitProvisionNumber(Objects.requireNonNullElse(provision.number(), ""));
      if (parts.isEmpty()) {
        continue;
      }
      var artNo = parts.get().article();
      var paraNo = parts.get().paragraph();
      for (var article : articles) {
        if (artNo.equalsIgnoreCase(article.number())) {
          if (paraNo != null) {
            var matchingPara =
                article.paragraphs().stream()
                    .filter(p -> paraNo.equalsIgnoreCase(p.number()))
                    .toList();
            if (!matchingPara.isEmpty()) {
              selected.addAll(matchingPara);
            } else if (article.paragraphs().stream().noneMatch(p -> p.number() != null)) {
              selected.addAll(article.paragraphs());
            }
          } else {
            selected.addAll(article.paragraphs());
          }
        }
      }
    }
    return selected;
  }

  private record ProvisionParts(String article, @Nullable String paragraph) {}

  private static Optional<ProvisionParts> splitProvisionNumber(String number) {
    int i = 0;
    while (i < number.length() && Character.isDigit(number.charAt(i))) {
      i++;
    }
    if (i == 0) {
      return Optional.empty();
    }
    if (i < number.length() && Character.isLetter(number.charAt(i))) {
      i++;
    }
    var artNo = number.substring(0, i);
    String paraNo = null;
    if (i < number.length() && (number.charAt(i) == '.' || number.charAt(i) == '(')) {
      int start = i + 1;
      int end = start;
      while (end < number.length() && Character.isLetterOrDigit(number.charAt(end))) {
        end++;
      }
      if (end > start) {
        paraNo = number.substring(start, end);
      }
    }
    return Optional.of(new ProvisionParts(artNo, paraNo));
  }

  private static @Nullable Pattern verbPattern(Kind kind) {
    return switch (kind) {
      case ENTRY_INTO_FORCE -> ENTRY_INTO_FORCE_VERB;
      case APPLICATION -> APPLICATION_VERB;
      case TAKING_EFFECT -> TAKING_EFFECT_VERB;
      case TRANSPOSITION -> null;
    };
  }

  private static boolean matchesKind(String sentence, Kind kind) {
    var verb = verbPattern(kind);
    return verb != null && verb.matcher(sentence).find();
  }

  private static boolean matchesSubject(String sentence, Kind kind, boolean forPart) {
    var verb = verbPattern(kind);
    if (verb == null) {
      return false;
    }
    var matcher = verb.matcher(sentence);
    if (!matcher.find()) {
      return false;
    }
    var beforeVerb = sentence.substring(0, matcher.start());
    if (forPart) {
      return PART_SUBJECT_START.matcher(beforeVerb.trim()).find();
    }
    return !PROVISION_SUBJECT.matcher(beforeVerb).find() && ACT_SUBJECT.matcher(beforeVerb).find();
  }

  static EnactedDate classifySentence(Sentence sentence, Kind kind) {
    var text = sentence.cleanText();
    var dates = sentence.dates();
    var lower = text.toLowerCase(Locale.ROOT);
    if (!lower.contains("publication")) {
      if (dates.size() == 1 && hasExplicitDateClause(sentence.markedText(), kind)) {
        return new EnactedDate.Fixed(dates.getFirst());
      }
      return new EnactedDate.Unknown();
    }
    if (!dates.isEmpty()) {
      return new EnactedDate.Unknown();
    }
    var verb = verbPattern(kind);
    if (verb == null) {
      return new EnactedDate.Unknown();
    }
    var verbMatcher = verb.matcher(text);
    if (!verbMatcher.find()) {
      return new EnactedDate.Unknown();
    }
    var afterVerb = text.substring(verbMatcher.end());
    if (DAY_OF_PUB.matcher(afterVerb).find()) {
      return new EnactedDate.OnPublication(0);
    }
    var nth = NTH_DAY_FOLLOWING.matcher(afterVerb);
    if (nth.find()) {
      var days = parseOrdinal(nth.group(1));
      if (days != null) {
        return new EnactedDate.OnPublication(days);
      }
      if (!nth.group(1).equalsIgnoreCase("the")) {
        return new EnactedDate.Unknown();
      }
    }
    if (DAY_FOLLOWING.matcher(afterVerb).find()) {
      return new EnactedDate.OnPublication(1);
    }
    return new EnactedDate.Unknown();
  }

  private static boolean hasExplicitDateClause(String markedText, Kind kind) {
    var lower = markedText.toLowerCase(Locale.ROOT);
    if (lower.contains("except")
        || lower.contains("until")
        || lower.replaceFirst("^however,\\s+", "").contains("however")) {
      return false;
    }
    return switch (kind) {
      case ENTRY_INTO_FORCE -> EXPLICIT_ENTRY_INTO_FORCE.matcher(markedText).find();
      case APPLICATION -> EXPLICIT_APPLICATION.matcher(markedText).find();
      case TAKING_EFFECT -> EXPLICIT_TAKING_EFFECT.matcher(markedText).find();
      case TRANSPOSITION -> false;
    };
  }

  private static @Nullable Integer parseOrdinal(String token) {
    var lower = token.toLowerCase(Locale.ROOT);
    var word = ORDINALS.get(lower);
    if (word != null) {
      return word;
    }
    var numeric = NUMERIC_ORDINAL.matcher(lower);
    if (numeric.matches()) {
      return Integer.valueOf(numeric.group(1));
    }
    return null;
  }

  private static List<Sentence> splitSentences(String raw) {
    var sentences = new ArrayList<Sentence>();
    var normalized = WHITESPACE.matcher(raw).replaceAll(" ").trim();
    if (normalized.isEmpty()) {
      return sentences;
    }
    for (var part : SENTENCE_SPLIT.split(normalized)) {
      var dates = new ArrayList<LocalDate>();
      var matcher = DATE_MARKER.matcher(part);
      var clean = new StringBuilder();
      while (matcher.find()) {
        dates.add(LocalDate.parse(matcher.group(1)));
        matcher.appendReplacement(clean, "");
      }
      matcher.appendTail(clean);
      var sentence = WHITESPACE.matcher(clean.toString()).replaceAll(" ").trim();
      var marked =
          WHITESPACE.matcher(DATE_MARKER.matcher(part).replaceAll("\u0000")).replaceAll(" ").trim();
      if (!sentence.isEmpty()) {
        sentences.add(new Sentence(sentence, marked, List.copyOf(dates)));
      }
    }
    return sentences;
  }

  private static Optional<LocalDate> parseFormexDate(@Nullable String iso) {
    if (iso == null || iso.length() != 8) {
      return Optional.empty();
    }
    try {
      return Optional.of(LocalDate.parse(iso, FORMEX_DATE));
    } catch (DateTimeParseException e) {
      return Optional.empty();
    }
  }

  private static @Nullable String cleanArticleNumber(String title) {
    var matcher = ARTICLE_HEADING.matcher(WHITESPACE.matcher(title).replaceAll(" ").trim());
    return matcher.find() ? matcher.group(1) : null;
  }

  private static @Nullable String cleanParagraphNumber(String raw) {
    var cleaned = WHITESPACE.matcher(raw).replaceAll("").replace(".", "").replace(")", "");
    return cleaned.isEmpty() ? null : cleaned;
  }

  private static XMLStreamReader newReader(byte[] bytes) throws XMLStreamException {
    var factory = XMLInputFactory.newFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    return factory.createXMLStreamReader(new ByteArrayInputStream(bytes));
  }

  private static byte[] readAll(ActIdentifier act, InputStream stream) {
    try {
      return stream.readAllBytes();
    } catch (IOException e) {
      throw new CellarException("Failed reading Formex XML for " + act, e);
    }
  }

  record Article(@Nullable String number, List<Paragraph> paragraphs) {}

  record Paragraph(@Nullable String number, String rawWithDateMarkers, List<LocalDate> dates) {}

  private record Sentence(String cleanText, String markedText, List<LocalDate> dates) {}
}
