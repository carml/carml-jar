# KROWN Benchmark Integration

Run [KROWN](https://github.com/kg-construct/KROWN) benchmarks against CARML.

## Prerequisites

- Docker
- Python 3.8+ (for KROWN's execution framework)
- CARML built: `cd carml-jar && mvn clean install -Pquick`

## Build Docker Image

```bash
cd carml-jar
docker build \
  --build-arg JAR_FILE=carml-app/carml-app-rdf4j/target/carml-jar-rdf4j-*.jar \
  -t carml:latest .
```

## KROWN Integration

CARML integrates with KROWN as a materialization resource. The Docker container follows
KROWN's pattern: stays alive via `tail -f /dev/null`, and KROWN invokes mapping commands
via `docker exec`.

### Mapping command

```bash
java -jar /app/app.jar map -m /data/shared/mapping.ttl -r /data/shared -F nt -o /data/shared/result.nt
```

The `-r /data/shared` flag sets the relative source location so that data file references
in the mapping (e.g., `rml:path "data.csv"`) resolve against the shared data directory.

### Evaluator variants

Run benchmarks with different evaluators by passing `-E`:

| Variant | Flag | Description |
|---------|------|-------------|
| Auto | `-E auto` | Best evaluator per view (default) |
| In-Process DB | `-E in-process-db` | Force in-process database evaluator |
| Reactive | `-E reactive` | Force reactive evaluator |

### Running KROWN scenarios

1. Clone KROWN: `git clone https://github.com/kg-construct/KROWN.git`
2. Copy CARML Docker config: `cp -r krown/dockers/CARML KROWN/execution-framework/dockers/`
3. Build the CARML Docker image (see above)
4. Generate test data: follow KROWN's data generator instructions
5. Run: `python3 KROWN/execution-framework/exectool --root /path/to/scenarios`

## Quick local benchmark

Without KROWN, you can benchmark directly:

```bash
# Generate test data (1M rows CSV)
python3 -c "
import csv, random
random.seed(42)
with open('/tmp/bench.csv', 'w', newline='') as f:
    w = csv.writer(f)
    w.writerow(['id','name','category','value'])
    for i in range(1000000):
        w.writerow([i, f'item_{i}', random.choice(['A','B','C']), random.randint(1,10000)])
"

# Create a mapping file
cat > /tmp/mapping.ttl << 'EOF'
@prefix rml: <http://w3id.org/rml/> .
@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
@prefix ex: <http://example.org/> .

<#CsvSource> a rml:LogicalSource ;
  rml:source [ a rml:RelativePathSource ;
    rml:root rml:MappingDirectory ;
    rml:path "bench.csv" ] ;
  rml:referenceFormulation rml:CSV .

<#ItemMap> a rml:TriplesMap ;
  rml:logicalSource <#CsvSource> ;
  rml:subjectMap [ rml:template "http://example.org/item/{id}/{name}" ] ;
  rml:predicateObjectMap [
    rml:predicate ex:category ;
    rml:objectMap [ rml:reference "category" ] ] ;
  rml:predicateObjectMap [
    rml:predicate ex:value ;
    rml:objectMap [ rml:reference "value" ; rml:datatype xsd:integer ] ] ;
  rml:predicateObjectMap [
    rml:predicate rdf:type ;
    rml:objectMap [ rml:constant ex:Item ] ] .
EOF

# Run with in-process-db evaluator
time docker run --rm -v /tmp:/data carml \
  java -jar /app/app.jar map -m /data/mapping.ttl -r /data -F nt -E in-process-db > /dev/null

# Run with reactive evaluator
time docker run --rm -v /tmp:/data carml \
  java -jar /app/app.jar map -m /data/mapping.ttl -r /data -F nt -E reactive > /dev/null
```
