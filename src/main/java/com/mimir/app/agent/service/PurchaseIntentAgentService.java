package com.mimir.app.agent.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.mimir.app.agent.domain.*;
import com.mimir.app.agent.utils.*;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimir.app.agent.domain.*;
import com.mimir.app.agent.utils.*;
import com.mimir.app.llm.OllamaStreamClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseIntentAgentService {

    private final AuthClient authClient;
    private final PurchaseIntentClient purchaseIntentClient;
    private final PdfClient pdfClient;
    private final IngestionClient ingestionClient;
    private final OllamaParser ollamaParser;
    private final AgentStateManager stateManager;
    private final OllamaStreamClient ollamaStreamClient;
    private final ObjectMapper objectMapper;
    private final RetryUtil retryUtil;

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 2000;
    private static final String OLLAMA_MODEL = "llama3.2";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Map<String, Object> processFullWorkflow(
            PurchaseAgentRequest request, Consumer<String> responseConsumer, AtomicBoolean cancelled) {

        String workflowKey = resolveWorkflowKey(request);
        log.info("=================================================");
        log.info("🛒 PURCHASE INTENT AGENT WITH OLLAMA");
        log.info("📝 Email: {}", request.getEmail());
        log.info("📝 Query: {}", request.getQuery());
        log.info("🎨 PO Colors: {}", request.getPoColors());
        log.info("=================================================");

        String accessToken = request.getAccessToken();
        if (accessToken == null || accessToken.isBlank()) {
            throw new RuntimeException("Access token is missing.");
        }

        var state = stateManager.createInitialState(request.getQuery());
        state.setAccessToken(accessToken);
        var finalResponse = new HashMap<String, Object>();

        try {
            SearchCriteria searchCriteria = runPlanStep(state, request, responseConsumer);
            Map<String, Object> searchResult = runSearchStep(state, searchCriteria, responseConsumer);

            List<JsonNode> allItems = extractAllItemsFromSearch(searchResult);

            // Check if multiple styles found and need PO color selection
            if (allItems.size() > 1
                    && (request.getPoColors() == null || request.getPoColors().isEmpty())) {
                List<String> availableColors = extractUniquePoColors(allItems);
                log.info(
                        "🎨 Multiple styles found with {} unique PO colors: {}",
                        availableColors.size(),
                        availableColors);

                if (responseConsumer != null) {
                    responseConsumer.accept("\n\n🎨 Multiple styles found with different PO colors.\n");
                    responseConsumer.accept("Please select PO color(s) to proceed.\n");
                }

                finalResponse.put("success", true);
                finalResponse.put("requiresPoColorSelection", true);
                finalResponse.put("availablePoColors", availableColors);
                finalResponse.put("searchCriteria", searchCriteria);
                finalResponse.put("message", "Multiple styles found. Please select PO color(s) to proceed.");

                return finalResponse;
            }

            // Filter by PO colors if provided
            if (request.getPoColors() != null && !request.getPoColors().isEmpty()) {
                allItems = filterByPoColors(allItems, request.getPoColors());
                log.info("🎨 Filtered to {} items with PO colors: {}", allItems.size(), request.getPoColors());
                if (responseConsumer != null) {
                    responseConsumer.accept(
                            "🎨 Filtered by PO colors: " + String.join(", ", request.getPoColors()) + "\n");
                }
            }

            List<MaxPurchaseIntentRequest> intentRequests =
                    buildRequestsFromItems(allItems, searchCriteria, responseConsumer);
            List<Long> createdPiNumbers = createPurchaseIntents(state, intentRequests, responseConsumer);

            byte[] pdfContent = downloadAndIngestPdfs(state, createdPiNumbers, responseConsumer);
            String finalAnswer = runAnswerStep(state, request, createdPiNumbers, pdfContent, responseConsumer);

            finalResponse.put("success", true);
            finalResponse.put("message", "Workflow completed successfully!");
            finalResponse.put("createdPiNumbers", createdPiNumbers);
            finalResponse.put("requiresPoColorSelection", false);

            if (state.getResults().containsKey("ingestion")) {
                finalResponse.put("ingestionResult", state.getResults().get("ingestion"));
            }

            if (pdfContent != null && pdfContent.length > 0) {
                finalResponse.put("pdfContent", Base64.getEncoder().encodeToString(pdfContent));
                finalResponse.put("pdfSize", pdfContent.length);
                finalResponse.put("fileType", "zip");
            }

            log.info("✅ WORKFLOW COMPLETED SUCCESSFULLY!");
            if (responseConsumer != null) responseConsumer.accept("\n\n✅ WORKFLOW COMPLETED!\n");

        } catch (Exception e) {
            log.error("❌ Agent error: {}", e.getMessage(), e);
            finalResponse.put("success", false);
            finalResponse.put("error", e.getMessage());
            if (responseConsumer != null) responseConsumer.accept("❌ Error: " + e.getMessage() + "\n");
        }

        return finalResponse;
    }

    // Extract unique PO colors from items
    private List<String> extractUniquePoColors(List<JsonNode> items) {
        Set<String> colors = new LinkedHashSet<>();
        for (JsonNode item : items) {
            if (item.has("poColour") && !item.get("poColour").isNull()) {
                String color = item.get("poColour").asText();
                if (color != null && !color.isBlank()) {
                    colors.add(color);
                }
            }
        }
        return new ArrayList<>(colors);
    }

    // Filter items by multiple PO colors
    private List<JsonNode> filterByPoColors(List<JsonNode> items, List<String> poColors) {
        List<JsonNode> filtered = new ArrayList<>();
        for (JsonNode item : items) {
            if (item.has("poColour") && !item.get("poColour").isNull()) {
                String itemColor = item.get("poColour").asText();
                for (String color : poColors) {
                    if (color.equalsIgnoreCase(itemColor)) {
                        filtered.add(item);
                        break;
                    }
                }
            }
        }
        return filtered.isEmpty() ? items : filtered;
    }

    private SearchCriteria runPlanStep(
            PurchaseIntentAgentState state, PurchaseAgentRequest request, Consumer<String> responseConsumer)
            throws Exception {
        stateManager.updateStep(state, "plan");
        log.info("📋 STEP 1: Analyzing query with Ollama...");
        if (responseConsumer != null) responseConsumer.accept("📋 PLANNING: Analyzing query...\n");

        var analysisPrompt = ollamaParser.buildAnalysisPrompt(state.getQuery());
        var analysisResult = new StringBuilder();

        ollamaStreamClient.stream(
                OLLAMA_MODEL,
                analysisPrompt,
                token -> {
                    analysisResult.append(token);
                    if (responseConsumer != null) responseConsumer.accept(token);
                },
                new AtomicBoolean(false));

        var extractedCriteria = ollamaParser.parseAnalysis(analysisResult.toString());
        state.setSearchCriteria(extractedCriteria);

        List<String> missingFields = new ArrayList<>();
        if (extractedCriteria.getDivName() == null
                || extractedCriteria.getDivName().isBlank()) missingFields.add("Division");
        if (extractedCriteria.getGroupName() == null
                || extractedCriteria.getGroupName().isEmpty()) missingFields.add("Group");
        if (extractedCriteria.getDeptName() == null
                || extractedCriteria.getDeptName().isEmpty()) missingFields.add("Department");
        if (extractedCriteria.getStyle() == null || extractedCriteria.getStyle().isEmpty()) missingFields.add("Style");

        if (!missingFields.isEmpty()) {
            throw new RuntimeException("Cannot proceed. Missing mandatory fields: " + String.join(", ", missingFields));
        }

        log.info("✅ Criteria extracted: {}", extractedCriteria);
        if (responseConsumer != null) responseConsumer.accept("\n\n✅ Query analysis complete!\n\n");
        return extractedCriteria;
    }

    private Map<String, Object> runSearchStep(
            PurchaseIntentAgentState state, SearchCriteria criteria, Consumer<String> responseConsumer)
            throws Exception {
        stateManager.updateStep(state, "search");
        log.info("🔍 STEP 2: Searching...");
        if (responseConsumer != null) responseConsumer.accept("🔍 Searching...\n");

        var searchResult = purchaseIntentClient.searchPurchaseIntents(state.getAccessToken(), criteria);

        if (!Boolean.TRUE.equals(searchResult.get("success"))) {
            throw new RuntimeException("Search failed: " + searchResult.get("error"));
        }

        var total = (long) searchResult.get("totalRecord");
        if (total == 0) throw new RuntimeException("Style not found.");

        log.info("✅ Found {} results", total);
        if (responseConsumer != null) responseConsumer.accept("✅ Found " + total + " results\n\n");
        return searchResult;
    }

    private List<MaxPurchaseIntentRequest> buildRequestsFromItems(
            List<JsonNode> allItems, SearchCriteria criteria, Consumer<String> responseConsumer) throws Exception {
        log.info("🔨 STEP 3: Building {} requests...", allItems.size());
        if (responseConsumer != null) responseConsumer.accept("🔨 Building requests...\n");

        List<MaxPurchaseIntentRequest> requests = new ArrayList<>();
        String criteriaJson = objectMapper.writeValueAsString(criteria);

        String deltaPrompt = """
                You are an expert JSON builder. The user wants to update specific fields in a Purchase Intent.
                User requested changes (SearchCriteria): %s
                STRICT RULES:
                1. Output a JSON object containing ONLY the fields that the user EXPLICITLY mentioned.
                2. Map fields: divName->"division", groupName->"groupName", deptName->"department", style->"style", season->"hit", vendor->"supplier".
                3. Output ONLY raw JSON. No code, no markdown. All values MUST be Strings.
                4. NEVER output empty strings ("") or the word "null". If a field is not mentioned, OMIT IT.
                """.formatted(criteriaJson);

        var deltaResult = new StringBuilder();
        ollamaStreamClient.stream(
                OLLAMA_MODEL,
                deltaPrompt,
                token -> {
                    deltaResult.append(token);
                    if (responseConsumer != null) responseConsumer.accept(token);
                },
                new AtomicBoolean(false));

        String cleanedDeltaJson = ollamaParser.extractAndFixJson(deltaResult.toString());
        Map<String, Object> overrides = new HashMap<>();
        try {
            overrides = objectMapper.readValue(cleanedDeltaJson, Map.class);
        } catch (Exception e) {
            log.warn("⚠️ Failed to parse LLM delta JSON. Proceeding with base templates only.");
        }

        for (JsonNode item : allItems) {
            MaxPurchaseLineItem lineItem = objectMapper.convertValue(item, MaxPurchaseLineItem.class);

            if (overrides.containsKey("division") && isNotBlank(overrides.get("division")))
                lineItem.setDivision(overrides.get("division").toString());
            if (overrides.containsKey("groupName") && isNotBlank(overrides.get("groupName")))
                lineItem.setGroupName(overrides.get("groupName").toString());
            if (overrides.containsKey("department") && isNotBlank(overrides.get("department")))
                lineItem.setDepartment(overrides.get("department").toString());
            if (overrides.containsKey("style") && isNotBlank(overrides.get("style")))
                lineItem.setStyle(overrides.get("style").toString());
            if (overrides.containsKey("hit") && isNotBlank(overrides.get("hit")))
                lineItem.setHit(overrides.get("hit").toString());
            if (overrides.containsKey("supplier") && isNotBlank(overrides.get("supplier")))
                lineItem.setSupplier(overrides.get("supplier").toString());

            MaxPurchaseIntentRequest request = new MaxPurchaseIntentRequest();
            request.setMaxPurchaseLineItems(lineItem);

            IntentRequest.Identity identity = new IntentRequest.Identity();
            identity.setRefId(
                    lineItem.getId() != null
                            ? lineItem.getId()
                            : UUID.randomUUID().toString());
            identity.setPurchaseIntentNo(
                    item.has("purchaseIntentNo")
                                    && !item.get("purchaseIntentNo").isNull()
                            ? item.get("purchaseIntentNo").asLong()
                            : 0L);
            identity.setPurchaseOrderNo(0L);
            request.setIdentity(identity);

            IntentRequest.SourceSnapshot snapshot = new IntentRequest.SourceSnapshot();
            snapshot.setRowHash(
                    lineItem.getRowHash() != null
                            ? lineItem.getRowHash()
                            : UUID.randomUUID().toString());
            snapshot.setLastUpdatedAmendAt(LocalDateTime.now().format(DATE_FORMATTER));
            snapshot.setLastUpdatedAmendBy("agent-auto");
            snapshot.setIsAmended(false);
            request.setSourceSnapshot(snapshot);

            IntentRequest.WorkflowCommand command = new IntentRequest.WorkflowCommand();
            command.setEvent("CREATE");
            command.setAllocationRequestedBy("");
            command.setAllocationRequestedAt("");
            command.setIsAllocationRequested(false);
            request.setWorkflowCommand(command);

            requests.add(request);
        }

        log.info("✅ Built {} requests successfully!", requests.size());
        if (responseConsumer != null) responseConsumer.accept("\n\n✅ Built " + requests.size() + " requests!\n\n");
        return requests;
    }

    private List<Long> createPurchaseIntents(
            PurchaseIntentAgentState state,
            List<MaxPurchaseIntentRequest> intentRequests,
            Consumer<String> responseConsumer)
            throws Exception {
        log.info("📦 STEP 4: Creating {} PIs...", intentRequests.size());
        if (responseConsumer != null) responseConsumer.accept("📦 Creating " + intentRequests.size() + " PIs...\n");

        var createResult = purchaseIntentClient.createPurchaseIntents(state.getAccessToken(), intentRequests);

        if (!Boolean.TRUE.equals(createResult.get("success"))) {
            throw new RuntimeException("Creation failed: " + createResult.get("error"));
        }

        List<Long> createdPiNumbers = extractPiNumbersFromCreateResult(createResult);

        if (createdPiNumbers.isEmpty()) {
            throw new RuntimeException("Could not extract PI numbers from create result.");
        }

        state.setCreatedPiNumbers(createdPiNumbers);
        log.info("✅ Created {} PIs! Numbers: {}", createdPiNumbers.size(), createdPiNumbers);
        if (responseConsumer != null)
            responseConsumer.accept(
                    "✅ Created " + createdPiNumbers.size() + " PIs! Numbers: " + createdPiNumbers + "\n\n");

        return createdPiNumbers;
    }

    private byte[] downloadAndIngestPdfs(
            PurchaseIntentAgentState state, List<Long> createdPiNumbers, Consumer<String> responseConsumer) {
        if (createdPiNumbers == null || createdPiNumbers.isEmpty()) return null;

        log.info("📄 STEP 5: Downloading PDFs for {} PIs...", createdPiNumbers.size());
        if (responseConsumer != null) responseConsumer.accept("📄 Downloading PDFs...\n");

        try {
            byte[] zipContent = retryUtil.executeWithRetry(
                    "PDF download",
                    MAX_RETRIES,
                    INITIAL_RETRY_DELAY_MS,
                    () -> pdfClient.downloadPurchaseIntentPdf(state.getAccessToken(), createdPiNumbers),
                    new byte[0]);

            if (zipContent == null || zipContent.length == 0) {
                log.warn("⚠️ No files downloaded");
                return null;
            }

            log.info("✅ ZIP downloaded! Size: {} bytes", zipContent.length);
            if (responseConsumer != null)
                responseConsumer.accept("✅ ZIP downloaded! Size: " + zipContent.length + " bytes\n");

            Map<String, byte[]> extractedPdfs = extractPdfsFromZip(zipContent);

            if (extractedPdfs.isEmpty()) {
                log.warn("⚠️ No PDFs found in ZIP file");
                return zipContent;
            }

            log.info("✅ Extracted {} PDF(s) from ZIP", extractedPdfs.size());
            if (responseConsumer != null) responseConsumer.accept("✅ Extracted " + extractedPdfs.size() + " PDF(s)\n");

            if (responseConsumer != null) responseConsumer.accept("📤 Calling ingestion API...\n");

            String inputSourceData = String.format(
                    "{\"piNumbers\": %s, \"source\": \"purchase-intent-agent\", \"timestamp\": \"%s\"}",
                    createdPiNumbers.toString(), LocalDateTime.now().format(DATE_FORMATTER));

            Map<String, Object> ingestionResult = ingestionClient.ingestPdfFiles(extractedPdfs, inputSourceData);

            if (Boolean.TRUE.equals(ingestionResult.get("success"))) {
                log.info("✅ Ingestion successful for {} PDFs", extractedPdfs.size());
                if (responseConsumer != null) responseConsumer.accept("✅ Ingestion successful!\n\n");
                stateManager.storeResult(state, "ingestion", ingestionResult);
            } else {
                log.warn("⚠️ Ingestion failed: {}", ingestionResult.get("message"));
                if (responseConsumer != null) responseConsumer.accept("⚠️ Ingestion failed\n\n");
            }

            return zipContent;

        } catch (Exception e) {
            log.warn("⚠️ File processing failed: {}", e.getMessage());
            if (responseConsumer != null) responseConsumer.accept("⚠️ File processing failed\n\n");
        }
        return null;
    }

    private String runAnswerStep(
            PurchaseIntentAgentState state,
            PurchaseAgentRequest request,
            List<Long> createdPiNumbers,
            byte[] pdfContent,
            Consumer<String> responseConsumer)
            throws Exception {
        log.info("📝 STEP 6: Generating summary...");
        if (responseConsumer != null) responseConsumer.accept("📝 Generating summary...\n\n");

        var answerPrompt = "Summarize the workflow: Query='%s', Created %d PIs: %s, File=%d bytes"
                .formatted(
                        request.getQuery(),
                        createdPiNumbers != null ? createdPiNumbers.size() : 0,
                        createdPiNumbers != null ? createdPiNumbers.toString() : "None",
                        pdfContent != null ? pdfContent.length : 0);

        var finalAnswer = new StringBuilder();
        ollamaStreamClient.stream(
                OLLAMA_MODEL,
                answerPrompt,
                token -> {
                    finalAnswer.append(token);
                    if (responseConsumer != null) responseConsumer.accept(token);
                },
                new AtomicBoolean(false));

        return finalAnswer.toString();
    }

    // Helper methods
    private String resolveWorkflowKey(PurchaseAgentRequest request) {
        return request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "anon";
    }

    private List<JsonNode> extractAllItemsFromSearch(Map<String, Object> searchResult) {
        List<JsonNode> items = new ArrayList<>();
        try {
            Object itemsObj = searchResult.get("items");
            if (itemsObj instanceof com.fasterxml.jackson.databind.node.ArrayNode arrayNode) {
                for (JsonNode node : arrayNode) items.add(node);
            } else if (itemsObj instanceof List<?> list) {
                for (Object obj : list) items.add(objectMapper.valueToTree(obj));
            }
        } catch (Exception e) {
            log.warn("Could not extract items: {}", e.getMessage());
        }
        return items;
    }

    private List<Long> extractPiNumbersFromCreateResult(Map<String, Object> createResult) {
        List<Long> piNumbers = new ArrayList<>();
        try {
            Object dataObj = createResult.get("data");
            if (dataObj instanceof Map<?, ?> responseMap) {
                if (responseMap.containsKey("data")) {
                    Object innerData = responseMap.get("data");
                    if (innerData instanceof List<?> list) {
                        for (Object item : list) {
                            if (item instanceof Map<?, ?> map) {
                                if (map.containsKey("purchaseIntentNumber")) {
                                    piNumbers.add(Long.valueOf(
                                            map.get("purchaseIntentNumber").toString()));
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("❌ Error extracting PI numbers: {}", e.getMessage());
        }
        return piNumbers;
    }

    private Map<String, byte[]> extractPdfsFromZip(byte[] zipBytes) {
        Map<String, byte[]> pdfs = new LinkedHashMap<>();
        if (zipBytes.length < 4 || zipBytes[0] != 0x50 || zipBytes[1] != 0x4B) {
            pdfs.put("extracted.pdf", zipBytes);
            return pdfs;
        }
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".pdf")) {
                    String fileName = entry.getName();
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = zis.read(buffer)) > 0) baos.write(buffer, 0, len);
                    pdfs.put(fileName, baos.toByteArray());
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            log.error("❌ Error extracting PDFs: {}", e.getMessage());
        }
        return pdfs;
    }

    private boolean isNotBlank(Object value) {
        if (value == null) return false;
        String str = value.toString().trim();
        return !str.isEmpty() && !str.equalsIgnoreCase("null") && !str.startsWith("[");
    }
}
