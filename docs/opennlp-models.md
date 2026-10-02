# English noun phrase models

Review creation and noun-phrase projection loading use Apache OpenNLP 1.9.4 with four English models. From the repository root, run this command using JDK 17+ on Windows, Linux, or macOS:

```text
java scripts/InstallNlpModels.java
```

It downloads approximately 8.8 MB to the ignored `src/lib/opennlp/` directory and verifies each file against `scripts/opennlp-models.properties`. Repeating the command verifies existing files and skips their download. Missing or corrupted files are replaced only after a successful download and checksum check. A network or checksum failure leaves existing files intact and exits unsuccessfully. The normal build and TUI launcher do not download models automatically.

To use another directory:

```text
java scripts/InstallNlpModels.java local/opennlp-models
```

Set `MYTIX_NLP_MODELS_DIR` to that directory before starting the application. A Java system property, `-Dmytix.nlp.modelsDir=...`, is also supported when invoking Java directly. Keep the `.bin` files compressed and preserve their names.

For an offline machine, copy the four verified files from a prepared installation into the same directory. Running the helper with all four matching files performs checksum verification without network requests. Maven and Java dependencies must also have been cached by an earlier build.

| File | Purpose |
| --- | --- |
| en-sent.bin | Sentence detection |
| en-token.bin | Tokenization |
| en-pos-maxent.bin | Penn Treebank part-of-speech tags |
| en-chunker.bin | Phrase chunking, including noun phrases |

These models were created in the OpenNLP 1.5.0 format and are compatible with this project's OpenNLP 1.9.4 API. All four must be installed together. The newer Universal Dependencies POS models use a different tag set; renaming them to these legacy filenames does not establish compatibility with the phrase chunker. English comments are supported; the statistical models can misclassify punctuation, unusual language, or short fragments.

`ReviewNounPhraseProjection.replace(connection, reviewID, comment)` runs inside the caller's review transaction. It extracts and normalizes each occurrence (Unicode NFC, lowercase and whitespace), retains duplicates and replaces that review's projection. It does not commit. Missing models or extraction/storage errors must roll back the whole review operation. Dataset loaders use the same helper.

R9 reads `review_noun_phrases`; SQL performs occurrence counts, event grouping and deterministic top-ten ranking. Report reads need no model files and never repair or populate projections. Long phrases are stored without truncation up to the MySQL TEXT byte limit. Statement-scoped sorting hints cover full TEXT values, because MySQL's default comparison prefix can otherwise merge long values that share a prefix; see [MySQL max_sort_length](https://dev.mysql.com/doc/refman/8.0/en/server-system-variables.html#sysvar_max_sort_length).

## Sources and licenses

The [OpenNLP model page](https://opennlp.apache.org/models.html) links to the [legacy model catalog](https://opennlp.sourceforge.net/models-1.5/). The installer uses the [Apache-hosted legacy mirror](https://nightlies.apache.org/opennlp/models-1.5/), also referenced in [Apache OpenNLP's own build configuration](https://github.com/apache/opennlp/blob/main/pom.xml). These are legacy pretrained models, not a new nightly release of this application. Their internal manifests identify English, model format 1.0, and OpenNLP 1.5.0.

The SHA-256 values in this repository pin the downloaded bytes checked on 2026-10-02; they are project checksums, not upstream release signatures. If an upstream file changes, the installer fails instead of silently accepting the changed model. Update a checksum only after verifying its source and rerunning extraction and report checks.

The OpenNLP toolkit is distributed under [Apache License 2.0](https://github.com/apache/opennlp/blob/opennlp-1.9.4/LICENSE). The legacy model catalog identifies training sources, including CoNLL-2000 for the chunker; the four downloaded archives do not contain separate LICENSE or NOTICE entries. This repository does not redistribute those model binaries or claim its own license over them. Preserve upstream attribution and consult the model/training-source terms when redistributing models.
