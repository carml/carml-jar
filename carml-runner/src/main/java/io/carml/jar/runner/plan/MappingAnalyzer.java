package io.carml.jar.runner.plan;

import io.carml.engine.ResolvedMapping;
import io.carml.logicalview.DedupStrategy;
import io.carml.model.DatabaseSource;
import io.carml.model.Field;
import io.carml.model.FilePath;
import io.carml.model.FileSource;
import io.carml.model.ForeignKeyAnnotation;
import io.carml.model.LogicalSource;
import io.carml.model.LogicalView;
import io.carml.model.NameableStream;
import io.carml.model.NotNullAnnotation;
import io.carml.model.PrimaryKeyAnnotation;
import io.carml.model.TriplesMap;
import io.carml.model.UniqueAnnotation;
import io.carml.util.LogUtil;
import io.carml.util.Mappings;
import io.carml.vocab.Rdf;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import org.eclipse.rdf4j.model.Resource;

/**
 * Analyzes resolved RML mappings and produces a structured report of source types, join patterns,
 * annotations, decomposition potential, and evaluator recommendations — all without executing the
 * mapping or connecting to any data source.
 */
public final class MappingAnalyzer {

  private MappingAnalyzer() {}

  // --- Result records ---

  public record MappingAnalysis(List<SourceAnalysis> sources, List<ViewAnalysis> views) {

    /**
     * Returns a new analysis with view evaluator recommendations refreshed based on the current source
     * iteration estimates. Call this after updating source iterations via interview or CLI options.
     */
    public MappingAnalysis refreshRecommendations() {
      var sourceById = sources.stream()
          .collect(java.util.stream.Collectors.toMap(SourceAnalysis::id, s -> s));

      var updatedViews = views.stream()
          .map(v -> {
            var source = sourceById.get(v.sourceId());
            var rows = source != null ? source.estimatedRows() : null;
            var hasJoins = v.joins()
                .leftJoinCount() > 0
                || v.joins()
                    .innerJoinCount() > 0;
            // Find max row count across join parent sources
            Long maxJoinRows = null;
            var anyParentUnknown = false;
            for (var parentId : v.joinParentSourceIds()) {
              var parentSource = sourceById.get(parentId);
              var parentRows = parentSource != null ? parentSource.estimatedRows() : null;
              if (parentRows == null) {
                anyParentUnknown = true;
              } else if (maxJoinRows == null || parentRows > maxJoinRows) {
                maxJoinRows = parentRows;
              }
            }
            if (anyParentUnknown && maxJoinRows == null) {
              maxJoinRows = null;
            }
            var rec = recommendEvaluator(v.inProcessDbCompatible(), hasJoins, rows, maxJoinRows);
            return new ViewAnalysis(v.triplesMapName(), v.sourceId(), v.fieldCount(), v.pomCount(), v.joins(),
                v.annotations(), v.decomposition(), v.dedupStrategy(), v.inProcessDbCompatible(), rec,
                v.joinParentSourceIds());
          })
          .toList();

      return new MappingAnalysis(sources, updatedViews);
    }
  }

  public record SourceAnalysis(String id, String name, String type, String formulation, String location,
      Long estimatedRows) {

    public SourceAnalysis withEstimatedRows(Long rows) {
      return new SourceAnalysis(id, name, type, formulation, location, rows);
    }
  }

  public record ViewAnalysis(String triplesMapName, String sourceId, int fieldCount, int pomCount, JoinInfo joins,
      AnnotationInfo annotations, DecompositionInfo decomposition, String dedupStrategy, boolean inProcessDbCompatible,
      String recommendedEvaluator, List<String> joinParentSourceIds) {
  }

  public record JoinInfo(int leftJoinCount, int innerJoinCount, boolean hasCrossSourceJoins) {
  }

  public record AnnotationInfo(int pkCount, int fkCount, int uniqueCount, int notNullCount, List<String> pkFields) {
  }

  public record DecompositionInfo(boolean decomposed, int groupCount) {
  }

  // --- Source type constants ---

  private static final String UNKNOWN = "unknown";

  private static final Set<Resource> CSV_FORMULATIONS = Set.of(Rdf.Ql.Csv, Rdf.Rml.Csv);

  private static final Set<Resource> JSON_FORMULATIONS = Set.of(Rdf.Ql.JsonPath, Rdf.Rml.JsonPath);

  private static final Set<Resource> XML_FORMULATIONS = Set.of(Rdf.Ql.XPath, Rdf.Rml.XPath);

  private static final Set<Resource> SQL_FORMULATIONS =
      Set.of(Rdf.Ql.Rdb, Rdf.Rml.Rdb, Rdf.Rml.SQL2008Table, Rdf.Rml.SQL2008Query);

  // --- Main analysis method ---

  /**
   * Analyzes the given triples maps and their resolved mappings.
   *
   * @param triplesMaps the original triples maps from the mapping
   * @param resolvedMappings the resolved mappings from {@link io.carml.engine.MappingResolver}
   * @return the analysis result
   */
  public static MappingAnalysis analyze(Set<TriplesMap> triplesMaps, List<ResolvedMapping> resolvedMappings) {
    var sourceMap = extractSourceMap(triplesMaps);
    var sources = sourceMap.values()
        .stream()
        .sorted(Comparator.comparing(SourceAnalysis::id))
        .toList();
    var views = analyzeViews(resolvedMappings, sourceMap);
    return new MappingAnalysis(sources, views);
  }

  // --- Source extraction ---

  private static IdentityHashMap<LogicalSource, SourceAnalysis> extractSourceMap(Set<TriplesMap> triplesMaps) {
    var sourceMap = new IdentityHashMap<LogicalSource, SourceAnalysis>();

    // Filter out functionValue inner TriplesMaps — their logicalSource is just a context
    // marker for expression evaluation, not a real data source.
    for (var tm : Mappings.filterMappable(triplesMaps)) {
      var logicalSource = findLogicalSource(tm);
      if (logicalSource != null) {
        sourceMap.computeIfAbsent(logicalSource, ls -> analyzeSource(String.valueOf(sourceMap.size() + 1), ls));
      }
    }
    return sourceMap;
  }

  private static LogicalSource findLogicalSource(TriplesMap tm) {
    var viewOn = tm.getLogicalSource();
    if (viewOn instanceof LogicalSource ls) {
      return ls;
    }
    if (viewOn instanceof LogicalView lv) {
      var nested = lv.getViewOn();
      if (nested instanceof LogicalSource ls) {
        return ls;
      }
    }
    return null;
  }

  private static SourceAnalysis analyzeSource(String id, LogicalSource logicalSource) {
    var refFormulation = logicalSource.getReferenceFormulation();
    var refIri = refFormulation != null ? refFormulation.getAsResource() : null;

    var type = detectSourceType(refIri, logicalSource);
    var formulation = refIri instanceof org.eclipse.rdf4j.model.IRI iri ? iri.getLocalName() : UNKNOWN;
    var location = detectSourceLocation(logicalSource);
    var name = deriveName(location, logicalSource);

    return new SourceAnalysis(id, name, type, formulation, location, null);
  }

  private static String detectSourceType(Resource refIri, LogicalSource logicalSource) {
    if (refIri == null) {
      // R2RML-style LogicalTable: no reference formulation, but has tableName or query
      if (logicalSource instanceof io.carml.model.LogicalTable || logicalSource.getTableName() != null
          || logicalSource.getQuery() != null) {
        return "SQL Database";
      }
      return UNKNOWN;
    }
    if (CSV_FORMULATIONS.contains(refIri)) {
      return "CSV";
    }
    if (JSON_FORMULATIONS.contains(refIri)) {
      return "JSON";
    }
    if (XML_FORMULATIONS.contains(refIri)) {
      return "XML";
    }
    if (SQL_FORMULATIONS.contains(refIri)) {
      return "SQL Database";
    }
    return UNKNOWN;
  }

  private static String detectSourceLocation(LogicalSource logicalSource) {
    var source = logicalSource.getSource();
    if (source instanceof FilePath fp) {
      return fp.getPath();
    }
    if (source instanceof FileSource fs) {
      return fs.getUrl();
    }
    if (source instanceof DatabaseSource db) {
      return db.getJdbcDsn();
    }
    if (source instanceof NameableStream ns) {
      return "stream:" + ns.getStreamName();
    }
    var tableName = logicalSource.getTableName();
    if (tableName != null) {
      return "table:" + tableName;
    }
    var query = logicalSource.getQuery();
    if (query != null) {
      return "query:" + query.strip()
          .split("\\s+")[0] + "...";
    }
    return UNKNOWN;
  }

  private static String deriveName(String location, LogicalSource logicalSource) {
    String baseName;
    if (location != null && !location.equals(UNKNOWN)) {
      var lastSlash = location.lastIndexOf('/');
      baseName = lastSlash >= 0 && lastSlash < location.length() - 1 ? location.substring(lastSlash + 1) : location;
    } else {
      baseName = LogUtil.log(logicalSource);
    }

    // Append iterator to disambiguate sources that share a file but iterate differently
    var iterator = logicalSource.getIterator();
    if (iterator != null && !iterator.isBlank()) {
      return "%s [%s]".formatted(baseName, iterator);
    }
    return baseName;
  }

  // --- View analysis ---

  private static List<ViewAnalysis> analyzeViews(List<ResolvedMapping> resolvedMappings,
      IdentityHashMap<LogicalSource, SourceAnalysis> sourceMap) {
    // Group resolved mappings by original TriplesMap to detect decomposition
    var byTriplesMap = new LinkedHashMap<TriplesMap, List<ResolvedMapping>>();
    for (var rm : resolvedMappings) {
      byTriplesMap.computeIfAbsent(rm.getOriginalTriplesMap(), k -> new ArrayList<>())
          .add(rm);
    }

    var views = new ArrayList<ViewAnalysis>();

    for (var entry : byTriplesMap.entrySet()) {
      var tm = entry.getKey();
      var mappings = entry.getValue();
      var primary = mappings.getFirst();

      var ls = findLogicalSource(tm);
      var sourceAnalysis = ls != null ? sourceMap.get(ls) : null;
      var sourceId = sourceAnalysis != null ? sourceAnalysis.id() : "?";

      var effectiveView = primary.getEffectiveView();
      var fieldCount = effectiveView.getFields()
          .size();
      var pomCount = tm.getPredicateObjectMaps()
          .size();

      var joins = analyzeJoins(effectiveView);
      var annotations = analyzeAnnotations(effectiveView);
      var decomposition = new DecompositionInfo(mappings.size() > 1, mappings.size());

      var dedupStrategy = describeDedupStrategy(primary.getEvaluationContext()
          .getDedupStrategy());

      var refIri = ls != null && ls.getReferenceFormulation() != null ? ls.getReferenceFormulation()
          .getAsResource() : null;
      var inProcessDbCompatible = isInProcessDbCompatible(refIri, ls);

      var hasJoins = effectiveView.getLeftJoins()
          .size() > 0
          || effectiveView.getInnerJoins()
              .size() > 0;
      var sourceRows = sourceAnalysis != null ? sourceAnalysis.estimatedRows() : null;

      // For join decisions, consider the largest source involved (own + parent sources).
      // Reactive's join store loads all parent records into memory, so large parent sources
      // are the OOM risk — not just the child source size.
      var maxJoinRows = maxJoinSourceRows(effectiveView, sourceMap);

      var recommendedEvaluator = recommendEvaluator(inProcessDbCompatible, hasJoins, sourceRows, maxJoinRows);

      var joinParentSourceIds = collectJoinParentSourceIds(effectiveView, sourceMap);

      var tmName = tm.getResourceName();
      views.add(new ViewAnalysis(tmName, sourceId, fieldCount, pomCount, joins, annotations, decomposition,
          dedupStrategy, inProcessDbCompatible, recommendedEvaluator, joinParentSourceIds));
    }
    return List.copyOf(views);
  }


  private static JoinInfo analyzeJoins(LogicalView view) {
    var leftJoins = view.getLeftJoins()
        .size();
    var innerJoins = view.getInnerJoins()
        .size();

    var hasCrossSource = false;
    var viewSource = view.getViewOn();
    for (var join : view.getLeftJoins()) {
      if (join.getParentLogicalView()
          .getViewOn() != viewSource) {
        hasCrossSource = true;
        break;
      }
    }
    if (!hasCrossSource) {
      for (var join : view.getInnerJoins()) {
        if (join.getParentLogicalView()
            .getViewOn() != viewSource) {
          hasCrossSource = true;
          break;
        }
      }
    }

    return new JoinInfo(leftJoins, innerJoins, hasCrossSource);
  }

  private static AnnotationInfo analyzeAnnotations(LogicalView view) {
    int pk = 0;
    int fk = 0;
    int unique = 0;
    int notNull = 0;
    var pkFields = new ArrayList<String>();

    for (var annotation : view.getStructuralAnnotations()) {
      if (annotation instanceof PrimaryKeyAnnotation) {
        pk++;
        pkFields.addAll(annotation.getOnFields()
            .stream()
            .map(Field::getFieldName)
            .toList());
      } else if (annotation instanceof ForeignKeyAnnotation) {
        fk++;
      } else if (annotation instanceof UniqueAnnotation) {
        unique++;
      } else if (annotation instanceof NotNullAnnotation) {
        notNull++;
      }
    }

    return new AnnotationInfo(pk, fk, unique, notNull, List.copyOf(pkFields));
  }

  private static final Class<?> NONE_DEDUP_CLASS = DedupStrategy.none()
      .getClass();

  private static final Class<?> SIMPLE_EQUALITY_DEDUP_CLASS = DedupStrategy.simpleEquality()
      .getClass();

  private static String describeDedupStrategy(DedupStrategy strategy) {
    if (strategy.getClass() == NONE_DEDUP_CLASS) {
      return "none";
    }
    if (strategy.getClass() == SIMPLE_EQUALITY_DEDUP_CLASS) {
      return "simple-equality";
    }
    return "exact";
  }

  /**
   * Returns the maximum estimated row count across all parent sources involved in joins for the given
   * view. Returns {@code null} if any parent source has unknown row count.
   */
  private static Long maxJoinSourceRows(LogicalView view, IdentityHashMap<LogicalSource, SourceAnalysis> sourceMap) {
    Long max = null;
    var anyUnknown = false;

    for (var join : view.getLeftJoins()) {
      var parentRows = parentSourceRows(join.getParentLogicalView(), sourceMap);
      if (parentRows == null) {
        anyUnknown = true;
      } else if (max == null || parentRows > max) {
        max = parentRows;
      }
    }
    for (var join : view.getInnerJoins()) {
      var parentRows = parentSourceRows(join.getParentLogicalView(), sourceMap);
      if (parentRows == null) {
        anyUnknown = true;
      } else if (max == null || parentRows > max) {
        max = parentRows;
      }
    }

    return anyUnknown && max == null ? null : max;
  }

  private static List<String> collectJoinParentSourceIds(LogicalView view,
      IdentityHashMap<LogicalSource, SourceAnalysis> sourceMap) {
    var ids = new ArrayList<String>();
    for (var join : view.getLeftJoins()) {
      addParentSourceId(join.getParentLogicalView(), sourceMap, ids);
    }
    for (var join : view.getInnerJoins()) {
      addParentSourceId(join.getParentLogicalView(), sourceMap, ids);
    }
    return List.copyOf(ids);
  }

  private static void addParentSourceId(LogicalView parentView,
      IdentityHashMap<LogicalSource, SourceAnalysis> sourceMap, List<String> ids) {
    var viewOn = parentView.getViewOn();
    if (viewOn instanceof LogicalSource ls) {
      var sa = sourceMap.get(ls);
      if (sa != null) {
        ids.add(sa.id());
      }
    }
  }

  private static Long parentSourceRows(LogicalView parentView,
      IdentityHashMap<LogicalSource, SourceAnalysis> sourceMap) {
    var viewOn = parentView.getViewOn();
    if (viewOn instanceof LogicalSource ls) {
      var sa = sourceMap.get(ls);
      return sa != null ? sa.estimatedRows() : null;
    }
    return null;
  }

  /**
   * Recommends the best evaluator for a view based on DuckDB compatibility, join presence, and
   * estimated source size. DuckDB excels at joins (SQL pushdown) and large datasets (spill-to-disk).
   * Reactive has lower overhead for flat mappings at moderate scale.
   *
   * @param maxJoinRows the largest estimated row count across parent join sources, or null if unknown
   */
  static String recommendEvaluator(boolean inProcessDbCompatible, boolean hasJoins, Long estimatedRows,
      Long maxJoinRows) {
    if (!inProcessDbCompatible) {
      return "reactive";
    }

    if (hasJoins) {
      // If any join source has unknown row count, prefer DuckDB for safety — reactive's
      // in-memory join store could OOM on large parent sources.
      if (estimatedRows == null || maxJoinRows == null) {
        return "in-process-db";
      }
      // Both known: use the largest source involved in the join (own + parents).
      var maxRows = Math.max(estimatedRows, maxJoinRows);
      if (maxRows > 100_000) {
        return "in-process-db"; // Large joins need SQL pushdown
      }
      return "reactive"; // Small joins are fine in-memory
    }

    // No joins: reactive is faster at moderate scale, DuckDB scales better for large data
    if (estimatedRows != null && estimatedRows > 100_000) {
      return "in-process-db";
    }
    return "reactive";
  }



  static boolean isInProcessDbCompatible(Resource refIri, LogicalSource logicalSource) {
    if (refIri == null) {
      return false;
    }
    if (CSV_FORMULATIONS.contains(refIri)) {
      return isFileBasedSource(logicalSource);
    }
    if (JSON_FORMULATIONS.contains(refIri)) {
      return isFileBasedSource(logicalSource);
    }
    // SQL always compatible; XML, streams, unknown — not compatible
    return SQL_FORMULATIONS.contains(refIri);
  }

  private static boolean isFileBasedSource(LogicalSource logicalSource) {
    var source = logicalSource.getSource();
    return source instanceof FilePath || source instanceof FileSource;
  }
}
