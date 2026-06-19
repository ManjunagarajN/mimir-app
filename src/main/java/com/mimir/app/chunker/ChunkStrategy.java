package com.mimir.app.chunker;

import java.util.List;

import com.mimir.app.domain.Chunk;

public interface ChunkStrategy {
    List<Chunk> chunk(String document);

    String getDescription();
}
