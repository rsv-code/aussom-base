#!/bin/bash
# Warm-startup benchmark for an Aussom Engine.
#
# Not part of "mvn test": timing is noisy and machine dependent, so it
# must not decide whether a build passes. Run it by hand when changing
# anything on the parse or engine construction path.
#
#   ./run-startup-bench.sh                          print the table
#   ./run-startup-bench.sh --baseline FILE          record a new baseline
#   ./run-startup-bench.sh --check FILE [TOLERANCE] compare, non-zero on regression
#
# Add --no-doc-retain to any of the above to build the engines with
# aussomdoc.retain=false, which is how the saving from that setting is
# measured. It changes nothing else.
#
# Baselines live in design/perf-output/. The heap is pinned so runs are
# comparable across days; absolute numbers are not comparable across
# machines, which is why the recorded header carries the JDK and CPU
# count.
set -e

CP_FILE=target/startup-bench-cp.txt

mvn -q test-compile
if [ ! -f "$CP_FILE" ]; then
  mvn -q dependency:build-classpath -Dmdep.outputFile="$CP_FILE"
fi

java -Xms1g -Xmx1g \
  -cp "target/classes:target/test-classes:$(cat $CP_FILE)" \
  com.aussom.StartupBench "$@"
