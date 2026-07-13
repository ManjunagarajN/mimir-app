package com.mimir.app.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.mimir.app.agent.client.IngestionClient;
import com.mimir.app.agent.client.PdfClient;
import com.mimir.app.agent.config.AgentStateManager;
import com.mimir.app.agent.domain.*;
import com.mimir.app.agent.utils.*;
import com.mimir.app.llm.OllamaStreamClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Service orchestrating the Purchase Intent creation workflow.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseIntentAgentService {

    private final PurchaseIntentClient purchaseIntentClient;
    private final PdfClient pdfClient;
    private final IngestionClient ingestionClient;
    private final OllamaParser ollamaParser;
    private final AgentStateManager stateManager;
    private final OllamaStreamClient ollamaStreamClient;
    
    private final JsonConversionService jsonConversionService;
    private final ArchiveExtractionService archiveExtractionService;
    private final TextFormattingService textFormattingService;
    private final DataCollectionService dataCollectionService;
    private final RetryUtil retryUtil;

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 2000;
    private static final String OLLAMA_MODEL = "llama3.2";
    private static final DateTimeFormatter DATE_FORMATTER = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Map<String, Object> executePurchaseIntentWorkflow(
            PurchaseAgentRequest request,
            Consumer<String> responseConsumer,
            AtomicBoolean cancelled) {

        logWorkflowStart(request);
        validateAccessToken(request);

        var state = initializeWorkflowState(request);
        var workflowResult = new HashMap<String, Object>();

        try {
            SearchCriteria searchCriteria = extractSearchCriteria(state, request, responseConsumer);
            Map<String, Object> searchResult = searchPurchaseIntents(state, searchCriteria, responseConsumer);
            List<JsonNode> lineItems = jsonConversionService.extractItemsFromSearchResult(searchResult);

            if (requiresPoColorSelection(lineItems, request)) {
                return buildPoColorSelectionResponse(lineItems, searchCriteria, responseConsumer);
            }

            if (hasPoColorFilter(request)) {
                lineItems = filterLineItemsByPoColors(lineItems, request.getPoColors());
                logFilteredResults(lineItems, request.getPoColors(), responseConsumer);
            }

            List<MaxPurchaseIntentRequest> intentRequests = 
                    buildPurchaseIntentRequests(lineItems, searchCriteria, responseConsumer);
            List<Long> createdPiNumbers = 
                    createPurchaseIntentsInSystem(state, intentRequests, responseConsumer);

            byte[] pdfContent = downloadAndIngestPurchaseIntentPdfs(
                    state, createdPiNumbers, responseConsumer);
            
            generateWorkflowSummary(state, request, createdPiNumbers, pdfContent, responseConsumer);

            buildSuccessResponse(workflowResult, createdPiNumbers, pdfContent, state);

            log.info("✅ WORKFLOW COMPLETED SUCCESSFULLY!");
            sendCompletionMessage(responseConsumer);

        } catch (Exception e) {
            handleWorkflowError(e, workflowResult, responseConsumer);
        }

        return workflowResult;
    }

    private SearchCriteria extractSearchCriteria(
            PurchaseIntentAgentState state,
            PurchaseAgentRequest request,
            Consumer<String> responseConsumer) throws IOException, InterruptedException {
        
        stateManager.updateStep(state, "plan");
        log.info("📋 STEP 1: Analyzing query with Ollama...");
        sendProgressMessage(responseConsumer, "📋 PLANNING: Analyzing query...\n");

        String analysisPrompt = ollamaParser.buildAnalysisPrompt(request.getQuery());
        String analysisResult = streamLlmResponse(analysisPrompt, responseConsumer);

        SearchCriteria extractedCriteria = ollamaParser.parseAnalysis(analysisResult);
        state.setSearchCriteria(extractedCriteria);

        validateMandatoryFields(extractedCriteria);

        log.info("✅ Criteria extracted: {}", extractedCriteria);
        sendProgressMessage(responseConsumer, "\n\n✅ Query analysis complete!\n\n");
        
        return extractedCriteria;
    }

    private Map<String, Object> searchPurchaseIntents(
            PurchaseIntentAgentState state,
            SearchCriteria criteria,
            Consumer<String> responseConsumer) throws Exception {
        
        stateManager.updateStep(state, "search");
        log.info("🔍 STEP 2: Searching...");
        sendProgressMessage(responseConsumer, "🔍 Searching...\n");

        Map<String, Object> searchResult = purchaseIntentClient.searchPurchaseIntents(
                state.getAccessToken(), criteria);

        if (!Boolean.TRUE.equals(searchResult.get("success"))) {
            throw new RuntimeException("Search failed: " + searchResult.get("error"));
        }

        long totalRecords = (long) searchResult.get("totalRecord");
        if (totalRecords == 0) {
            throw new RuntimeException("Style not found.");
        }

        log.info("✅ Found {} results", totalRecords);
        sendProgressMessage(responseConsumer, "✅ Found " + totalRecords + " results\n\n");
        
        return searchResult;
    }

    private List<MaxPurchaseIntentRequest> buildPurchaseIntentRequests(
            List<JsonNode> lineItems,
            SearchCriteria criteria,
            Consumer<String> responseConsumer) throws IOException, InterruptedException {
        
        log.info("🔨 STEP 3: Building {} requests...", lineItems.size());
        sendProgressMessage(responseConsumer, "🔨 Building requests...\n");

        Map<String, Object> fieldOverrides = extractFieldOverrides(criteria, responseConsumer);

        List<MaxPurchaseIntentRequest> requests = lineItems.stream()
                .map(lineItem -> buildSinglePurchaseIntentRequest(lineItem, fieldOverrides))
                .toList();

        log.info("✅ Built {} requests successfully!", requests.size());
        sendProgressMessage(responseConsumer, "\n\n✅ Built " + requests.size() + " requests!\n\n");
        
        return requests;
    }

    private List<Long> createPurchaseIntentsInSystem(
            PurchaseIntentAgentState state,
            List<MaxPurchaseIntentRequest> intentRequests,
            Consumer<String> responseConsumer) throws Exception {
        
        log.info("📦 STEP 4: Creating {} PIs...", intentRequests.size());
        sendProgressMessage(responseConsumer, "📦 Creating " + intentRequests.size() + " PIs...\n");

        Map<String, Object> createResult = purchaseIntentClient.createPurchaseIntents(
                state.getAccessToken(), intentRequests);

        if (!Boolean.TRUE.equals(createResult.get("success"))) {
            throw new RuntimeException("Creation failed: " + createResult.get("error"));
        }

        List<Long> createdPiNumbers = extractCreatedPiNumbers(createResult);

        if (dataCollectionService.isEmpty(createdPiNumbers)) {
            throw new RuntimeException("Could not extract PI numbers from create result.");
        }

        state.setCreatedPiNumbers(createdPiNumbers);
        log.info("✅ Created {} PIs! Numbers: {}", createdPiNumbers.size(), createdPiNumbers);
        sendProgressMessage(responseConsumer, 
                "✅ Created " + createdPiNumbers.size() + " PIs! Numbers: " + createdPiNumbers + "\n\n");

        return createdPiNumbers;
    }

    private byte[] downloadAndIngestPurchaseIntentPdfs(
            PurchaseIntentAgentState state,
            List<Long> createdPiNumbers,
            Consumer<String> responseConsumer) {
        
        if (dataCollectionService.isEmpty(createdPiNumbers)) {
            return null;
        }

        log.info("📄 STEP 5: Downloading PDFs for {} PIs...", createdPiNumbers.size());
        sendProgressMessage(responseConsumer, "📄 Downloading PDFs...\n");

        try {
            byte[] zipContent = downloadPdfZip(state.getAccessToken(), createdPiNumbers);
            
            if (zipContent == null || zipContent.length == 0) {
                log.warn("⚠️ No files downloaded");
                return null;
            }

            log.info("✅ ZIP downloaded! Size: {} bytes", zipContent.length);
            sendProgressMessage(responseConsumer, "✅ ZIP downloaded! Size: " + zipContent.length + " bytes\n");

            Map<String, byte[]> extractedPdfs = archiveExtractionService.extractPdfFiles(zipContent);

            if (extractedPdfs.isEmpty()) {
                log.warn("⚠️ No PDFs found in ZIP file");
                return zipContent;
            }

            log.info("✅ Extracted {} PDF(s) from ZIP", extractedPdfs.size());
            sendProgressMessage(responseConsumer, "✅ Extracted " + extractedPdfs.size() + " PDF(s)\n");

            ingestExtractedPdfs(state, createdPiNumbers, extractedPdfs, responseConsumer);

            return zipContent;

        } catch (Exception e) {
            log.warn("⚠️ File processing failed: {}", e.getMessage());
            sendProgressMessage(responseConsumer, "⚠️ File processing failed\n\n");
        }
        
        return null;
    }

    private void generateWorkflowSummary(
            PurchaseIntentAgentState state,
            PurchaseAgentRequest request,
            List<Long> createdPiNumbers,
            byte[] pdfContent,
            Consumer<String> responseConsumer) throws IOException, InterruptedException {
        
        log.info("📝 STEP 6: Generating summary...");
        sendProgressMessage(responseConsumer, "📝 Generating summary...\n\n");

        String summaryPrompt = ollamaParser.buildWorkflowSummaryPrompt(
                request.getQuery(),
                dataCollectionService.isNotEmpty(createdPiNumbers) ? createdPiNumbers.size() : 0,
                dataCollectionService.isNotEmpty(createdPiNumbers) ? createdPiNumbers.toString() : "None",
                pdfContent != null ? pdfContent.length : 0
        );

        streamLlmResponse(summaryPrompt, responseConsumer);
    }

    private void logWorkflowStart(PurchaseAgentRequest request) {
        log.info("=================================================");
        log.info("🛒 PURCHASE INTENT AGENT WITH OLLAMA");
        log.info("📝 Email: {}", request.getEmail());
        log.info("📝 Query: {}", request.getQuery());
        log.info("🎨 PO Colors: {}", request.getPoColors());
        log.info("=================================================");
    }

    private void validateAccessToken(PurchaseAgentRequest request) {
        if (textFormattingService.isBlank(request.getAccessToken())) {
            throw new RuntimeException("Access token is missing.");
        }
    }

    private PurchaseIntentAgentState initializeWorkflowState(PurchaseAgentRequest request) {
        var state = stateManager.createInitialState(request.getQuery());
        state.setAccessToken(request.getAccessToken());
        return state;
    }

    private boolean requiresPoColorSelection(List<JsonNode> lineItems, PurchaseAgentRequest request) {
        return lineItems.size() > 1 && dataCollectionService.isEmpty(request.getPoColors());
    }

    private boolean hasPoColorFilter(PurchaseAgentRequest request) {
        return dataCollectionService.isNotEmpty(request.getPoColors());
    }

    private Map<String, Object> buildPoColorSelectionResponse(
            List<JsonNode> lineItems,
            SearchCriteria searchCriteria,
            Consumer<String> responseConsumer) {
        
        List<String> availableColors = extractDistinctPoColors(lineItems);
        log.info("🎨 Multiple styles found with {} unique PO colors: {}", 
                availableColors.size(), availableColors);

        sendProgressMessage(responseConsumer, "\n\n🎨 Multiple styles found with different PO colors.\n");
        sendProgressMessage(responseConsumer, "Please select PO color(s) to proceed.\n");

        return Map.of(
                "success", true,
                "requiresPoColorSelection", true,
                "availablePoColors", availableColors,
                "searchCriteria", searchCriteria,
                "message", "Multiple styles found. Please select PO color(s) to proceed."
        );
    }

    private void logFilteredResults(
            List<JsonNode> lineItems,
            List<String> poColors,
            Consumer<String> responseConsumer) {
        
        log.info("🎨 Filtered to {} items with PO colors: {}", lineItems.size(), poColors);
        sendProgressMessage(responseConsumer,
                "🎨 Filtered by PO colors: " + textFormattingService.joinWithComma(poColors) + "\n");
    }

    private void validateMandatoryFields(SearchCriteria criteria) {
        List<String> missingFields = new ArrayList<>();
        
        if (textFormattingService.isBlank(criteria.getDivName())) missingFields.add("Division");
        if (dataCollectionService.isEmpty(criteria.getGroupName())) missingFields.add("Group");
        if (dataCollectionService.isEmpty(criteria.getDeptName())) missingFields.add("Department");
        if (dataCollectionService.isEmpty(criteria.getStyle())) missingFields.add("Style");

        if (dataCollectionService.isNotEmpty(missingFields)) {
            throw new RuntimeException("Cannot proceed. Missing mandatory fields: " + 
                    textFormattingService.joinWithComma(missingFields));
        }
    }

    private Map<String, Object> extractFieldOverrides(
            SearchCriteria criteria,
            Consumer<String> responseConsumer) throws IOException, InterruptedException {
        
        String overridePrompt = ollamaParser.buildFieldOverridePrompt(criteria);
        String overrideResult = streamLlmResponse(overridePrompt, responseConsumer);

        String cleanedJson = ollamaParser.extractAndFixJson(overrideResult);
        return jsonConversionService.toMap(cleanedJson);
    }

    private MaxPurchaseIntentRequest buildSinglePurchaseIntentRequest(
            JsonNode lineItem,
            Map<String, Object> fieldOverrides) {
        
        MaxPurchaseLineItem lineItemObj = jsonConversionService.convertValue(lineItem, MaxPurchaseLineItem.class);
        applyFieldOverrides(lineItemObj, fieldOverrides);

        var request = new MaxPurchaseIntentRequest();
        request.setMaxPurchaseLineItems(lineItemObj);
        request.setIdentity(buildIdentity(lineItem, lineItemObj));
        request.setSourceSnapshot(buildSourceSnapshot(lineItemObj));
        request.setWorkflowCommand(buildWorkflowCommand());

        return request;
    }

    private void applyFieldOverrides(MaxPurchaseLineItem lineItem, Map<String, Object> overrides) {
        var overrideMappings = Map.of(
                "division", (Consumer<String>) lineItem::setDivision,
                "groupName", (Consumer<String>) lineItem::setGroupName,
                "department", (Consumer<String>) lineItem::setDepartment,
                "style", (Consumer<String>) lineItem::setStyle,
                "hit", (Consumer<String>) lineItem::setHit,
                "supplier", (Consumer<String>) lineItem::setSupplier
        );

        overrideMappings.forEach((key, setter) -> {
            Object value = overrides.get(key);
            if (textFormattingService.isValidStringValue(value)) {
                setter.accept(value.toString());
            }
        });
    }

    private IntentRequest.Identity buildIdentity(JsonNode lineItem, MaxPurchaseLineItem lineItemObj) {
        var identity = new IntentRequest.Identity();
        identity.setRefId(lineItemObj.getId() != null ? lineItemObj.getId() : UUID.randomUUID().toString());
        identity.setPurchaseIntentNo(jsonConversionService.getLongValue(lineItem, "purchaseIntentNo", 0L));
        identity.setPurchaseOrderNo(0L);
        return identity;
    }

    private IntentRequest.SourceSnapshot buildSourceSnapshot(MaxPurchaseLineItem lineItem) {
        var snapshot = new IntentRequest.SourceSnapshot();
        snapshot.setRowHash(lineItem.getRowHash() != null ? lineItem.getRowHash() : UUID.randomUUID().toString());
        snapshot.setLastUpdatedAmendAt(LocalDateTime.now().format(DATE_FORMATTER));
        snapshot.setLastUpdatedAmendBy("agent-auto");
        snapshot.setIsAmended(false);
        return snapshot;
    }

    private IntentRequest.WorkflowCommand buildWorkflowCommand() {
        var command = new IntentRequest.WorkflowCommand();
        command.setEvent("CREATE");
        command.setAllocationRequestedBy("");
        command.setAllocationRequestedAt("");
        command.setIsAllocationRequested(false);
        return command;
    }

    private byte[] downloadPdfZip(String accessToken, List<Long> piNumbers) throws Exception {
        return retryUtil.executeWithRetry(
                "PDF download",
                MAX_RETRIES,
                INITIAL_RETRY_DELAY_MS,
                () -> pdfClient.downloadPurchaseIntentPdf(accessToken, piNumbers),
                new byte[0]
        );
    }

    private void ingestExtractedPdfs(
            PurchaseIntentAgentState state,
            List<Long> createdPiNumbers,
            Map<String, byte[]> extractedPdfs,
            Consumer<String> responseConsumer) {
        
        sendProgressMessage(responseConsumer, "📤 Calling ingestion API...\n");

        String inputSourceData = String.format(
                "{\"piNumbers\": %s, \"source\": \"purchase-intent-agent\", \"timestamp\": \"%s\"}",
                createdPiNumbers.toString(),
                LocalDateTime.now().format(DATE_FORMATTER)
        );

        Map<String, Object> ingestionResult = ingestionClient.ingestPdfFiles(extractedPdfs, inputSourceData);

        if (Boolean.TRUE.equals(ingestionResult.get("success"))) {
            log.info("✅ Ingestion successful for {} PDFs", extractedPdfs.size());
            sendProgressMessage(responseConsumer, "✅ Ingestion successful!\n\n");
            stateManager.storeResult(state, "ingestion", ingestionResult);
        } else {
            log.warn("⚠️ Ingestion failed: {}", ingestionResult.get("message"));
            sendProgressMessage(responseConsumer, "⚠️ Ingestion failed\n\n");
        }
    }

    private void buildSuccessResponse(
            Map<String, Object> response,
            List<Long> createdPiNumbers,
            byte[] pdfContent,
            PurchaseIntentAgentState state) {
        
        response.put("success", true);
        response.put("message", "Workflow completed successfully!");
        response.put("createdPiNumbers", createdPiNumbers);
        response.put("requiresPoColorSelection", false);

        if (state.getResults().containsKey("ingestion")) {
            response.put("ingestionResult", state.getResults().get("ingestion"));
        }

        if (pdfContent != null && pdfContent.length > 0) {
            response.put("pdfContent", Base64.getEncoder().encodeToString(pdfContent));
            response.put("pdfSize", pdfContent.length);
            response.put("fileType", "zip");
        }
    }

    private void handleWorkflowError(
            Exception e,
            Map<String, Object> response,
            Consumer<String> responseConsumer) {
        
        log.error("❌ Agent error: {}", e.getMessage(), e);
        response.put("success", false);
        response.put("error", e.getMessage());
        sendProgressMessage(responseConsumer, "❌ Error: " + e.getMessage() + "\n");
    }

    private List<String> extractDistinctPoColors(List<JsonNode> items) {
        return items.stream()
                .map(item -> jsonConversionService.getStringValue(item, "poColour"))
                .flatMap(Optional::stream)
                .filter(textFormattingService::isNotBlank)
                .distinct()
                .toList();
    }

    private List<JsonNode> filterLineItemsByPoColors(List<JsonNode> items, List<String> poColors) {
        Set<String> colorSet = new HashSet<>(poColors);
        
        List<JsonNode> filtered = items.stream()
                .filter(item -> {
                    Optional<String> itemColor = jsonConversionService.getStringValue(item, "poColour");
                    return itemColor.isPresent() && 
                           colorSet.stream().anyMatch(color -> 
                                   color.equalsIgnoreCase(itemColor.get()));
                })
                .toList();
        
        return dataCollectionService.isEmpty(filtered) ? items : filtered;
    }

    private List<Long> extractCreatedPiNumbers(Map<String, Object> createResult) {
        try {
            Object dataObj = createResult.get("data");
            
            if (dataObj instanceof Map<?, ?> responseMap) {
                Object innerData = responseMap.get("data");
                
                if (innerData instanceof List<?> list) {
                    return list.stream()
                            .filter(item -> item instanceof Map<?, ?>)
                            .map(item -> (Map<?, ?>) item)
                            .filter(map -> map.containsKey("purchaseIntentNumber"))
                            .map(map -> Long.valueOf(map.get("purchaseIntentNumber").toString()))
                            .toList();
                }
            }
        } catch (Exception e) {
            log.error("❌ Error extracting PI numbers: {}", e.getMessage());
        }
        
        return Collections.emptyList();
    }

    private String streamLlmResponse(String prompt, Consumer<String> responseConsumer) throws IOException, InterruptedException {
        var result = new StringBuilder();
        
        ollamaStreamClient.stream(
                OLLAMA_MODEL,
                prompt,
                token -> {
                    result.append(token);
                    sendProgressMessage(responseConsumer, token);
                },
                new AtomicBoolean(false)
        );
        
        return result.toString();
    }

    private void sendProgressMessage(Consumer<String> responseConsumer, String message) {
        if (responseConsumer != null) {
            responseConsumer.accept(message);
        }
    }

    private void sendCompletionMessage(Consumer<String> responseConsumer) {
        sendProgressMessage(responseConsumer, "\n\n✅ WORKFLOW COMPLETED!\n");
    }
}