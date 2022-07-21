# carml-jar
**A CLI for CARML**

[![Build](https://github.com/carml/carml-jar/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/carml/carml-jar/actions/workflows/build.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=carml_carml-jar&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=carml_carml-jar)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=carml_carml-jar&metric=coverage)](https://sonarcloud.io/summary/new_code?id=carml_carml-jar)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=carml_carml-jar&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=carml_carml-jar)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=carml_carml-jar&metric=code_smells)](https://sonarcloud.io/summary/new_code?id=carml_carml-jar)

> Project status: WIP

## Usage

### Map RDF file to output

```console
Usage: carml map [-hV] (-m=<mappingFiles> [-m=<mappingFiles>]...
                 [-f=<mappingFileRdfFormat>] [-r=<relativeSourceLocation>])
                 [[-o=<outputPath>] [-t=<outputRdfFormat>] [-P]]
  -h, --help      Show this help message and exit.
  -V, --version   Print version information and exit.
  -m, --mapping=<mappingFiles>
                  Mapping file path(s) and/or mapping file directory path(s).
  -f, --format=<mappingFileRdfFormat>
                  Mapping file RDF format:
                  ttl (text/turtle),
                  nt (application/n-triples),
                  nq (application/n-quads),
                  rdf (application/rdf+xml),
                  jsonld (application/ld+json),
                  trig (application/trig),
                  n3 (text/n3),
                  trix (application/trix),
                  brf (application/x-binary-rdf),
                  rj (application/rdf+json).
  -r, -rsl, --rel-src-loc=<relativeSourceLocation>
                  Path from which to relatively find the sources specified in
                    the mapping files.
  -o, --output=<outputPath>
                  Output file path.
                  If path is directory, will default to fileName `output`.
                  If left empty will output to console.
  -t, -of, --outformat=<outputRdfFormat>
                  Output RDF format (see -f).
  -P, --pretty    Serialize output in pretty fashion. (Caution: will cause
                    in-memory processing)
```

For example:

The following command:
* maps (`map`) all mapping files
* under the 'rml' directory (`-m rml`)
* using the relative source location 'input' (`-rsl input`)
* with output format Turtle (`-of ttl`)
* pretty printed (`-P`)
* to stdout.

```console
java -jar carml-jar-X.jar  map -m rml -rsl input -of ttl -P
```

## Building the project

The project can be built by running:

```console
mvn clean package
```

The runnable jar will be generated in the `/carml-app/target` dir.
