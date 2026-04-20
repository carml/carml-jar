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
import static org.mockito.Mockito.mock;
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
  void givenStreamEvaluatorAndSpillToDiskArg_whenMapCommandRun_thenSpillToDiskArgIgnored() {
    // Given
    var mapping = getStringForPath(TEST_PATH, "mapping", "mapping.rml.ttl");
    var relativeSourceLocation = getStringForPath(TEST_PATH, "source");
    var args = new String[] {"map", "-m", mapping, "-rsl", relativeSourceLocation, "-E", "reactive", "--spill-to-disk"};

    // When
    int exitCode = commandLine.execute(args);

    // Then - flag is silently ignored; reactive evaluator runs successfully
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
    // RmlMappingLoader) so we can assert the routing happens independently of the end-to-end
    // mapping-document parse path, which currently does not bind rml:FilePath as a rml:target
    // (model-layer gap tracked separately for Task 7.12).
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
    // FilePath` branch in buildTargetRouter binds it to a file writer via the factory. Use
    // Mockito with extraInterfaces because the production model has no concrete class that
    // implements both Target and FilePath today (model-layer gap tracked in Task 7.12).
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
