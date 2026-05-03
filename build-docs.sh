#!/bin/bash
# The Aussom file directory to build the docs from.
AUSSOM_DIR=src/main/resources/com/aussom/stdlib/aus
OUT_DIR=docs

# Remove everything from the out dir.
rm $OUT_DIR/*

# Iterate all the files and build the docs for them.
for f in $(find $AUSSOM_DIR -name '*.aus');
do
  aussom -d -o $OUT_DIR $f;
done

# Remove inctest.aus.md because we don't want it here.
rm $OUT_DIR/inctest.aus.md
