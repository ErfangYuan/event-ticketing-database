package mytix.nlp;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;

public final class OpenNlpNounPhraseExtractor implements NounPhraseExtractor {

    private static final String[] MODEL_FILES = {
        "en-sent.bin", "en-token.bin", "en-pos-maxent.bin", "en-chunker.bin"
    };

    private final SentenceDetectorME sentenceDetector;
    private final TokenizerME tokenizer;
    private final POSTaggerME posTagger;
    private final ChunkerME chunker;

    public OpenNlpNounPhraseExtractor() {
        File dir = resolveModelsDir();
        try {
            this.sentenceDetector = new SentenceDetectorME(
                    new SentenceModel(open(dir, "en-sent.bin")));
            this.tokenizer = new TokenizerME(
                    new TokenizerModel(open(dir, "en-token.bin")));
            this.posTagger = new POSTaggerME(
                    new POSModel(open(dir, "en-pos-maxent.bin")));
            this.chunker = new ChunkerME(
                    new ChunkerModel(open(dir, "en-chunker.bin")));
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to load OpenNLP models from " + dir.getAbsolutePath()
                            + " — see mytix.nlp.OpenNlpNounPhraseExtractor javadoc "
                            + "for expected file names / download source.",
                    e);
        }
    }

    @Override
    public List<String> extractNounPhrases(String text) {
        List<String> phrases = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return phrases;
        }
        String[] sentences;
        String[] tokens;
        String[] tags;
        String[] chunks;
        synchronized (this) {
            sentences = sentenceDetector.sentDetect(text);
            for (String sentence : sentences) {
                tokens = tokenizer.tokenize(sentence);
                if (tokens.length == 0) {
                    continue;
                }
                tags = posTagger.tag(tokens);
                chunks = chunker.chunk(tokens, tags);
                phrases.addAll(collectNounPhrases(tokens, chunks));
            }
        }
        return phrases;
    }

    
    private static List<String> collectNounPhrases(String[] tokens, String[] chunkTags) {
        List<String> phrases = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inNp = false;
        for (int i = 0; i < tokens.length; i++) {
            String tag = chunkTags[i];
            boolean startsNp = tag.equals("B-NP");
            boolean continuesNp = tag.equals("I-NP");
            if (startsNp) {
                if (inNp) {
                    phrases.add(current.toString());
                }
                current = new StringBuilder(tokens[i]);
                inNp = true;
            } else if (continuesNp && inNp) {
                current.append(' ').append(tokens[i]);
            } else {
                if (inNp) {
                    phrases.add(current.toString());
                }
                inNp = false;
            }
        }
        if (inNp) {
            phrases.add(current.toString());
        }
        return phrases;
    }

    private static InputStream open(File dir, String fileName) throws IOException {
        return new FileInputStream(new File(dir, fileName));
    }

    private static File resolveModelsDir() {
        String prop = System.getProperty("mytix.nlp.modelsDir");
        if (prop != null && !prop.isBlank() && hasAllModels(new File(prop))) {
            return new File(prop);
        }
        String env = System.getenv("MYTIX_NLP_MODELS_DIR");
        if (env != null && !env.isBlank() && hasAllModels(new File(env))) {
            return new File(env);
        }
        File relative = new File("src/lib/opennlp");
        if (hasAllModels(relative)) {
            return relative;
        }
        
        
        return relative;
    }

    private static boolean hasAllModels(File dir) {
        if (!dir.isDirectory()) {
            return false;
        }
        for (String f : MODEL_FILES) {
            if (!new File(dir, f).isFile()) {
                return false;
            }
        }
        return true;
    }
}
