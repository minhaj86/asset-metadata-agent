package com.example.dam.taxonomy.application;

import com.example.dam.taxonomy.domain.TaxonomyNode;
import com.example.dam.taxonomy.domain.TaxonomyRepository;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Deterministic taxonomy lookup and keyword search. The taxonomy is small and changes rarely, so it is cached
 * in memory. AI agents use this (via tools) to retrieve valid nodes instead of inventing paths.
 */
@Service
public class TaxonomyService {

    private static final Set<String> STOP_WORDS = Set.of("a", "an", "and", "the", "of", "for", "with", "in", "on", "s");
    private static final double OWN_TERM_SCORE = 3.0;
    private static final double ANCESTOR_TERM_SCORE = 1.0;
    private static final double LEAF_BONUS = 0.5;

    private final TaxonomyRepository repository;
    private volatile Index index;

    public TaxonomyService(TaxonomyRepository repository) {
        this.repository = repository;
    }

    public List<TaxonomyNode> search(String query, int limit) {
        Set<String> queryTerms = terms(query);
        if (queryTerms.isEmpty()) {
            return List.of();
        }
        Index current = index();
        return current.nodes().stream()
                .map(node -> new Scored(node, score(node, queryTerms, current)))
                .filter(scored -> scored.score() > 0)
                .sorted(Comparator.comparingDouble(Scored::score).reversed()
                        .thenComparing(scored -> -scored.node().depth())
                        .thenComparing(scored -> scored.node().path()))
                .limit(limit)
                .map(Scored::node)
                .toList();
    }

    public Optional<TaxonomyNode> findById(String id) {
        return Optional.ofNullable(index().byId().get(id));
    }

    public Optional<TaxonomyNode> findByPath(String path) {
        return path == null ? Optional.empty() : Optional.ofNullable(index().byPath().get(path.strip()));
    }

    public boolean pathExists(String path) {
        return findByPath(path).isPresent();
    }

    public List<TaxonomyNode> all() {
        return index().nodes();
    }

    public void refresh() {
        index = null;
    }

    private double score(TaxonomyNode node, Set<String> queryTerms, Index current) {
        Set<String> own = new HashSet<>(terms(node.name()));
        node.synonyms().forEach(synonym -> own.addAll(terms(synonym)));
        Set<String> ancestors = new HashSet<>();
        String parentId = node.parentId();
        while (parentId != null) {
            TaxonomyNode parent = current.byId().get(parentId);
            if (parent == null) {
                break;
            }
            ancestors.addAll(terms(parent.name()));
            parent.synonyms().forEach(synonym -> ancestors.addAll(terms(synonym)));
            parentId = parent.parentId();
        }
        double score = 0;
        for (String term : queryTerms) {
            if (own.contains(term)) {
                score += OWN_TERM_SCORE;
            } else if (ancestors.contains(term)) {
                score += ANCESTOR_TERM_SCORE;
            }
        }
        return score > 0 && node.leaf() ? score + LEAF_BONUS : score;
    }

    /** Lower-cased, singularised terms without stop words ("Men's Shirts" -> {men, shirt}). */
    static Set<String> terms(String text) {
        if (text == null) {
            return Set.of();
        }
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9-]+"))
                .map(token -> token.replaceAll("^-+|-+$", ""))
                .filter(token -> token.length() > 1 && !STOP_WORDS.contains(token))
                .map(TaxonomyService::singular)
                .collect(Collectors.toSet());
    }

    static String singular(String token) {
        if (token.length() <= 3) {
            return token;
        }
        if (token.endsWith("ies")) {
            return token.substring(0, token.length() - 3) + "y";
        }
        if (token.endsWith("sses") || token.endsWith("shes") || token.endsWith("ches") || token.endsWith("xes")) {
            return token.substring(0, token.length() - 2);
        }
        if (token.endsWith("s") && !token.endsWith("ss")) {
            return token.substring(0, token.length() - 1);
        }
        return token;
    }

    private Index index() {
        Index current = index;
        if (current == null) {
            List<TaxonomyNode> nodes = repository.findAll();
            current = new Index(nodes,
                    nodes.stream().collect(Collectors.toUnmodifiableMap(TaxonomyNode::id, Function.identity())),
                    nodes.stream().collect(Collectors.toUnmodifiableMap(TaxonomyNode::path, Function.identity())));
            index = current;
        }
        return current;
    }

    private record Index(List<TaxonomyNode> nodes, Map<String, TaxonomyNode> byId, Map<String, TaxonomyNode> byPath) {
    }

    private record Scored(TaxonomyNode node, double score) {
    }
}
