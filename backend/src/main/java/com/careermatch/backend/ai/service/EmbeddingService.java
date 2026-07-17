package com.careermatch.backend.ai.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.output.Response;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingService {

    private EmbeddingModel embeddingModel;

    @PostConstruct
    public void init() {
        // Initialize the in-process quantized ONNX all-MiniLM-L6-v2 model (smaller heap footprint)
        this.embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
    }

    public float[] generateEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return new float[384];
        }
        // Truncate to reasonable length matching token context window limits
        String truncated = text.length() > 3000 ? text.substring(0, 3000) : text;
        Response<Embedding> response = embeddingModel.embed(truncated);
        return response.content().vector();
    }
}
