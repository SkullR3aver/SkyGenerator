package pl.skygen.skygenerator;

import java.util.UUID;

public final class PlacedGenerator {
    public final BlockKey key;
    public final UUID owner;
    public final String typeId;
    public int stored;
    public int timer;

    public PlacedGenerator(BlockKey key, UUID owner, String typeId) {
        this.key = key;
        this.owner = owner;
        this.typeId = typeId;
    }
}
