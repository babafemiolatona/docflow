package com.tech.docflow.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tech.docflow.models.DocumentType;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Service
@RequiredArgsConstructor
@Slf4j
public class FieldExtractionService {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String OLLAMA_API_URL = "http://ollama:11434/api/generate";
    
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
        .connectTimeout(java.time.Duration.ofSeconds(10))
        .writeTimeout(java.time.Duration.ofSeconds(30))
        .readTimeout(java.time.Duration.ofSeconds(240))
        .build();
    
    @PostConstruct
    public void warmupPhiModel() {
        new Thread(() -> {
            try {
                log.info("Starting Phi model pre-warmup...");
                String response = callOllamaAPI("Say 'Model ready'", "phi");
                if (response != null && !response.isEmpty()) {
                    log.info("Phi model pre-warmup successful");
                } else {
                    log.warn("Phi model pre-warmup returned empty response");
                }
            } catch (Exception e) {
                log.warn("Phi model pre-warmup failed (will retry on first extraction): {}", e.getMessage());
            }
        }).start();
    }
    
    @Scheduled(fixedDelay = 300000)
    public void keepPhiModelWarm() {
        try {
            log.debug("Sending keep-alive ping to Phi model...");
            callOllamaAPI("Say 'Still here'", "phi");
            log.debug("Phi model keep-alive ping successful");
        } catch (Exception e) {
            log.debug("Phi model keep-alive ping failed (non-critical): {}", e.getMessage());
        }
    }
    
    public Map<String, String> extractFields(String rawText, DocumentType documentType) {
        Map<String, String> fields = new HashMap<>();
        
        switch (documentType) {
            case INVOICE:
                fields.putAll(extractInvoiceFields(rawText));
                break;
            case RESUME:
                fields.putAll(extractResumeFields(rawText));
                break;
            case CONTRACT:
                fields.putAll(extractContractFields(rawText));
                break;
            default:
                log.warn("No field extraction defined for document type: {}", documentType);
        }
        
        return fields;
    }
    
    private Map<String, String> extractInvoiceFields(String text) {
        Map<String, String> fields = new HashMap<>();
        try {
            fields = extractInvoiceFieldsWithLLM(text);
            if (fields.size() >= 2) {
                log.info("LLM extraction successful, extracted {} fields", fields.size());
                return fields;
            }
        } catch (Exception e) {
            log.warn("LLM extraction failed: {}", e.getMessage());
        }
        
        log.info("LLM extraction insufficient (got {} fields), using regex fallback", fields.size());
        fields = extractInvoiceFieldsRegex(text);
        
        return fields;
    }
    
    private Map<String, String> extractInvoiceFieldsRegex(String text) {
        Map<String, String> fields = new HashMap<>();
        String[] lines = text.split("\n");
        
        String invoiceNum = null;

        Pattern invoicePattern1 = Pattern.compile("(?:Invoice|INV|INVOICE)[\\s#:]*([0-9\\-A-Z]+)", Pattern.CASE_INSENSITIVE);
        Matcher invoiceMatcher1 = invoicePattern1.matcher(text);
        if (invoiceMatcher1.find()) {
            invoiceNum = invoiceMatcher1.group(1).trim();
            if (!invoiceNum.isEmpty() && !invoiceNum.matches("(?i)INVOICE|INV")) {
                fields.put("invoice_number", invoiceNum);
            }
        }
        
        String vendorName = null;
        
        for (String line : lines) {
            if (line.matches("(?i).*(?:Invoice|INV|INVOICE)\\s+\\d+.*")) {
                Pattern vendorPattern = Pattern.compile("(?:Invoice|INV|INVOICE)\\s+\\d+\\s+(.+?)$", Pattern.CASE_INSENSITIVE);
                Matcher vendorMatcher = vendorPattern.matcher(line);
                if (vendorMatcher.find()) {
                    vendorName = vendorMatcher.group(1).trim();
                    if (!vendorName.isEmpty() && vendorName.length() < 100 && !vendorName.matches(".*[0-9]{2}.*")) {
                        fields.put("vendor_name", vendorName);
                        break;
                    }
                }
            }
        }
        
        if (!fields.containsKey("vendor_name")) {
            int invoiceLine = -1;
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].matches("(?i).*(?:Invoice|INV|INVOICE)\\s+\\d+.*")) {
                    invoiceLine = i;
                    break;
                }
            }
            
            if (invoiceLine >= 0) {
                for (int i = invoiceLine + 1; i < Math.min(invoiceLine + 4, lines.length); i++) {
                    String line = lines[i].trim();
                    if (!line.isEmpty()
                        && !line.matches("(?i).*(Date|TO|SHIP|BILL|Same|Address|Street|Strasse|Rd|Blvd|Ave).*")
                        && !line.contains("@")
                        && !line.matches(".*[0-9]{2,}.*[A-Z]{2}.*")
                        && line.length() > 2 && line.length() < 100) {
                        fields.put("vendor_name", line);
                        break;
                    }
                }
            }
        }
        
        Pattern textDatePattern = Pattern.compile("((?:January|February|March|April|May|June|July|August|September|October|November|December)\\s+\\d{1,2},?\\s+\\d{4})", Pattern.CASE_INSENSITIVE);
        Matcher textDateMatcher = textDatePattern.matcher(text);
        if (textDateMatcher.find()) {
            String dateStr = textDateMatcher.group(1);
            fields.put("invoice_date", dateStr);
        }
        
        if (!fields.containsKey("invoice_date")) {
            Pattern numericDatePattern = Pattern.compile("\\b([0-9]{1,2}[./\\-][0-9]{1,2}[./\\-][0-9]{4})\\b");
            Matcher numericDateMatcher = numericDatePattern.matcher(text);
            if (numericDateMatcher.find()) {
                fields.put("invoice_date", numericDateMatcher.group(1));
            }
        }
        
        for (String line : lines) {
            if (line.matches("(?i).*[Tt][Oo][Tt][Aa][Ll].*")) {
                Pattern rightmostNumber = Pattern.compile("(\\d+(?:[,.]\\d{2})?)\\s*$");
                Matcher rightmostMatcher = rightmostNumber.matcher(line);
                if (rightmostMatcher.find()) {
                    fields.put("total_amount", rightmostMatcher.group(1));
                    break;
                }
            }
        }
        
        return fields;
    }
    
    private Map<String, String> extractInvoiceFieldsWithLLM(String text) throws Exception {
        Map<String, String> fields = new HashMap<>();
        
        List<String> chunks = chunkText(text, 1200);
        log.info("Generated {} chunks from OCR text for invoice extraction", chunks.size());
        
        List<String> relevantChunks = findRelevantChunks(chunks);
        log.info("Selected {} relevant chunks out of {} for LLM analysis", relevantChunks.size(), chunks.size());
        
        String textToAnalyze = relevantChunks.isEmpty() ? text : combineChunks(relevantChunks);
        
        String prompt = "Extract these 4 fields from the invoice text and return ONLY a JSON object on a single line (no markdown, no backticks, no extra text):\n" +
            "1. invoice_number: the complete invoice number (e.g., 1213, INV-001, etc.) - extract the FULL number, not partial\n" +
            "2. vendor_name: the company/vendor name that issued the invoice (often at the very top with contact info)\n" +
            "3. invoice_date: the complete invoice date (e.g., 16.12.2021, 2021-12-16, etc.) - extract the FULL date\n" +
            "4. total_amount: the complete total amount due (usually at the end after subtotal and tax, e.g., 2809.30)\n\n" +
            "Return this format only: {\"invoice_number\":\"X\",\"vendor_name\":\"Y\",\"invoice_date\":\"Z\",\"total_amount\":\"W\"}\n" +
            "Use null for missing fields. Single line only.\n\n" +
            "Invoice text:\n" +
            textToAnalyze;
        
        String response = callOllamaAPI(prompt, "phi");
        
        if (response != null && !response.isEmpty()) {
            log.info("Phi response: {}", response.substring(0, Math.min(500, response.length())));
            try {
                String cleanedResponse = response
                    .replaceAll("(?i)```.*?```", "")
                    .replaceAll("```", "")
                    .replaceAll("Sure,.*?:", "")
                    .replaceAll("Here's.*?:", "")
                    .replaceAll("Here is.*?:", "")
                    .trim();
                
                int jsonStart = cleanedResponse.indexOf('{');
                int jsonEnd = cleanedResponse.lastIndexOf('}');
                
                if (jsonStart >= 0 && jsonEnd > jsonStart) {
                    String jsonStr = cleanedResponse.substring(jsonStart, jsonEnd + 1);
                    
                    jsonStr = jsonStr.replaceAll(",\\s*([\\]}])", "$1");
                    
                    log.info("Cleaned JSON: {}", jsonStr);
                    
                    JsonNode jsonFields = objectMapper.readTree(jsonStr);
                    
                    if (jsonFields.has("invoice_number") && !jsonFields.get("invoice_number").isNull()) {
                        fields.put("invoice_number", jsonFields.get("invoice_number").asText());
                    }
                    if (jsonFields.has("vendor_name") && !jsonFields.get("vendor_name").isNull()) {
                        fields.put("vendor_name", jsonFields.get("vendor_name").asText());
                    }
                    if (jsonFields.has("invoice_date") && !jsonFields.get("invoice_date").isNull()) {
                        fields.put("invoice_date", jsonFields.get("invoice_date").asText());
                    }
                    if (jsonFields.has("total_amount") && !jsonFields.get("total_amount").isNull()) {
                        fields.put("total_amount", jsonFields.get("total_amount").asText());
                    }
                } else {
                    log.warn("No JSON object found in Phi response: {}", cleanedResponse);
                }
            } catch (Exception e) {
                log.warn("Failed to parse LLM response: {}", e.getMessage());
            }
        } else {
            log.warn("Phi returned empty response");
        }
        
        return fields;
    }
    
    private String callOllamaAPI(String prompt, String model) throws Exception {
        String jsonPayload = objectMapper.writeValueAsString(
            new java.util.HashMap<String, Object>() {{
                put("model", model);
                put("prompt", prompt);
                put("stream", true);
                put("num_ctx", 2048);
            }}
        );
        
        RequestBody body = RequestBody.create(jsonPayload, MediaType.parse("application/json"));
        Request request = new Request.Builder()
            .url(OLLAMA_API_URL)
            .post(body)
            .build();
        
        StringBuilder fullResponse = new StringBuilder();
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(response.body().byteStream())
                );
                String line;
                while ((line = reader.readLine()) != null) {
                    try {
                        JsonNode lineNode = objectMapper.readTree(line);
                        if (lineNode.has("response")) {
                            fullResponse.append(lineNode.get("response").asText());
                        }
                    } catch (Exception e) {
                        log.debug("Failed to parse streaming line: {}", line);
                    }
                }
                log.debug("Ollama streaming response received");
                return fullResponse.toString();
            } else {
                log.error("Ollama API error: {} {}", response.code(), response.message());
                return null;
            }
        }
    }
    
    private List<String> chunkText(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        
        if (text == null || text.isEmpty()) {
            return chunks;
        }
        
        String[] lines = text.split("\n");
        StringBuilder currentChunk = new StringBuilder();
        
        for (String line : lines) {
            if (currentChunk.length() + line.length() + 1 > chunkSize && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());
                currentChunk = new StringBuilder();
            }
            
            currentChunk.append(line).append("\n");
        }
        
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }
        
        return chunks;
    }
    
    private List<String> findRelevantChunks(List<String> chunks) {
        if (chunks.isEmpty()) {
            return new ArrayList<>();
        }
        
        final String[] keywords = {"invoice", "total", "amount", "date", "bill", "vendor"};
        List<Map.Entry<String, Integer>> scoredChunks = new ArrayList<>();
        
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            String lowerChunk = chunk.toLowerCase();
            int score = 0;
            
            for (String keyword : keywords) {
                int idx = 0;
                while ((idx = lowerChunk.indexOf(keyword, idx)) != -1) {
                    score++;
                    idx += keyword.length();
                }
            }
            
            if (i == 0) {
                score += 5;
            }
            
            if (i == chunks.size() - 1) {
                score += 3;
            }
            
            if (score > 0) {
                scoredChunks.add(Map.entry(chunk, score));
            }
        }
        
        return scoredChunks.stream()
            .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
            .limit(3)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
    
    private String combineChunks(List<String> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }
        return String.join("\n---\n", chunks);
    }
    
    private Map<String, String> extractResumeFields(String text) {
        Map<String, String> fields = new HashMap<>();
        
        Pattern emailPattern = Pattern.compile("([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})");
        Matcher emailMatcher = emailPattern.matcher(text);
        if (emailMatcher.find()) {
            fields.put("email", emailMatcher.group(1));
        }
        
        Pattern phonePattern = Pattern.compile("(?:\\+?\\d{1,3}[\\s.-]?)?\\(?\\d{3}\\)?[\\s.-]?\\d{3}[\\s.-]?\\d{4}");
        Matcher phoneMatcher = phonePattern.matcher(text);
        if (phoneMatcher.find()) {
            fields.put("phone", phoneMatcher.group(0));
        }
        
        String[] lines = text.split("\n");
        if (lines.length > 0) {
            fields.put("full_name", lines[0].trim());
        }
        
        Pattern linkedinPattern = Pattern.compile("linkedin\\.com/in/([\\w\\-]+)", Pattern.CASE_INSENSITIVE);
        Matcher linkedinMatcher = linkedinPattern.matcher(text);
        if (linkedinMatcher.find()) {
            fields.put("linkedin", "linkedin.com/in/" + linkedinMatcher.group(1));
        }
        
        return fields;
    }
    
    private Map<String, String> extractContractFields(String text) {
        Map<String, String> fields = new HashMap<>();
        
        Pattern datePattern = Pattern.compile("(?:executed|dated|date)[:\\s]*(\\w+\\s+\\d{1,2},?\\s+\\d{4})", Pattern.CASE_INSENSITIVE);
        Matcher dateMatcher = datePattern.matcher(text);
        if (dateMatcher.find()) {
            fields.put("contract_date", dateMatcher.group(1));
        }
        
        Pattern partyPattern = Pattern.compile("(?:between|party)[:\\s]*([\\w\\s,&]+?)(?:and|,)", Pattern.CASE_INSENSITIVE);
        Matcher partyMatcher = partyPattern.matcher(text);
        if (partyMatcher.find()) {
            fields.put("party_1", partyMatcher.group(1).trim());
        }
        
        Pattern termPattern = Pattern.compile("(?:term|period)[:\\s]*(\\d+)\\s*(?:year|month|day)", Pattern.CASE_INSENSITIVE);
        Matcher termMatcher = termPattern.matcher(text);
        if (termMatcher.find()) {
            fields.put("contract_term", termMatcher.group(1));
        }
        
        return fields;
    }
}