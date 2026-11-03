package pl.skygen.skygenerator;

import org.bukkit.Material;

public record GeneratorType(
        String id,
        String name,
        Material block,
        Material product,
        int amount,
        int interval,
        int capacity,
        Material costMaterial,
        int costAmount,
        double armorPoints) {
}
