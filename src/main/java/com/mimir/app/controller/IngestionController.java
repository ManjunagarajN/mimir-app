package com.mimir.app.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.mimir.app.service.DataInjectionService;

import lombok.Generated;

@RestController
@RequestMapping
public class IngestionController {
    private final DataInjectionService dataInjectionService;

    @Generated
    public IngestionController(DataInjectionService dataInjectionService) {
        this.dataInjectionService = dataInjectionService;
    }

    @PostMapping(consumes = {"multipart/form-data", "application/json", "application/xml", "text/plain"})
    public ResponseEntity<String> ingest(
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @RequestPart(value = "inputSourceData", required = false) String inputSourceData) {
        this.dataInjectionService.ingest(files, inputSourceData);
        return ResponseEntity.ok("Ingestion request accepted successfully");
    }
}
