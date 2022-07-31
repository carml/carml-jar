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
Usage: carml map [-hPVv] [-F=<outputRdfFormat>] [-o=<outputPath>]
                 (-m=<mappingFiles> [-m=<mappingFiles>]...
                 [-f=<mappingFileRdfFormat>] [-r=<relativeSourceLocation>])
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
  -F, -of, --outformat=<outputRdfFormat>
                  Output RDF format (see -f). Default: nq.
  -o, --output=<outputPath>
                  Output file path.
                  If path is directory, will default to fileName `output`.
                  If left empty will output to console.
  -v, --verbose   Specify multiple -v or --verbose options to increase
                    verbosity.
                  For example `-v -v`, or `-vv` or `--verbose --verbose`
  -P, --pretty    Serialize pretty printed output. (Caution: will cause
                    in-memory output collection).
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
java -jar carml-jar-X.jar map -m rml -rsl input -of ttl -P
```

#### Output
If an output path is provided (via `-o`) the RDF result is output to the specified path.

By default, if no output path is provided the plain RDF result will be output to `stdout`.

To get more information on what is going on during execution, the verbosity level can be increased via `-v`.
You can get more verbosity by adding more `v`s, for example `-vvv`, which is the highest level of verbosity and will
give `TRACE` level logging.

Errors logs are output to `stderr`.

## Building the project

The project can be built by running:

```console
mvn clean package
```

The runnable jar will be generated in the `/carml-app/target` dir.
