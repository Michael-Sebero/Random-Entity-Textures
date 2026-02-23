package michaelsebero.randomentitytextures.mixin;

import michaelsebero.randomentitytextures.client.TrackedEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Mixes {@link TrackedEntity} into every {@link Entity}.
 *
 * WHY NO @Shadow:
 *   The original code declared @Shadow(remap = false) fields with SRG names
 *   (field_70165_t, field_70170_p, etc.). Although those field names DO exist
 *   on Entity at runtime, Mixin 0.7.x validates shadow declarations during
 *   class transformation using type data that is not always consistent under
 *   ForgeGradle 2.3, and can silently drop the mixin if validation fails.
 *
 * THE FIX — (Entity)(Object)this cast:
 *   We cast 'this' to Entity using the double-cast trick and access fields by
 *   their MCP names (posX, posY, posZ, world, getUniqueID). ForgeGradle's
 *   reobfuscation step remaps these to SRG names in the output jar, exactly
 *   as it does for every other field access in the codebase.  No @Shadow, no
 *   refmap, no Mixin annotation-processor involvement at all.
 *
 *   The (Entity)(Object) double cast satisfies the Java compiler (which sees
 *   'this' as EntityMixin and rejects a direct cast to an unrelated type).
 *   At runtime, after Mixin application, 'this' IS the Entity, so the cast
 *   never throws ClassCastException.
 */
@Mixin(Entity.class)
public abstract class EntityMixin implements TrackedEntity {

    @Unique private boolean mcp$init       = false;
    @Unique private int     mcp$initX      = 0;
    @Unique private int     mcp$initY      = 0;
    @Unique private int     mcp$initZ      = 0;
    @Unique private Biome   mcp$initBiome  = null;
    @Unique private int     mcp$initRandId = 0;

    @Unique
    private void mcp$ensureInit() {
        if (mcp$init) return;

        Entity self = (Entity)(Object) this;
        if (self.world == null) return;

        mcp$initX     = MathHelper.floor(self.posX);
        mcp$initY     = MathHelper.floor(self.posY);
        mcp$initZ     = MathHelper.floor(self.posZ);
        mcp$initBiome = self.world.getBiome(new BlockPos(mcp$initX, mcp$initY, mcp$initZ));

        long lsb      = self.getUniqueID().getLeastSignificantBits();
        mcp$initRandId = (int)(lsb & Integer.MAX_VALUE);

        mcp$init = true;
    }

    @Override public int   mcp$initialX()     { mcp$ensureInit(); return mcp$initX; }
    @Override public int   mcp$initialY()     { mcp$ensureInit(); return mcp$initY; }
    @Override public int   mcp$initialZ()     { mcp$ensureInit(); return mcp$initZ; }
    @Override public Biome mcp$initialBiome() { mcp$ensureInit(); return mcp$initBiome; }
    @Override public int   mcp$randomId()     { mcp$ensureInit(); return mcp$initRandId; }
}
