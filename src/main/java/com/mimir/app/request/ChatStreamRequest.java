package com.mimir.app.request;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) // Prevents breaking on future payload additions
public class ChatStreamRequest {

    @JsonProperty("stream")
    private Boolean stream = true;

    @JsonProperty("version")
    private String version;

    @JsonProperty("incremental_output")
    private Boolean incrementalOutput = true;

    @JsonProperty("chat_id")
    @NotNull(message = "chat_id is required")
    private UUID chatId;

    @JsonProperty("chat_mode")
    private String chatMode = "normal";

    @JsonProperty("model")
    @NotBlank(message = "model is required")
    private String model;

    // Handles both "parent_id" and "parentId" safely
    @JsonAlias({"parent_id", "parentId"})
    @JsonProperty("parent_id")
    private UUID parentId;

    @JsonProperty("messages")
    @NotEmpty(message = "messages must contain at least one entry")
    @Valid
    private List<Message> messages;

    @JsonProperty("timestamp")
    private Long timestamp; // Epoch seconds. Use Instant with @JsonFormat if preferred.

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {
        @JsonProperty("fid")
        @NotNull
        private UUID fid;

        @JsonAlias({"parentId", "parent_id"})
        @JsonProperty("parentId")
        private UUID parentId;

        @JsonProperty("childrenIds")
        private List<UUID> childrenIds;

        @JsonProperty("role")
        @NotBlank
        private String role;

        @JsonProperty("content")
        @NotBlank
        private String content;

        @JsonProperty("user_action")
        private String userAction;

        @JsonProperty("files")
        private List<Object> files; // Replace with FileAttachmentDTO if structured

        @JsonProperty("timestamp")
        private Long timestamp;

        @JsonProperty("models")
        private List<String> models;

        @JsonProperty("chat_type")
        private String chatType;

        @JsonProperty("feature_config")
        @Valid
        private FeatureConfig featureConfig;

        @JsonProperty("extra")
        private Extra extra;

        @JsonProperty("sub_chat_type")
        private String subChatType;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FeatureConfig {
        @JsonProperty("thinking_enabled")
        private Boolean thinkingEnabled = false;

        @JsonProperty("output_schema")
        private String outputSchema;

        @JsonProperty("research_mode")
        private String researchMode;

        @JsonProperty("auto_thinking")
        private Boolean autoThinking = false;

        @JsonProperty("thinking_mode")
        private String thinkingMode;

        @JsonProperty("thinking_format")
        private String thinkingFormat;

        @JsonProperty("auto_search")
        private Boolean autoSearch = false;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Extra {
        @JsonProperty("meta")
        private Meta meta;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Meta {
            @JsonProperty("subChatType")
            private String subChatType;
        }
    }
}
