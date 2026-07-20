package org.com.bloodpalace.worldgen.prefab;

import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public record PrefabSection(int sectionY, List<BlockState> palette, int bitsPerEntry, long[] data) {
    public BlockState stateAt(int x, int y, int z) {
        int linearIndex = (y << 8) | (z << 4) | x;
        int entriesPerLong = 64 / bitsPerEntry;
        int longIndex = linearIndex / entriesPerLong;
        int bitOffset = (linearIndex % entriesPerLong) * bitsPerEntry;
        int paletteIndex = (int) ((data[longIndex] >>> bitOffset) & ((1L << bitsPerEntry) - 1L));
        return palette.get(paletteIndex);
    }

    public int estimatedBytes() {
        return 64 + palette.size() * 48 + data.length * Long.BYTES;
    }
}
