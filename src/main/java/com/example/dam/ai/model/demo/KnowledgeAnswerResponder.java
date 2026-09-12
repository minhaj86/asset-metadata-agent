package com.example.dam.ai.model.demo;

import com.example.dam.ai.agent.KnowledgeAnswerOutput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Demo rules for the knowledge assistant: answers with the sentences of the retrieved knowledge (injected by the
 * LangChain4j retrieval augmentor) that best overlap with the question; "UNKNOWN" when nothing was retrieved.
 */
final class KnowledgeAnswerResponder implements DemoResponder {

    private static final Set<String> STOP_WORDS = Set.of("what", "which", "when", "where", "does", "should", "must",
            "the", "for", "are", "and", "with", "how", "mean", "means", "fields", "field", "metadata", "be", "is");
    private static final int MAX_SENTENCES = 3;

    private final DemoSupport support;

    KnowledgeAnswerResponder(DemoSupport support) {
        this.support = support;
    }

    @Override
    public String schemaName() {
        return KnowledgeAnswerOutput.class.getSimpleName();
    }

    @Override
    public DemoReply reply(DemoConversation conversation) {
        String question = conversation.section("question").orElse("");
        String knowledge = conversation.section("knowledge").orElse("");
        if (knowledge.isBlank()) {
            return DemoReply.answer(support.write(new KnowledgeAnswerOutput("UNKNOWN", false, List.of())));
        }
        Set<String> terms = Arrays.stream(question.toLowerCase(Locale.ROOT).split("[^a-z0-9_]+"))
                .filter(term -> term.length() > 2 && !STOP_WORDS.contains(term))
                .collect(Collectors.toSet());
        List<Entry> entries = Arrays.stream(knowledge.split("\n\n(?=content: )")).map(Entry::parse).toList();
        Entry best = entries.stream().max(Comparator.comparingLong(entry -> entry.overlap(terms))).orElseThrow();

        List<String> sentences = Arrays.stream(best.content().split("\n", 2))
                .skip(1)
                .flatMap(body -> Arrays.stream(body.replace('\n', ' ').split("(?<=[.!?])\\s+")))
                .map(String::strip)
                .filter(sentence -> !sentence.isEmpty())
                .toList();
        List<String> relevant = sentences.stream().filter(sentence -> overlap(sentence, terms) > 0).limit(MAX_SENTENCES)
                .toList();
        String answer = String.join(" ", relevant.isEmpty() ? sentences.stream().limit(MAX_SENTENCES).toList() : relevant);
        List<String> cited = new ArrayList<>();
        cited.add(best.title() + " - " + best.section());
        return DemoReply.answer(support.write(new KnowledgeAnswerOutput(answer, true, cited)));
    }

    private static long overlap(String text, Set<String> terms) {
        String lower = text.toLowerCase(Locale.ROOT);
        return terms.stream().filter(lower::contains).count();
    }

    private record Entry(String content, String title, String section) {
        static Entry parse(String raw) {
            String content = raw;
            String title = "";
            String section = "";
            int titleIndex = raw.lastIndexOf("\ntitle: ");
            if (titleIndex >= 0) {
                content = raw.substring(0, titleIndex);
                String rest = raw.substring(titleIndex + 1);
                for (String line : rest.split("\n")) {
                    if (line.startsWith("title: ")) {
                        title = line.substring(7).strip();
                    } else if (line.startsWith("section: ")) {
                        section = line.substring(9).strip();
                    }
                }
            }
            return new Entry(content.replaceFirst("^content: ", ""), title, section);
        }

        long overlap(Set<String> terms) {
            return KnowledgeAnswerResponder.overlap(content + " " + section, terms);
        }
    }
}
