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
Usage:  map [-hPVv] [-F=<outputRdfFormat>] [-o=<outputPath>]
            [-M=<prefixMappings>]... [-p=<prefixDeclarations>[,
            <prefixDeclarations>...]]... (-m=<mappingFiles>
            [-m=<mappingFiles>]... [-f=<mappingFileRdfFormat>]
            [-r=<relativeSourceLocation>])
  -h, --help      Show this help message and exit.
  -V, --version   Print version information and exit.
  -m, --mapping=<mappingFiles>
                  Mapping file path(s) and/or mapping file directory path(s).
  -f, --format=<mappingFileRdfFormat>
                  Mapping file RDF format:
                  ttl (text/turtle),
                  ttls (application/x-turtlestar)
                  nt (application/n-triples),
                  nq (application/n-quads),
                  rdf (application/rdf+xml),
                  jsonld (application/ld+json),
                  ndjsonld (application/x-ld+ndjson)
                  trig (application/trig),
                  trigs (application/x-trigstar)
                  n3 (text/n3),
                  trix (application/trix),
                  brf (application/x-binary-rdf),
                  rj (application/rdf+json).
  -r, -rsl, --rel-src-loc=<relativeSourceLocation>
                  Path from which to relatively find the sources specified in
                    the mapping files.
  -F, -of, --outformat=<outputRdfFormat>
                  Output RDF format. Default: nq.
                  Supported values are nt, rdf, ttl, n3, rj, trig, trigs, nq,
                    brf, ndjsonld, xml, ttls, jsonld
  -o, --output=<outputPath>
                  Output file path.
                  If path is directory, will default to fileName `output`.
                  If left empty will output to console.
  -M, -pm, --prefix-mapping=<prefixMappings>
                  File or directory path(s) containing prefix mappings.
                  Files must be JSON or YAML files containing a map of prefix
                    declarations.
                  File names must have either .json or .yaml/.yml file
                    extensions.
  -p, --prefixes=<prefixDeclarations>[,<prefixDeclarations>...]
                  Declares which prefixes to apply to the output.
                  Can be a prefix reference or an inline prefix declaration.
                  A prefix reference will be resolved against the provided
                    prefix mapping (`-pm`), or the default prefix mapping.
                  An inline prefix declaration can be provided as 'prefix=iri.
                    For example: ex=http://example.com/'
                  Multiple declarations can be separated by ','. For example:
                    ex=http://example.com/,foo,bar
  -P, --pretty    Serialize pretty printed output. (Caution: will cause
                    in-memory output collection).
  -v, --verbose   Specify multiple -v or --verbose options to increase
                    verbosity.
                  For example `-v -v`, or `-vv` or `--verbose --verbose`
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

##### Namespace Prefixes

To generate output with prefixed IRIs namespace prefixes can be specified via `-p`.
The prefix definition can be provided inline by using the format `prefix=iri`. For example:

```console
-p ex=http://example.org/
```

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

## Building the project

The project can be built by running:

```console
mvn clean package
```

The runnable jar will be generated in the `/carml-app/target` dir.
