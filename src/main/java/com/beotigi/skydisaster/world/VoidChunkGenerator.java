package com.beotigi.skydisaster.world;

import org.bukkit.generator.ChunkGenerator;

/**
 * 완전히 빈 월드 - 지형/동굴/구조물/장식/몹, 아무것도 자동 생성하지 않는다.
 * 섬은 전부 이 플러그인이 직접 짓는다.
 */
public class VoidChunkGenerator extends ChunkGenerator {

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateBedrock() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }
}
