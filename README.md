# carml-jar

**A CLI for CARML**

[![Build](https://github.com/carml/carml-jar/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/carml/carml-jar/actions/workflows/build.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=carml_carml-jar&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=carml_carml-jar)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=carml_carml-jar&metric=coverage)](https://sonarcloud.io/summary/new_code?id=carml_carml-jar)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=carml_carml-jar&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=carml_carml-jar)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=carml_carml-jar&metric=code_smells)](https://sonarcloud.io/summary/new_code?id=carml_carml-jar)

## Table of contents

- [Introduction](#introduction)
- [Usage](#usage)
- [Plan command](#plan-command)
- [CARML jar RDF4J](#carml-jar-rdf4j-output)
- [CARML jar Jena](#carml-jar-jena-output)
- [Monitoring](#monitoring)
- [Building the project](#building-the-project)
- [Customizing the mapper](#customizing-the-mapper)

## Introduction
CARML jar is a CLI application for executing RML mappings with [CARML](https://github.com/carml/carml).

CARML jar runs on JDK 17.

This project produces two artifacts:
* CARML jar RDF4J - which outputs RDF using [RDF4J](https://rdf4j.org/)
* CARML jar Jena - which outputs RDF using [Apache Jena](https://jena.apache.org/)

## Usage

### Map RDF file to output

```console
Usage:  map [-hVPvS] [-F=<outputRdfFormat>] [-o=<outputPath>]
            [-M=<prefixMappings>]... [-p=<prefixDeclarations>[,
            <prefixDeclarations>...]]... [-b=<baseIri>] [-l=<limit>]
            [-E=<evaluatorMode>] [--spill-to-disk]
            [--in-process-db-memory=<inProcessDbMemory>]
            [--reactive-spill-threshold=<reactiveSpillThreshold>] [--metrics
            [=<metricsEndpoint>]] (-m=<mappingFiles> [-m=<mappingFiles>]...
            [-f=<mappingFileRdfFormat>] [-r=<relativeSourceLocation>])
  -h, --help                 Show this help message and exit.
  -V, --version              Print version information and exit.
  -m, --mapping=<mappingFiles>
                             Mapping file path(s) and/or mapping file directory
                               path(s).
  -f, --format=<mappingFileRdfFormat>
                             Mapping file RDF format:
                             ttl (text/turtle),
                             ttls (application/x-turtlestar),
                             nt (application/n-triples),
                             nq (application/n-quads),
                             rdf (application/rdf+xml),
                             jsonld (application/ld+json),
                             ndjsonld (application/x-ld+ndjson),
                             trig (application/trig),
                             trigs (application/x-trigstar),
                             n3 (text/n3),
                             trix (application/trix),
                             brf (application/x-binary-rdf),
                             rj (application/rdf+json).
  -r, -rsl, --rel-src-loc=<relativeSourceLocation>
                             Path from which to relatively find the sources
                               specified in the mapping files.
  -F, -of, --outformat=<outputRdfFormat>
                             Output RDF format. Default: nq.
                             Supported values are brf, jsonld, n3, ndjsonld,
                               nq, nt, rdf, rj, trig, trigs, ttl, ttls, xml
  -o, --output=<outputPath>  Output file path.
                             If path is directory, will default to fileName
                               `output`.
                             If left empty will output to console.
  -M, -pm, --prefix-mapping=<prefixMappings>
                             File or directory path(s) containing prefix
                               mappings.
                             Files must be JSON or YAML files containing a map
                               of prefix declarations.
                             File names must have either .json or .yaml/.yml
                               file extensions.
  -p, --prefixes=<prefixDeclarations>[,<prefixDeclarations>...]
                             Declares which prefixes to apply to the output.
                             Can be a prefix reference or an inline prefix
                               declaration.
                             A prefix reference will be resolved against the
                               provided prefix mapping (`-pm`), or the default
                               prefix mapping.
                             An inline prefix declaration can be provided as
                               'prefix=iri. For example: ex=http://example.com/'
                             Multiple declarations can be separated by ','. For
                               example: ex=http://example.com/,foo,bar
  -P, --pretty               Serialize pretty printed output. (Caution: will
                               cause in-memory output collection).
  -b, --base-iri=<baseIri>   Base IRI to use to expand relative IRIs in the
                               generated output.
                             If not specified the default `http://example.
                               com/base/` is used.
  -l, --limit=<limit>        Limit the number of statements generated by the
                               amount provided.
  -v, --verbose              Specify multiple -v or --verbose options to
                               increase verbosity.
                             For example `-v -v`, or `-vv` or `--verbose
                               --verbose`
  -S, --strict               Enable strict mode.
                             Raises an error if a reference expression never
                               produces a value across all records of a logical
                               source.
  -E, --evaluator=<evaluatorMode>
                             Logical view evaluator mode.
                             auto: Select best evaluator per view via
                               ServiceLoader (default).
                             reactive: Force reactive evaluator for all views.
                             in-process-db: Force in-process database evaluator
                               for all views.
      --spill-to-disk        Enable spill-to-disk for larger-than-memory
                               workloads.
                             For the in-process-db evaluator: uses an on-disk
                               database instead of in-memory;
                             memory and threads are auto-tuned. Minimum 512 MB
                               system/container memory recommended.
                             For the reactive evaluator: routes joins through
                               an in-process-DB join executor that
                             switches from in-memory probe to SQL HASH JOIN
                               once parent rows exceed
                             --reactive-spill-threshold, with intermediates
                               spilled to the system temp directory.
                             In Docker, mount a volume to /carml-spill for
                               database and spill files:
                               docker run -v /tmp/carml-spill:/carml-spill
                               carml map --spill-to-disk -m mapping.ttl
      --in-process-db-memory=<inProcessDbMemory>
                             Memory limit for the in-process database (e.g.
                               '4GB', '512MB').
                             Overrides the auto-tuned value. Only effective
                               with --spill-to-disk.
                             Default: system memory minus JVM heap minus 512 MB
                               overhead.
      --reactive-spill-threshold=<reactiveSpillThreshold>
                             Parent-row count threshold for spilling reactive
                               joins to disk.
                             Below the threshold, joins use an in-memory probe
                               (fast).
                             Above, joins are routed through the in-process DB
                               with a spillable SQL hash join.
                             Default: 50000. Only effective with
                               --spill-to-disk.
      --metrics[=<metricsEndpoint>]
                             Push execution metrics to a Prometheus Pushgateway
                               after mapping completes.
                             Optionally specify host:port (default: localhost:
                               9091).
                             Also starts a Prometheus scrape endpoint on port
                               9092 for real-time monitoring.
                             Metrics include statement counts, durations,
                               iteration counts per TriplesMap.
                             Start the monitoring stack with: docker compose -f
                               docker/docker-compose.yml up -d
```

For example, the following command:

```console
java -jar carml-jar-X.jar map -m rml -rsl input -of ttl -P
```

* maps (`map`) all mapping files
* under the 'rml' directory (`-m rml`)
* using the relative source location 'input' (`-rsl input`)
* with output format Turtle (`-of ttl`)
* pretty printed (`-P`)
* to stdout.

#### Output

If an output path is provided (via `-o`) the RDF result is output to the specified path.

By default, if no output path is provided the plain RDF result will be output to `stdout`.

To get more information on what is going on during execution, the verbosity level can be increased via `-v`.
You can get more verbosity by adding more `v`s, for example `-vvv`, which is the highest level of verbosity and will
give `TRACE` level logging.

Errors logs are output to `stderr`.

##### Namespace Prefixes

To generate output with prefixed IRIs, namespace prefixes can be specified via `-p`.
The prefix definition can be provided inline by using the format `prefix=iri`. For example:

```console
-p ex=http://example.org/
```

Multiple prefix definitions can be separated by comma's.

The prefix can also reference a prefix defined in a prefix mapping document provided via `-pm`. For example:

```console
-p foo,bar -pm /path/to/prefix/mapping.json
```

A prefix mapping file is a JSON or a YAML file that provides a map of key values, where the key is the prefix and
the value is the name (the IRI). JSON files **must** have file name with a `.json` file extension and YAML files **
must**
have a `.yaml` or `.yml` file extension.

Example contents of a JSON prefix mapping file:

```json
{
  "foo": "http://foo.org/",
  "bar": "http://bar.org#"
}
```

Example contents of a YAML prefix mapping file:

```yaml
foo: http://foo.org/
bar: http://bar.org#
```

If no prefix mapping document is provided via `-pm` the default [prefix.cc](https://prefix.cc) mappings will be used.

#### Exit codes

The following exit codes are returned on exit.

* `0`: success
* `1`: "failure" when running the command
* `2`: command line usage error

### Using `stdin`
It is possible to map a data source provided via `stdin`. In this case the
[CARML input stream extension](https://github.com/carml/carml#input-stream-extension) can be used.
`stdin` is available as the default unnamed stream, and can be reference from a mapping as follows.

```turtle
@prefix carml: <http://carml.taxonic.com/carml/> .
...

:SomeLogicalSource
  rml:source [
    a carml:Stream ;
  ];
  ...
.
```

This makes it possible to pipe input into a mapping process. For example:

```console
cat some/input | java -jar carml-jar-X.jar map -m rml/mapping.ttl
```

## Plan command

The `plan` command analyzes a mapping and recommends the optimal execution strategy without executing it. It inspects source types, join patterns, and structural annotations to determine which evaluator (`reactive` or `in-process-db`) is best suited for each TriplesMap.

### When to use

Use `plan` before running large or complex mappings to:
- Understand which evaluator will be used for each TriplesMap
- Determine whether `--spill-to-disk` is needed
- Get a ready-to-use `carml map` command with the recommended options

### Usage

```console
carml plan -m mapping.ttl [-rsl ./data] [--source-rows name=count] [-i]
```

Options:
- `-m`, `--mapping` — Mapping file path(s), same as for `map`
- `-r`, `-rsl` — Relative source location, same as for `map`
- `--source-iterations name=count` — Pre-supply estimated iteration counts (repeatable). An iteration is one logical iteration: a row in CSV/SQL, a matched object in JSON, or a matched element in XML. The `name` matches the source file name shown in the plan output.
- `-i`, `--interactive` — Prompt for iteration counts for sources where estimates are missing

### How it works

The plan command:

1. **Loads and resolves** the mapping (same as `map` does at startup)
2. **Analyzes** each TriplesMap's source type, join pattern, and structural annotations
3. **Recommends** an evaluator per TriplesMap based on:
   - **Not in-process-db compatible** (XML, streams) → `reactive`
   - **Has joins, any source unknown or >100K rows** → `in-process-db` (SQL join pushdown prevents OOM)
   - **Has joins, all sources ≤100K rows** → `reactive` (in-memory join store is fine)
   - **No joins, >100K rows** → `in-process-db` (scales better for large data)
   - **No joins, ≤100K rows or unknown** → `reactive` (lower overhead)
4. **Generates** a recommended `carml map` command with the appropriate flags

### Pre-supplying row counts

For non-interactive use (e.g., CI pipelines), provide row counts via `--source-rows`:

```console
carml plan -m mapping.ttl --source-iterations source-file-1.csv=12950390 --source-iterations source-file2.json=47206
```

## CARML jar RDF4J output
The CARML jar RDF4J artifact supports the same output formats (`-of`) that are supported for the mapping file format
(`-f`).

See the `map` help-description for details:

```console
Usage:  map [-hVPvS] [-F=<outputRdfFormat>] [-o=<outputPath>]
            [-M=<prefixMappings>]... [-p=<prefixDeclarations>[,
            <prefixDeclarations>...]]... [-b=<baseIri>] [-l=<limit>]
            [-E=<evaluatorMode>] [--spill-to-disk]
            [--in-process-db-memory=<inProcessDbMemory>]
            [--reactive-spill-threshold=<reactiveSpillThreshold>] [--metrics
            [=<metricsEndpoint>]] (-m=<mappingFiles> [-m=<mappingFiles>]...
            [-f=<mappingFileRdfFormat>] [-r=<relativeSourceLocation>])
  -h, --help                 Show this help message and exit.
  -V, --version              Print version information and exit.
  -m, --mapping=<mappingFiles>
                             Mapping file path(s) and/or mapping file directory
                               path(s).
  -f, --format=<mappingFileRdfFormat>
                             Mapping file RDF format:
                             ttl (text/turtle),
                             ttls (application/x-turtlestar),
                             nt (application/n-triples),
                             nq (application/n-quads),
                             rdf (application/rdf+xml),
                             jsonld (application/ld+json),
                             ndjsonld (application/x-ld+ndjson),
                             trig (application/trig),
                             trigs (application/x-trigstar),
                             n3 (text/n3),
                             trix (application/trix),
                             brf (application/x-binary-rdf),
                             rj (application/rdf+json).
  -r, -rsl, --rel-src-loc=<relativeSourceLocation>
                             Path from which to relatively find the sources
                               specified in the mapping files.
  -F, -of, --outformat=<outputRdfFormat>
                             Output RDF format. Default: nq.
                             Supported values are brf, jsonld, n3, ndjsonld,
                               nq, nt, rdf, rj, trig, trigs, ttl, ttls, xml
  -o, --output=<outputPath>  Output file path.
                             If path is directory, will default to fileName
                               `output`.
                             If left empty will output to console.
  -M, -pm, --prefix-mapping=<prefixMappings>
                             File or directory path(s) containing prefix
                               mappings.
                             Files must be JSON or YAML files containing a map
                               of prefix declarations.
                             File names must have either .json or .yaml/.yml
                               file extensions.
  -p, --prefixes=<prefixDeclarations>[,<prefixDeclarations>...]
                             Declares which prefixes to apply to the output.
                             Can be a prefix reference or an inline prefix
                               declaration.
                             A prefix reference will be resolved against the
                               provided prefix mapping (`-pm`), or the default
                               prefix mapping.
                             An inline prefix declaration can be provided as
                               'prefix=iri. For example: ex=http://example.com/'
                             Multiple declarations can be separated by ','. For
                               example: ex=http://example.com/,foo,bar
  -P, --pretty               Serialize pretty printed output. (Caution: will
                               cause in-memory output collection).
  -b, --base-iri=<baseIri>   Base IRI to use to expand relative IRIs in the
                               generated output.
                             If not specified the default `http://example.
                               com/base/` is used.
  -l, --limit=<limit>        Limit the number of statements generated by the
                               amount provided.
  -v, --verbose              Specify multiple -v or --verbose options to
                               increase verbosity.
                             For example `-v -v`, or `-vv` or `--verbose
                               --verbose`
  -S, --strict               Enable strict mode.
                             Raises an error if a reference expression never
                               produces a value across all records of a logical
                               source.
  -E, --evaluator=<evaluatorMode>
                             Logical view evaluator mode.
                             auto: Select best evaluator per view via
                               ServiceLoader (default).
                             reactive: Force reactive evaluator for all views.
                             in-process-db: Force in-process database evaluator
                               for all views.
      --spill-to-disk        Enable spill-to-disk for larger-than-memory
                               workloads.
                             For the in-process-db evaluator: uses an on-disk
                               database instead of in-memory;
                             memory and threads are auto-tuned. Minimum 512 MB
                               system/container memory recommended.
                             For the reactive evaluator: routes joins through
                               an in-process-DB join executor that
                             switches from in-memory probe to SQL HASH JOIN
                               once parent rows exceed
                             --reactive-spill-threshold, with intermediates
                               spilled to the system temp directory.
                             In Docker, mount a volume to /carml-spill for
                               database and spill files:
                               docker run -v /tmp/carml-spill:/carml-spill
                               carml map --spill-to-disk -m mapping.ttl
      --in-process-db-memory=<inProcessDbMemory>
                             Memory limit for the in-process database (e.g.
                               '4GB', '512MB').
                             Overrides the auto-tuned value. Only effective
                               with --spill-to-disk.
                             Default: system memory minus JVM heap minus 512 MB
                               overhead.
      --reactive-spill-threshold=<reactiveSpillThreshold>
                             Parent-row count threshold for spilling reactive
                               joins to disk.
                             Below the threshold, joins use an in-memory probe
                               (fast).
                             Above, joins are routed through the in-process DB
                               with a spillable SQL hash join.
                             Default: 50000. Only effective with
                               --spill-to-disk.
      --metrics[=<metricsEndpoint>]
                             Push execution metrics to a Prometheus Pushgateway
                               after mapping completes.
                             Optionally specify host:port (default: localhost:
                               9091).
                             Also starts a Prometheus scrape endpoint on port
                               9092 for real-time monitoring.
                             Metrics include statement counts, durations,
                               iteration counts per TriplesMap.
                             Start the monitoring stack with: docker compose -f
                               docker/docker-compose.yml up -d
```

## CARML jar Jena output

The CARML jar Jena artifact transforms the output to Jena datastructures and uses Jena to generate the output.
This transformation has negligible performance impact and, since Jena is used as the output handler, the available
output formats (`-of`) are those that [Jena supports](https://jena.apache.org/documentation/io/#formats).

See the `map` help-description for details:

```console
Usage:  map [-hVPvS] [-F=<outputRdfFormat>] [-o=<outputPath>]
            [-M=<prefixMappings>]... [-p=<prefixDeclarations>[,
            <prefixDeclarations>...]]... [-b=<baseIri>] [-l=<limit>]
            [-E=<evaluatorMode>] [--spill-to-disk]
            [--in-process-db-memory=<inProcessDbMemory>]
            [--reactive-spill-threshold=<reactiveSpillThreshold>] [--metrics
            [=<metricsEndpoint>]] (-m=<mappingFiles> [-m=<mappingFiles>]...
            [-f=<mappingFileRdfFormat>] [-r=<relativeSourceLocation>])
  -h, --help                 Show this help message and exit.
  -V, --version              Print version information and exit.
  -m, --mapping=<mappingFiles>
                             Mapping file path(s) and/or mapping file directory
                               path(s).
  -f, --format=<mappingFileRdfFormat>
                             Mapping file RDF format:
                             ttl (text/turtle),
                             ttls (application/x-turtlestar),
                             nt (application/n-triples),
                             nq (application/n-quads),
                             rdf (application/rdf+xml),
                             jsonld (application/ld+json),
                             ndjsonld (application/x-ld+ndjson),
                             trig (application/trig),
                             trigs (application/x-trigstar),
                             n3 (text/n3),
                             trix (application/trix),
                             brf (application/x-binary-rdf),
                             rj (application/rdf+json).
  -r, -rsl, --rel-src-loc=<relativeSourceLocation>
                             Path from which to relatively find the sources
                               specified in the mapping files.
  -F, -of, --outformat=<outputRdfFormat>
                             Output RDF format. Default: nq.
                             Supported values are jsonld, jsonld11,
                               n3, nq, nt, owl, pbrdf, rdf, rj, rpb, rt,
                               shaclc, shc, trdf, trig, trix, ttl, xml
  -o, --output=<outputPath>  Output file path.
                             If path is directory, will default to fileName
                               `output`.
                             If left empty will output to console.
  -M, -pm, --prefix-mapping=<prefixMappings>
                             File or directory path(s) containing prefix
                               mappings.
                             Files must be JSON or YAML files containing a map
                               of prefix declarations.
                             File names must have either .json or .yaml/.yml
                               file extensions.
  -p, --prefixes=<prefixDeclarations>[,<prefixDeclarations>...]
                             Declares which prefixes to apply to the output.
                             Can be a prefix reference or an inline prefix
                               declaration.
                             A prefix reference will be resolved against the
                               provided prefix mapping (`-pm`), or the default
                               prefix mapping.
                             An inline prefix declaration can be provided as
                               'prefix=iri. For example: ex=http://example.com/'
                             Multiple declarations can be separated by ','. For
                               example: ex=http://example.com/,foo,bar
  -P, --pretty               Serialize pretty printed output. (Caution: will
                               cause in-memory output collection).
  -b, --base-iri=<baseIri>   Base IRI to use to expand relative IRIs in the
                               generated output.
                             If not specified the default `http://example.
                               com/base/` is used.
  -l, --limit=<limit>        Limit the number of statements generated by the
                               amount provided.
  -v, --verbose              Specify multiple -v or --verbose options to
                               increase verbosity.
                             For example `-v -v`, or `-vv` or `--verbose
                               --verbose`
  -S, --strict               Enable strict mode.
                             Raises an error if a reference expression never
                               produces a value across all records of a logical
                               source.
  -E, --evaluator=<evaluatorMode>
                             Logical view evaluator mode.
                             auto: Select best evaluator per view via
                               ServiceLoader (default).
                             reactive: Force reactive evaluator for all views.
                             in-process-db: Force in-process database evaluator
                               for all views.
      --spill-to-disk        Enable spill-to-disk for larger-than-memory
                               workloads.
                             For the in-process-db evaluator: uses an on-disk
                               database instead of in-memory;
                             memory and threads are auto-tuned. Minimum 512 MB
                               system/container memory recommended.
                             For the reactive evaluator: routes joins through
                               an in-process-DB join executor that
                             switches from in-memory probe to SQL HASH JOIN
                               once parent rows exceed
                             --reactive-spill-threshold, with intermediates
                               spilled to the system temp directory.
                             In Docker, mount a volume to /carml-spill for
                               database and spill files:
                               docker run -v /tmp/carml-spill:/carml-spill
                               carml map --spill-to-disk -m mapping.ttl
      --in-process-db-memory=<inProcessDbMemory>
                             Memory limit for the in-process database (e.g.
                               '4GB', '512MB').
                             Overrides the auto-tuned value. Only effective
                               with --spill-to-disk.
                             Default: system memory minus JVM heap minus 512 MB
                               overhead.
      --reactive-spill-threshold=<reactiveSpillThreshold>
                             Parent-row count threshold for spilling reactive
                               joins to disk.
                             Below the threshold, joins use an in-memory probe
                               (fast).
                             Above, joins are routed through the in-process DB
                               with a spillable SQL hash join.
                             Default: 50000. Only effective with
                               --spill-to-disk.
      --metrics[=<metricsEndpoint>]
                             Push execution metrics to a Prometheus Pushgateway
                               after mapping completes.
                             Optionally specify host:port (default: localhost:
                               9091).
                             Also starts a Prometheus scrape endpoint on port
                               9092 for real-time monitoring.
                             Metrics include statement counts, durations,
                               iteration counts per TriplesMap.
                             Start the monitoring stack with: docker compose -f
                               docker/docker-compose.yml up -d
```

## Monitoring

CARML includes a Prometheus + Grafana monitoring stack for real-time execution monitoring.

### Setup

```console
# Start monitoring stack (from project root)
cd docker
docker compose up -d
```

Open the Grafana dashboard at http://localhost:3000/d/carml-mapping (login: admin / carml, or anonymous access).

### Run with metrics

```console
java -jar carml-jar-X.jar map -m mapping.ttl -rsl input -F nt -o output.nt --metrics
```

The `--metrics` flag:
- Starts a Prometheus scrape endpoint on `localhost:9092` for real-time time-series monitoring (throughput, iterations/sec)
- Pushes final metrics to the Pushgateway at `localhost:9091` on completion (totals, durations, distributions)

To push to a custom Pushgateway endpoint:

```console
java -jar carml-jar-X.jar map -m mapping.ttl -rsl input -F nt --metrics myhost:9091
```

### Published metrics

| Metric | Type | Tags |
|---|---|---|
| `carml.statements.generated` | Counter | `triples_map` |
| `carml.statements.total` | Counter | -- |
| `carml.iterations.processed` | Counter | `triples_map`, `evaluator` |
| `carml.iterations.deduplicated` | Counter | `triples_map` |
| `carml.errors` | Counter | `triples_map` |
| `carml.mapping.duration` | Timer | `triples_map` |
| `carml.mapping.completed` | Counter | `triples_map`, `reason` |
| `carml.mapping.statements` | DistributionSummary | `triples_map` |
| `carml.view.evaluation.duration` | Timer | `view`, `evaluator` |
| `carml.view.evaluation.iterations` | DistributionSummary | `view`, `evaluator` |
| `carml.mappings.active` | Gauge | -- |

## Building the project

The project can be built by running:

```console
mvn clean package
```

The runnable jars will be generated in the `/carml-app/*/target` dirs.

## Customizing the mapper

The `RmlMapperConfigurer` interface allows customizing the `RdfRmlMapper.Builder` without modifying any code. Implementations are discovered automatically via Java's `ServiceLoader` mechanism.

This can be used to add functions to the builder for example.

### 1. Implement the interface

Use [Google AutoService](https://github.com/google/auto/tree/main/service) to generate the `ServiceLoader` registration automatically:

```java
package foo.bar;

import com.google.auto.service.AutoService;
import io.carml.engine.rdf.RdfRmlMapper;
import io.carml.jar.runner.RmlMapperConfigurer;

@AutoService(RmlMapperConfigurer.class)
public class FunctionRmlMapperConfigurer implements RmlMapperConfigurer {

  @Override
  public void configureMapper(RdfRmlMapper.Builder builder) {
    builder.addFunctions(new MyCustomFunctions());
  }
}
```

### 2. Add to classpath

Package your implementation as a JAR and add it to the classpath when running CARML:

```console
java -cp "carml-rdf4j.jar:my-custom-functions.jar" io.carml.jar.app.CarmlJarRdf4jApplication map -m mapping.ttl
```

The configurer will be discovered and applied automatically.
