package com.sunline.dict.vectorization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Embedding 服务客户端
 * 调用 embedding-service 的 /embed 接口，将文本转换为向量
 *
 * <p>支持长文本分段向量化：超出模型 token 限制时，自动拆分为多段分别生成向量，
 * 然后取所有段向量的平均值作为最终向量（Mean Pooling）。
 */
@Component
public class EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingClient.class);

    @Value("${vectorization.embedding-url:http://21.73.16.151:8765}")
    private String embeddingUrl;

    private final RestTemplate restTemplate;

    /**
     * 每段最大字符数（BGE-base-zh 约 512 token，中文约 1 token/字，留余量设 450）
     */
    private static final int CHUNK_SIZE = 450;

    private static final int HEADER_RESERVE = 80;

    public EmbeddingClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(60_000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 将文本转换为向量。
     * 短文本直接生成；长文本自动分段后取平均值（Mean Pooling）。
     */
    public List<Float> embed(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new RuntimeException("向量化文本不能为空");
        }

        if (text.length() <= CHUNK_SIZE) {
            return embedSingle(text);
        }

        List<String> chunks = splitIntoChunks(text);
        log.debug("长文本分段向量化：原始长度={}，拆分为 {} 段", text.length(), chunks.size());

        List<List<Float>> allVectors = new ArrayList<>();
        for (String chunk : chunks) {
            allVectors.add(embedSingle(chunk));
        }

        return meanPooling(allVectors);
    }

    private List<String> splitIntoChunks(String text) {
        List<String> chunks = new ArrayList<>();

        String header;
        int firstNewline = text.indexOf('\n');
        if (firstNewline > 0 && firstNewline <= HEADER_RESERVE) {
            header = text.substring(0, firstNewline).trim();
        } else {
            header = text.substring(0, Math.min(HEADER_RESERVE, text.length())).trim();
        }

        String remaining = text.substring(header.length()).trim();
        int chunkContentSize = CHUNK_SIZE - header.length() - 1;
        if (chunkContentSize < 100) chunkContentSize = 100;

        int start = 0;
        while (start < remaining.length()) {
            int end = Math.min(start + chunkContentSize, remaining.length());
            String chunkContent = remaining.substring(start, end).trim();
            if (!chunkContent.isEmpty()) {
                chunks.add(header + " " + chunkContent);
            }
            start = end;
        }

        if (chunks.isEmpty()) {
            chunks.add(text.substring(0, Math.min(CHUNK_SIZE, text.length())));
        }

        return chunks;
    }

    private List<Float> meanPooling(List<List<Float>> vectors) {
        if (vectors.size() == 1) return vectors.get(0);

        int dim = vectors.get(0).size();
        float[] sum = new float[dim];

        for (List<Float> vec : vectors) {
            for (int i = 0; i < dim; i++) {
                sum[i] += vec.get(i);
            }
        }

        List<Float> result = new ArrayList<>(dim);
        float count = vectors.size();
        for (int i = 0; i < dim; i++) {
            result.add(sum[i] / count);
        }

        return result;
    }

    private List<Float> embedSingle(String text) {
        try {
            Map<String, Object> request = Map.of("texts", List.of(text));
            Map<?, ?> response = restTemplate.postForObject(
                    embeddingUrl + "/embed", request, Map.class);

            if (response == null) {
                throw new RuntimeException("embedding-service 返回 null");
            }

            List<?> embeddings;
            if (response.containsKey("embeddings")) {
                embeddings = (List<?>) response.get("embeddings");
            } else if (response.containsKey("vectors")) {
                embeddings = (List<?>) response.get("vectors");
            } else {
                throw new RuntimeException("embedding-service 返回数据异常，缺少 embeddings/vectors 字段：" + response.keySet());
            }

            List<?> vector = (List<?>) embeddings.get(0);
            return vector.stream()
                    .map(v -> ((Number) v).floatValue())
                    .toList();

        } catch (Exception e) {
            log.error("调用 embedding-service 失败，text 前50字符：{}，错误：{}",
                    text.length() > 50 ? text.substring(0, 50) : text, e.getMessage());
            throw new RuntimeException("向量化失败：" + e.getMessage(), e);
        }
    }
}
