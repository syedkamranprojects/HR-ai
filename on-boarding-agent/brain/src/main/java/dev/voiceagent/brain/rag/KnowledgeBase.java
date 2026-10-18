package dev.voiceagent.brain.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.QueryFactory;
import io.qdrant.client.WithPayloadSelectorFactory;
import io.qdrant.client.WithVectorsSelectorFactory;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.grpc.Points.QueryPoints;
import io.qdrant.client.grpc.Points.ScoredPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

/**
 * RAG over the company policy documents in brain/knowledge-base. Backed by
 * Qdrant for storage/search and a local Ollama embedding model
 * (nomic-embed-text -- small enough to run fast on this laptop, unlike the
 * chat model; see Milestone 3 notes on why chat generation moved to Groq).
 *
 * Search queries Qdrant directly via the raw client rather than
 * QdrantEmbeddingStore#search, which (as of the 1.11.0-beta19 integration)
 * recomputes cosine similarity client-side from vectors the search response
 * doesn't actually return, throwing on a 0-length vector. Qdrant already
 * scores and ranks results server-side, so this also avoids redundant work.
 */
public final class KnowledgeBase {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBase.class);
    private static final String COLLECTION_NAME = "company-policies";
    private static final String PAYLOAD_TEXT_KEY = "text";
    private static final int EMBEDDING_DIMENSIONS = 768; // nomic-embed-text

    private final EmbeddingModel embeddingModel;
    private final QdrantEmbeddingStore embeddingStore;
    private final QdrantClient client;

    public KnowledgeBase(EmbeddingModel embeddingModel, String qdrantHost, int qdrantPort) {
        this.embeddingModel = embeddingModel;

        ManagedChannel channel = ManagedChannelBuilder.forAddress(qdrantHost, qdrantPort).usePlaintext().build();
        this.client = new QdrantClient(QdrantGrpcClient.newBuilder(channel).build());

        try {
            if (Boolean.TRUE.equals(client.collectionExistsAsync(COLLECTION_NAME).get())) {
                client.deleteCollectionAsync(COLLECTION_NAME).get();
            }
            client.createCollectionAsync(
                    COLLECTION_NAME,
                    VectorParams.newBuilder()
                            .setSize(EMBEDDING_DIMENSIONS)
                            .setDistance(Distance.Cosine)
                            .build()
            ).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("failed to set up Qdrant collection", e);
        }

        this.embeddingStore = QdrantEmbeddingStore.builder()
                .host(qdrantHost)
                .port(qdrantPort)
                .collectionName(COLLECTION_NAME)
                .payloadTextKey(PAYLOAD_TEXT_KEY)
                .useTls(false)
                .build();
    }

    /** Re-ingests every .txt file in the given directory, replacing whatever was there before. */
    public void ingest(Path knowledgeBaseDir) {
        DocumentSplitter splitter = DocumentSplitters.recursive(500, 50);
        try (Stream<Path> files = Files.list(knowledgeBaseDir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".txt")).toList()) {
                Document document = Document.from(Files.readString(file));
                List<TextSegment> segments = splitter.split(document);
                List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
                embeddingStore.addAll(embeddings, segments);
                log.info("ingested {} ({} chunks)", file.getFileName(), segments.size());
            }
        } catch (IOException e) {
            throw new RuntimeException("failed to ingest knowledge base from " + knowledgeBaseDir, e);
        }
    }

    public List<String> search(String query, int maxResults) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        QueryPoints request = QueryPoints.newBuilder()
                .setCollectionName(COLLECTION_NAME)
                .setQuery(QueryFactory.nearest(queryEmbedding.vectorAsList()))
                .setLimit(maxResults)
                .setScoreThreshold(0.4f)
                .setWithPayload(WithPayloadSelectorFactory.enable(true))
                .setWithVectors(WithVectorsSelectorFactory.enable(false))
                .build();

        try {
            List<ScoredPoint> points = client.queryAsync(request).get();
            return points.stream()
                    .map(point -> point.getPayloadOrDefault(PAYLOAD_TEXT_KEY, Value.getDefaultInstance()).getStringValue())
                    .filter(text -> !text.isBlank())
                    .toList();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Qdrant search failed", e);
        }
    }
}
