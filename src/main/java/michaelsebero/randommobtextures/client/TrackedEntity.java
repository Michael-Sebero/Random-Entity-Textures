package michaelsebero.randommobtextures.client;

import net.minecraft.world.biome.Biome;

/**
 * Mixed into {@link net.minecraft.entity.Entity} by
 * {@link michaelsebero.randommobtextures.mixin.EntityMixin}.
 *
 * Provides the values needed for texture-variant selection, all lazily
 * captured the first time any accessor is called (i.e. the first time the
 * entity is rendered).
 */
public interface TrackedEntity {
    /** Block X at (or near) spawn. */
    int mcp$initialX();

    /** Block Y at (or near) spawn. */
    int mcp$initialY();

    /** Block Z at (or near) spawn. */
    int mcp$initialZ();

    /** Biome at the spawn position, or {@code null} if the world is unavailable. */
    Biome mcp$initialBiome();

    /**
     * Stable non-negative integer derived from the entity's UUID.
     * Used as the seed for uniform texture-variant selection.
     */
    int mcp$randomId();
}
