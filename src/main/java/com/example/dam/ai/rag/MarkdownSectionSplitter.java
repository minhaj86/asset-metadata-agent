package com.example.dam.ai.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits markdown governance documents along their {@code ##}/{@code ###} sections so that each chunk is one
 * coherent rule or definition. Long sections are split further with LangChain4j's recursive splitter. Each chunk
 * is prefixed with "document - section" which noticeably improves retrieval of short rules.
 */
final class MarkdownSectionSplitter implements DocumentSplitter {

    static final String TITLE = "title";
    static final String SECTION = "section";
    private static final int MAX_SECTION_CHARS = 1200;

    private final DocumentSplitter fallback = DocumentSplitters.recursive(800, 100);

    @Override
    public List<TextSegment> split(Document document) {
        String title = document.metadata().getString(TITLE);
        List<TextSegment> segments = new ArrayList<>();
        String section = "Overview";
        StringBuilder body = new StringBuilder();
        for (String line : document.text().split("\n")) {
            if (line.startsWith("# ")) {
                continue;
            }
            if (line.startsWith("## ") || line.startsWith("### ")) {
                flush(document.metadata(), title, section, body.toString(), segments);
                section = line.replaceFirst("^#+\\s*", "").strip();
                body.setLength(0);
            } else {
                body.append(line).append('\n');
            }
        }
        flush(document.metadata(), title, section, body.toString(), segments);
        return segments;
    }

    private void flush(Metadata documentMetadata, String title, String section, String text, List<TextSegment> out) {
        String content = text.strip();
        if (content.isEmpty()) {
            return;
        }
        List<String> chunks = content.length() <= MAX_SECTION_CHARS
                ? List.of(content)
                : fallback.split(Document.from(content)).stream().map(TextSegment::text).toList();
        for (String chunk : chunks) {
            Metadata metadata = documentMetadata.copy()
                    .put(SECTION, section)
                    .put("index", out.size());
            out.add(TextSegment.from(title + " - " + section + "\n" + chunk, metadata));
        }
    }
}
