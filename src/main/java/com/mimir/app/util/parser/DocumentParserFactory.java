package com.mimir.app.util.parser;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentParserFactory {
    private final List<DocumentParser> parsers;

    public Optional<DocumentParser> getParser(String extension) {
        return parsers.stream().filter(p -> p.supports(extension)).findFirst();
    }
}
