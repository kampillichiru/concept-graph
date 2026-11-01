package com.conceptgraph.retrieval;

/**
 * Vector maths written by hand, so similarity is not a black box.
 * (LangChain4j has CosineSimilarity.between(...); we compare against it in POC 2.)
 */
public final class VectorMath {

    private VectorMath() {
    }

    /**
     * Cosine similarity: do two vectors point in the same direction?
     *  1.0 = same direction (same meaning), 0 = unrelated, -1 = opposite.
     * Formula: dot(a, b) / (|a| * |b|)
     */
    public static double cosine(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vectors must have the same dimension: " + a.length + " vs " + b.length);
        }
        double dot = 0;     // sum of a[i]*b[i]: large when both are big in the same positions
        double normA = 0;   // squared length of a
        double normB = 0;   // squared length of b
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        // Dividing by the lengths removes "how long" and keeps only "which direction"
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /** Length of a vector (its L2 norm). Many embedding models return length ~1.0. */
    public static double norm(float[] v) {
        double sum = 0;
        for (float x : v) {
            sum += x * x;
        }
        return Math.sqrt(sum);
    }

    public static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}
