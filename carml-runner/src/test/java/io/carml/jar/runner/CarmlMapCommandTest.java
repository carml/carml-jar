package io.carml.jar.runner;

import static com.github.stefanbirkner.systemlambda.SystemLambda.catchSystemExit;
import static io.carml.jar.runner.TestApplication.getStringForPath;
import static io.carml.jar.runner.TestApplication.getTestSourcePath;
import static io.carml.jar.runner.format.RdfFormat.ttl;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static picocli.CommandLine.ExitCode.USAGE;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.carml.engine.target.TargetWriter;
import io.carml.engine.target.TargetWriterFactory;
import io.carml.jar.runner.input.Rdf4jModelLoader;
import io.carml.jar.runner.option.LoggingOptions;
import io.carml.jar.runner.option.OutputRdfFormats;
import io.carml.jar.runner.output.OutputHandler;
import io.carml.jar.runner.prefix.DefaultNamespacePrefixMapper;
import io.carml.model.FilePath;
import io.carml.model.LogicalTarget;
import io.carml.model.Target;
import io.carml.model.impl.CarmlLogicalSource;
import io.carml.model.impl.CarmlLogicalTarget;
import io.carml.model.impl.CarmlSubjectMap;
import io.carml.model.impl.CarmlTarget;
import io.carml.model.impl.CarmlTriplesMap;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.util.ModelCollector;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFWriterRegistry;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class CarmlMapCommandTest {

  private static final Path TEST_PATH = getTestSourcePath(Paths.get("carml-map-command"));

  private OutputHandler outputHandler;

  private CommandLine commandLine;

  @Captor
  private ArgumentCaptor<Flux<Statement>> statementsCaptor;

  @Captor
  private ArgumentCaptor<Flux<byte[]>> bytesCaptor;

  @TempDir
  private Path tmpOutputDir;

  @BeforeEach
  void setUp() {
    outputHandler = spy(new TestOutputHandler());
    var modelLoader = new Rdf4jModelLoader();
    var prefixMapper = new DefaultNamespacePrefixMapper(new ObjectMapper(), new YAMLMapper());
    var mapCommand = new CarmlMapCommand(modelLoader, outputHandler, prefixMapper, List.of());

    var rdfFormats = RDFWriterRegistry.getInstance()
        .getKeys()
        .stream()
        .map(RDFFormat::getDefaultFileExtension)
        .collect(Collectors.toCollection(LinkedHashSet::new));

    commandLine = new CommandLine(new CarmlCommand(), createFactory(new OutputRdfFormats(rdfFormats)))
        .setExecutionStrategy(LoggingOptions::executionStrategy)
        .addSubcommand("map", mapCommand);
  }

  private static CommandLine.IFactory createFactory(OutputRdfFormats outputRdfFormats) {
    return new CommandLine.IFactory() {
      @Override
      public <K> K create(Class<K> cls) throws Exception {
        if (cls.isAssignableFrom(OutputRdfFormats.class)) {
          return cls.cast(outputRdfFormats);
        }
        return CommandLine.defaultFactory()
            .create(cls);
      }
    };
  }

  @Test
  void givenMappingAndRelativeSourceLocationArgs_whenMapCommandRun_thenReturnStreamingNqOutput() {
    // Given
    var mapping = getStringForPath(TEST_PATH, "mapping", "mapping.rml.ttl");
    var relativeSourceLocation = getStringForPath(TEST_PATH, "source");
    var args = new String[] {"map", "-m", mapping, "-rsl", relativeSourceLocation};

    // When
    commandLine.execute(args);

    // Then
    verify(outputHandler).outputStreamingBytes(bytesCaptor.capture(), eq(System.out));
  }

  @Test
  void givenMappingAndSystemIn_whenMapCommandRun_thenReturnStreamingNqOutput() {
    // Given
    var mapping = getStringForPath(TEST_PATH, "mapping", "mapping.inputstream.rml.ttl");
    var args = new String[] {"map", "-m", mapping};
    var stdin = System.in;
    var inputStream = IOUtils.toInputStream(String.format("id,make%n1,Toyota%n2,Mercedes"), StandardCharsets.UTF_8);
    System.setIn(inputStream);

    // When
    commandLine.execute(args);

    // Then
    System.setIn(stdin);
    verify(outputHandler).outputStreamingBytes(bytesCaptor.capture(), eq(System.out));
  }

  @Test
  void givenMappingAndMappingFormatArgs_whenMapCommandRun_thenReturnStreamingNqOutput() {
    // Given
    var mapping = getStringForPath(TEST_PATH, "mapping", "mapping.rml.ttl");
    var relativeSourceLocation = getStringForPath(TEST_PATH, "source");
    var mappingFormat = "ttl";
    var args = new String[] {"map", "-m", mapping, "-rsl", relativeSourceLocation, "-f", mappingFormat};

    // When
    commandLine.execute(args);

    // Then
    verify(outputHandler).outputStreamingBytes(bytesCaptor.capture(), eq(System.out));
  }

  @Test
  void givenMappingAndStreamableOutputFormatArgs_whenMapCommandRun_thenReturnStreamingFormattedOutput() {
    // Given
    var mapping = getStringForPath(TEST_PATH, "mapping", "mapping.rml.ttl");
    var relativeSourceLocation = getStringForPath(TEST_PATH, "source");
    var outputFormat = "ttl";
    var args = new String[] {"map", "-m", mapping, "-rsl", relativeSourceLocation, "-of", outputFormat};

    // When
    commandLine.execute(args);

    // Then
    verify(outputHandler).outputStreaming(statementsCaptor.capture(), eq(ttl.name()), eq(Map.of()), eq(System.out));
    var model = statementsCaptor.getValue()
        .collect(new ModelCollector())
        .block();
    assertNotNull(model);
    assertThat(model.size(), is(2));
  }

  @Test
  void givenMappingAndOutputFormatAndPrettyArgs_whenMapCommandRun_thenReturnPrettyFormattedOutput() {
    // Given
    var mapping = getStringForPath(TEST_PATH, "mapping", "mapping.rml.ttl");
    var relativeSourceLocation = getStringForPath(TEST_PATH, "source");
    var outputFormat = "ttl";
    var prefixes = "schema=https://schema.org/";
    var args =
        new String[] {"map", "-m", mapping, "-rsl", relativeSourceLocation, "-p", prefixes, "-of", outputFormat, "-P"};

    // When
    commandLine.execute(args);

    // Then
    verify(outputHandler).outputPretty(statementsCaptor.capture(), eq(ttl.name()),
        eq(Map.of("schema", "https://schema.org/")), eq(System.out));
    var model = statementsCaptor.getValue()
        .collect(new ModelCollector())
        .block();
    assertNotNull(model);
    assertThat(model.size(), is(2));
  }

  @Test
  void givenMappingAndOutputPathArgs_whenMapCommandRun_thenOutputsNqToPathViaBufferedOutputStream() {
    // Given
    var mapping = getStringForPath(TEST_PATH, "mapping", "mapping.rml.ttl");
    var relativeSourceLocation = getStringForPath(TEST_PATH, "source");
    var outputPath = tmpOutputDir.resolve("out.nq")
        .toString();
    var prefixes = "schema=https://schema.org/";
    var args = new String[] {"map", "-m", mapping, "-rsl", relativeSourceLocation, "-p", prefixes, "-o", outputPath};

    // When
    commandLine.execute(args);

    // Then
    verify(outputHandler).outputStreamingBytes(bytesCaptor.capture(), isA(BufferedOutputStream.class));
  }

  @Test
  void givenIncorrectPrefixMapping_whenMapCommandRun_thenExitWithUsageCode() throws Exception {
    // Given
    var mapping = getStringForPath(TEST_PATH, "mapping", "mapping.rml.ttl");
    var relativeSourceLocation = getStringForPath(TEST_PATH, "source");
    var args =
        new String[] {"map", "-m", mapping, "-rsl", relativeSourceLocation, "-pm", relativeSourceLocation, "-p", "foo"};

    // When
    var exitCode = catchSystemExit(() -> {
      int result = commandLine.execute(args);
      System.exit(result);
    });

    // Then
    assertThat(exitCode, is(USAGE));
  }

  @Test
  void givenLimitArg_whenMapCommandRun_thenReturnLimitedOutput() {
    // Given
    var mapping = getStringForPath(TEST_PATH, "mapping", "mapping.rml.ttl");
    var relativeSourceLocation = getStringForPath(TEST_PATH, "source");
    var limit = "1";
    var args = new String[] {"map", "-m", mapping, "-rsl", relativeSourceLocation, "-l", limit};

    // When
    commandLine.execute(args);

    // Then
    verify(outputHandler).outputStreamingBytes(bytesCaptor.capture(), eq(System.out));
  }

  @Test
  void givenBaseIriArg_whenMapCommandRun_thenReturnOutputWithBaseIri() {
    // Given
    var mapping = getStringForPath(TEST_PATH, "mapping", "mapping.rml.ttl");
    var relativeSourceLocation = getStringForPath(TEST_PATH, "source");
    var baseIri = "https://foo.bar/";
    var args = new String[] {"map", "-m", mapping, "-rsl", relativeSourceLocation, "-b", baseIri};

    // When
    commandLine.execute(args);

    // Then
    verify(outputHandler).outputStreamingBytes(bytesCaptor.capture(), eq(System.out));
  }

  @Test
  void givenReactiveEvaluatorArg_whenMapCommandRun_thenReturnStreamingNqOutput() {
    // Given
    var mapping = getStringForPath(TEST_PATH, "mapping", "mapping.rml.ttl");
    var relativeSourceLocation = getStringForPath(TEST_PATH, "source");
    var args = new String[] {"map", "-m", mapping, "-rsl", relativeSourceLocation, "-E", "reactive"};

    // When
    commandLine.execute(args);

    // Then
    verify(outputHandler).outputStreamingBytes(bytesCaptor.capture(), eq(System.out));
  }

  @Test
  void givenInProcessDbEvaluatorArg_whenMapCommandRun_thenReturnStreamingNqOutput() {
    // Given
    var mapping = getStringForPath(TEST_PATH, "mapping", "mapping.rml.ttl");
    var relativeSourceLocation = getStringForPath(TEST_PATH, "source");
    var args = new String[] {"map", "-m", mapping, "-rsl", relativeSourceLocation, "-E", "in-process-db"};

    // When
    int exitCode = commandLine.execute(args);

    // Then
    verify(outputHandler).outputStreamingBytes(bytesCaptor.capture(), eq(System.out));
    assertThat(exitCode, is(0));
  }

  @Test
  void givenSpillToDiskArg_whenMapCommandRun_thenReturnStreamingNqOutput() {
    // Given
    var mapping = getStringForPath(TEST_PATH, "mapping", "mapping.rml.ttl");
    var relativeSourceLocation = getStringForPath(TEST_PATH, "source");
    var args = new String[] {"map", "-m", mapping, "-rsl", relativeSourceLocation, "--spill-to-disk"};

    // When
    int exitCode = commandLine.execute(args);

    // Then
    verify(outputHandler).outputStreamingBytes(bytesCaptor.capture(), eq(System.out));
    assertThat(exitCode, is(0));
  }

  @Test
  void givenInProcessDbEvaluatorAndSpillToDiskArgs_whenMapCommandRun_thenReturnStreamingNqOutput() {
    // Given
    var mapping = getStringForPath(TEST_PATH, "mapping", "mapping.rml.ttl");
    var relativeSourceLocation = getStringForPath(TEST_PATH, "source");
    var args =
        new String[] {"map", "-m", mapping, "-rsl", relativeSourceLocation, "-E", "in-process-db", "--spill-to-disk"};

    // When
    int exitCode = commandLine.execute(args);

    // Then
    verify(outputHandler).outputStreamingBytes(bytesCaptor.capture(), eq(System.out));
    assertThat(exitCode, is(0));
  }

  @Test
  void givenStreamEvaluatorAndSpillToDiskArg_whenMapCommandRun_thenSpillToDiskAppliesToReactive() {
    // Given
    var mapping = getStringForPath(TEST_PATH, "mapping", "mapping.rml.ttl");
    var relativeSourceLocation = getStringForPath(TEST_PATH, "source");
    var args = new String[] {"map", "-m", mapping, "-rsl", relativeSourceLocation, "-E", "reactive", "--spill-to-disk"};

    // When
    int exitCode = commandLine.execute(args);

    // Then — flag now wires the DuckDB-backed JoinExecutor for reactive joins; mapping completes
    // successfully without warnings.
    verify(outputHandler).outputStreamingBytes(bytesCaptor.capture(), eq(System.out));
    assertThat(exitCode, is(0));
  }

  @Test
  void givenReactiveSpillThresholdArg_whenMapCommandRun_thenParsedAndAccepted() {
    // Given
    var mapping = getStringForPath(TEST_PATH, "mapping", "mapping.rml.ttl");
    var relativeSourceLocation = getStringForPath(TEST_PATH, "source");
    var args = new String[] {"map", "-m", mapping, "-rsl", relativeSourceLocation, "-E", "reactive", "--spill-to-disk",
        "--reactive-spill-threshold", "1000"};

    // When
    int exitCode = commandLine.execute(args);

    // Then — option parses; reactive evaluator with spill threshold 1000 runs successfully.
    verify(outputHandler).outputStreamingBytes(bytesCaptor.capture(), eq(System.out));
    assertThat(exitCode, is(0));
  }

  @Test
  void givenMappingAndDefaultBytePipeline_whenMapCommandRun_thenBytesActuallyWritten() {
    // Given — use a real OutputHandler that inherits the default outputStreamingBytes
    // implementation to verify bytes are actually subscribed and written through the pipeline.
    var mapCommand = getCarmlMapCommand();

    var rdfFormats = RDFWriterRegistry.getInstance()
        .getKeys()
        .stream()
        .map(RDFFormat::getDefaultFileExtension)
        .collect(Collectors.toCollection(LinkedHashSet::new));

    var localCommandLine = new CommandLine(new CarmlCommand(), createFactory(new OutputRdfFormats(rdfFormats)))
        .setExecutionStrategy(LoggingOptions::executionStrategy)
        .addSubcommand("map", mapCommand);

    var mapping = getStringForPath(TEST_PATH, "mapping", "mapping.rml.ttl");
    var relativeSourceLocation = getStringForPath(TEST_PATH, "source");
    var outputPath = tmpOutputDir.resolve("out.nq")
        .toString();
    var args = new String[] {"map", "-m", mapping, "-rsl", relativeSourceLocation, "-o", outputPath};

    // When
    int exitCode = localCommandLine.execute(args);

    // Then — verify bytes were actually written to the output file
    assertThat(exitCode, is(0));
    var outputFile = Path.of(outputPath);
    assertThat(Files.exists(outputFile), is(true));
    try {
      var content = Files.readString(outputFile, StandardCharsets.UTF_8);
      assertThat(content.isBlank(), is(false));
      // NQ format: each line is a quad/triple
      var lines = content.strip()
          .split("\n");
      assertThat(lines.length, is(2));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void givenMappingWithLogicalTarget_whenCollectLogicalTargets_thenUnionReturned() {
    // Given — collectLogicalTargets walks all term maps on the TriplesMap set and returns the
    // union of their declared rml:logicalTargets. Using an in-memory CARML model (bypassing
    // RmlMappingLoader) so this test asserts the routing-collection invariant in isolation
    // from the mapping-document parse path.
    var target = CarmlTarget.builder()
        .serialization(SimpleValueFactory.getInstance()
            .createIRI("http://www.w3.org/ns/formats/N-Quads"))
        .build();
    var logicalTarget = CarmlLogicalTarget.builder()
        .target(target)
        .build();
    var subjectMap = CarmlSubjectMap.builder()
        .reference("id")
        .logicalTargets(Set.of(logicalTarget))
        .build();

    var triplesMap = CarmlTriplesMap.builder()
        .logicalSource(CarmlLogicalSource.builder()
            .build())
        .subjectMap(subjectMap)
        .build();

    // When
    var collected = CarmlMapCommand.collectLogicalTargets(Set.of(triplesMap));

    // Then
    assertThat(collected, is(Set.of(logicalTarget)));
  }

  @Test
  void givenMappingWithoutLogicalTarget_whenCollectLogicalTargets_thenReturnsEmpty() {
    // Given — a mapping without any rml:logicalTarget declarations.
    var subjectMap = CarmlSubjectMap.builder()
        .reference("id")
        .build();

    var triplesMap = CarmlTriplesMap.builder()
        .logicalSource(CarmlLogicalSource.builder()
            .build())
        .subjectMap(subjectMap)
        .build();

    // When
    var collected = CarmlMapCommand.collectLogicalTargets(Set.of(triplesMap));

    // Then — the CLI falls back to the existing unrouted path when this set is empty.
    assertThat(collected.isEmpty(), is(true));
  }

  @Test
  void buildTargetRouter_withFilePathTarget_returnsRouter() {
    // Given - a LogicalTarget whose Target also implements FilePath. The `target instanceof
    // FilePath` branch in buildTargetRouter binds it to a file writer via the factory. Mockito
    // with extraInterfaces decouples this test from the model-layer wiring (CarmlFilePath is
    // the production combined-type class today), keeping the unit test focused on the
    // routing-construction branching rather than model internals.
    var serialization = SimpleValueFactory.getInstance()
        .createIRI("http://www.w3.org/ns/formats/N-Quads");
    var fileTarget = mock(Target.class, withSettings().extraInterfaces(FilePath.class));
    when(fileTarget.getSerialization()).thenReturn(serialization);
    when(((FilePath) fileTarget).getPath()).thenReturn(tmpOutputDir.resolve("routed.nq")
        .toString());
    var logicalTarget = mock(LogicalTarget.class);
    when(logicalTarget.getTarget()).thenReturn(fileTarget);

    var targetWriterFactory = TargetWriterFactory.builder()
        .build();
    var defaultWriter = mock(TargetWriter.class);

    // When
    try (var router = CarmlMapCommand.buildTargetRouter(Set.of(logicalTarget), targetWriterFactory, defaultWriter)) {

      // Then - one registered file writer + the supplied default writer.
      assertThat(router.getTargetWriterCount(), is(2));
      assertThat(router.getLogicalTargets(), is(Set.of(logicalTarget)));
      assertThat(router.hasDefaultWriter(), is(true));
    }
  }

  @Test
  @SuppressWarnings("resource")
  void buildTargetRouter_withLogicalTargetLevelProperties_overridesTargetLevelProperties() {
    // Given - a LogicalTarget that declares its own serialization/encoding/compression. Per
    // RML-IO precedence, LogicalTarget-level values win over the nested Target equivalents. The
    // factory is a spy so we can observe which values it received at the four-arg entry point
    // without running actual I/O. Target-level getters are NOT stubbed: with all three LT-level
    // values non-null, the precedence rule short-circuits before touching them.
    var vf = SimpleValueFactory.getInstance();
    var ltSerialization = vf.createIRI("http://www.w3.org/ns/formats/N-Triples");
    var ltEncoding = vf.createIRI("http://w3id.org/rml/UTF-16");
    var ltCompression = vf.createIRI("http://w3id.org/rml/gzip");

    var fileTarget = mock(Target.class, withSettings().extraInterfaces(FilePath.class));

    var logicalTarget = mock(LogicalTarget.class);
    when(logicalTarget.getTarget()).thenReturn(fileTarget);
    when(logicalTarget.getSerialization()).thenReturn(ltSerialization);
    when(logicalTarget.getEncoding()).thenReturn(ltEncoding);
    when(logicalTarget.getCompression()).thenReturn(ltCompression);

    var factory = spy(TargetWriterFactory.builder()
        .build());
    var stubbedWriter = mock(TargetWriter.class);
    // doReturn is required over when(factory.method()).thenReturn — the latter would invoke the
    // real createFileWriter during stub setup, which then resolves the non-null FilePath path
    // against the filesystem.
    doReturn(stubbedWriter).when(factory)
        .createFileWriter(isA(FilePath.class), eq(ltSerialization), eq(ltEncoding), eq(ltCompression));
    var defaultWriter = mock(TargetWriter.class);

    // When
    try (var router = CarmlMapCommand.buildTargetRouter(Set.of(logicalTarget), factory, defaultWriter)) {
      assertThat(router.getLogicalTargets(), is(Set.of(logicalTarget)));
    }

    // Then - the factory received the LogicalTarget-level values; Target-level values MUST NOT
    // have leaked through.
    verify(factory).createFileWriter(isA(FilePath.class), eq(ltSerialization), eq(ltEncoding), eq(ltCompression));
    // Pin the short-circuit contract: with all three LogicalTarget-level values non-null, the
    // routing logic must not consult any target-level getter.
    verify(fileTarget, never()).getSerialization();
    verify(fileTarget, never()).getEncoding();
    verify(fileTarget, never()).getCompression();
  }

  @Test
  @SuppressWarnings("resource")
  void buildTargetRouter_withTargetLevelPropertiesOnly_usesTargetLevelProperties() {
    // Given - a LogicalTarget with null serialization/encoding/compression; the FilePath-typed
    // Target carries them. Per the LogicalTarget-over-Target precedence rule, the Target-level
    // values flow through unchanged.
    var vf = SimpleValueFactory.getInstance();
    var tgtSerialization = vf.createIRI("http://www.w3.org/ns/formats/N-Quads");
    var tgtEncoding = vf.createIRI("http://w3id.org/rml/UTF-8");
    var tgtCompression = vf.createIRI("http://w3id.org/rml/gzip");

    var fileTarget = mock(Target.class, withSettings().extraInterfaces(FilePath.class));
    when(fileTarget.getSerialization()).thenReturn(tgtSerialization);
    when(fileTarget.getEncoding()).thenReturn(tgtEncoding);
    when(fileTarget.getCompression()).thenReturn(tgtCompression);
    // No stub on FilePath.getPath — the spy short-circuits createFileWriter before path
    // resolution runs.

    var logicalTarget = mock(LogicalTarget.class);
    when(logicalTarget.getTarget()).thenReturn(fileTarget);
    // LogicalTarget getters return null by default on an un-stubbed mock, so no when(...) calls
    // are needed here; stubbing them explicitly would trigger UnnecessaryStubbing under Mockito's
    // strict mode.

    var factory = spy(TargetWriterFactory.builder()
        .build());
    var stubbedWriter = mock(TargetWriter.class);
    // See note on doReturn vs when(...).thenReturn in the sibling test above.
    doReturn(stubbedWriter).when(factory)
        .createFileWriter(isA(FilePath.class), eq(tgtSerialization), eq(tgtEncoding), eq(tgtCompression));
    var defaultWriter = mock(TargetWriter.class);

    // When
    try (var router = CarmlMapCommand.buildTargetRouter(Set.of(logicalTarget), factory, defaultWriter)) {
      assertThat(router.getLogicalTargets(), is(Set.of(logicalTarget)));
    }

    // Then - the factory got the Target-level values because the LogicalTarget declared none.
    verify(factory).createFileWriter(isA(FilePath.class), eq(tgtSerialization), eq(tgtEncoding), eq(tgtCompression));
  }

  @Test
  @SuppressWarnings("resource")
  void buildTargetRouter_withPartialLogicalTargetOverride_usesLtEncodingAndTargetCompressionSerialization() {
    // Given - only LogicalTarget.encoding is declared; serialization and compression fall through
    // to target-level values. This pins the per-property null-check contract inside
    // buildWriterForLogicalTarget: each property is resolved independently, so a partial
    // LogicalTarget override must mix LT-level and Target-level values in the factory call.
    var vf = SimpleValueFactory.getInstance();
    var ltEncoding = vf.createIRI("http://w3id.org/rml/UTF-16");
    // Target-level serialization/compression supply the fallback values.
    var tgtSerialization = vf.createIRI("http://www.w3.org/ns/formats/N-Quads");
    var tgtCompression = vf.createIRI("http://w3id.org/rml/gzip");

    var fileTarget = mock(Target.class, withSettings().extraInterfaces(FilePath.class));
    when(fileTarget.getSerialization()).thenReturn(tgtSerialization);
    when(fileTarget.getCompression()).thenReturn(tgtCompression);
    // Target-level encoding is intentionally NOT stubbed: the LT-level value short-circuits the
    // null check, so target.getEncoding() must never be invoked. The verify(..., never()) below
    // pins that invariant.

    var logicalTarget = mock(LogicalTarget.class);
    when(logicalTarget.getTarget()).thenReturn(fileTarget);
    when(logicalTarget.getEncoding()).thenReturn(ltEncoding);
    // serialization/compression on LogicalTarget default to null on the un-stubbed mock — no
    // explicit stub needed and explicitly stubbing them to null would trigger Mockito's
    // UnnecessaryStubbing under strict mode.

    var factory = spy(TargetWriterFactory.builder()
        .build());
    var stubbedWriter = mock(TargetWriter.class);
    // doReturn-over-when keeps the spy from calling through to createFileWriter during stub
    // setup — mirrors the pattern in the sibling LT-override/target-only tests.
    doReturn(stubbedWriter).when(factory)
        .createFileWriter(isA(FilePath.class), eq(tgtSerialization), eq(ltEncoding), eq(tgtCompression));
    var defaultWriter = mock(TargetWriter.class);

    // When
    try (var router = CarmlMapCommand.buildTargetRouter(Set.of(logicalTarget), factory, defaultWriter)) {
      assertThat(router.getLogicalTargets(), is(Set.of(logicalTarget)));
    }

    // Then - the factory received the MIXED quadruple: LT encoding, Target serialization, Target
    // compression.
    verify(factory).createFileWriter(isA(FilePath.class), eq(tgtSerialization), eq(ltEncoding), eq(tgtCompression));
    // Encoding short-circuit contract: with LT-level encoding non-null, target.getEncoding() must
    // never be consulted. The other two target-level getters ARE called (verified implicitly by
    // the factory call above).
    verify(fileTarget, never()).getEncoding();
  }

  @Test
  void buildTargetRouter_withNullTarget_throwsUnsupportedOperationException() {
    // Given - a LogicalTarget whose getTarget() returns null. The buildWriterForLogicalTarget
    // helper handles this defensively: rather than NPE'ing it emits a diagnostic pointing at
    // the "null" placeholder branch. The error message must also include the "file an issue"
    // hint so users have an actionable next step.
    var logicalTarget = mock(LogicalTarget.class);
    when(logicalTarget.getTarget()).thenReturn(null);
    when(logicalTarget.getResourceName()).thenReturn(":nullTarget");

    var targetWriterFactory = TargetWriterFactory.builder()
        .build();
    var logicalTargets = Set.of(logicalTarget);

    // When / Then
    try (var defaultWriter = mock(TargetWriter.class)) {
      var thrown = assertThrows(UnsupportedOperationException.class,
          () -> CarmlMapCommand.buildTargetRouter(logicalTargets, targetWriterFactory, defaultWriter));

      // "null" placeholder from target.getClass().getSimpleName() ternary branch.
      assertThat(thrown.getMessage(), containsString("null"));
      // GitHub-issue hint preserved across both the non-FilePath and null-target branches.
      assertThat(thrown.getMessage(), containsString("file an issue"));
    }
  }

  @Test
  void buildTargetRouter_withNonFilePathTarget_throwsUnsupportedOperationException() {
    // Given - a Target that is NOT a FilePath. The buildTargetRouter must reject it because
    // routing to non-file targets (SPARQL endpoints, etc.) is out of scope for Task 7.11. The
    // exception message must include the target class name and a hint pointing at the file an
    // issue path so users have an actionable diagnostic.
    var nonFileTarget = mock(Target.class);
    var logicalTarget = mock(LogicalTarget.class);
    when(logicalTarget.getTarget()).thenReturn(nonFileTarget);
    when(logicalTarget.getResourceName()).thenReturn(":sparqlTarget");

    var targetWriterFactory = TargetWriterFactory.builder()
        .build();
    var logicalTargets = Set.of(logicalTarget);

    // When / Then
    try (var defaultWriter = mock(TargetWriter.class)) {
      var thrown = assertThrows(UnsupportedOperationException.class,
          () -> CarmlMapCommand.buildTargetRouter(logicalTargets, targetWriterFactory, defaultWriter));

      // The message includes the target class simple name (Mockito-generated mock subclass name
      // contains "Target") and the GitHub-issue hint.
      assertThat(thrown.getMessage(), containsString(nonFileTarget.getClass()
          .getSimpleName()));
      assertThat(thrown.getMessage(), containsString("file an issue"));
    }
  }

  private static @NonNull CarmlMapCommand getCarmlMapCommand() {
    var realOutputHandler = new OutputHandler() {
      @Override
      public long outputPretty(@NonNull Flux<Statement> statementFlux, @NonNull String rdfFormat,
          @NonNull Map<String, String> namespaces, @NonNull OutputStream outputStream) {
        return 0;
      }

      @Override
      public long outputStreaming(@NonNull Flux<Statement> statementFlux, @NonNull String rdfFormat,
          @NonNull Map<String, String> namespaces, @NonNull OutputStream outputStream) {
        return 0;
      }

      @Override
      public boolean isFormatStreamable(@NonNull String rdfFormat, boolean pretty) {
        return !pretty;
      }
    };

    var modelLoader = new Rdf4jModelLoader();
    var prefixMapper = new DefaultNamespacePrefixMapper(new ObjectMapper(), new YAMLMapper());

    return new CarmlMapCommand(modelLoader, realOutputHandler, prefixMapper, List.of());
  }

  /**
   * Test-only OutputHandler that returns stub values.
   */
  private static class TestOutputHandler implements OutputHandler {

    @Override
    public long outputPretty(@NonNull Flux<Statement> statementFlux, @NonNull String format,
        @NonNull Map<String, String> namespaces, @NonNull OutputStream outputStream) {
      return 1;
    }

    @Override
    public long outputStreaming(@NonNull Flux<Statement> statementFlux, @NonNull String format,
        @NonNull Map<String, String> namespaces, @NonNull OutputStream outputStream) {
      return 2;
    }

    @Override
    public long outputStreamingBytes(@NonNull Flux<byte[]> byteFlux, @NonNull OutputStream outputStream) {
      return 2;
    }

    @Override
    public boolean isFormatStreamable(@NonNull String rdfFormat, boolean pretty) {
      return !pretty;
    }
  }
}
