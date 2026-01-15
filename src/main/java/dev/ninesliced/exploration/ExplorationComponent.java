package dev.ninesliced.exploration;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.LongArrayCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import javax.annotation.Nonnull;

public class ExplorationComponent implements Component<EntityStore> {

    public static final BuilderCodec<ExplorationComponent> CODEC = BuilderCodec.builder(ExplorationComponent.class, ExplorationComponent::new)
            .addField(
                    new KeyedCodec<>("ExploredChunks", new LongArrayCodec()),
                    (component, chunks) -> {
                        if (chunks != null) {
                            component.exploredChunks = new LongOpenHashSet(chunks);
                        } else {
                            component.exploredChunks = new LongOpenHashSet();
                        }
                    },
                    component -> component.exploredChunks.toLongArray()
            )
            .build();

    private LongSet exploredChunks = new LongOpenHashSet();

    public ExplorationComponent() {
    }

    public LongSet getExploredChunks() {
        return exploredChunks;
    }

    public void addExploredChunk(long chunkIndex) {
        exploredChunks.add(chunkIndex);
    }

    public boolean isExplored(long chunkIndex) {
        return exploredChunks.contains(chunkIndex);
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        ExplorationComponent clone = new ExplorationComponent();
        clone.exploredChunks = new LongOpenHashSet(this.exploredChunks);
        return clone;
    }
}
