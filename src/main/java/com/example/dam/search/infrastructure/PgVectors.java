package com.example.dam.search.infrastructure;

import java.util.Arrays;
import java.util.Locale;

/** Text representation of pgvector values ({@code [0.1,0.2,...]}). */
final class PgVectors {

    private PgVectors() {
    }

    static String toLiteral(float[] vector) {
        StringBuilder literal = new StringBuilder(vector.length * 10).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                literal.append(',');
            }
            literal.append(String.format(Locale.ROOT, "%.7f", vector[i]));
        }
        return literal.append(']').toString();
    }

    static float[] fromLiteral(String literal) {
        String body = literal.strip();
        body = body.substring(1, body.length() - 1);
        if (body.isBlank()) {
            return new float[0];
        }
        String[] parts = body.split(",");
        float[] vector = new float[parts.length];
        Arrays.setAll(parts, i -> parts[i].strip());
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i]);
        }
        return vector;
    }
}
