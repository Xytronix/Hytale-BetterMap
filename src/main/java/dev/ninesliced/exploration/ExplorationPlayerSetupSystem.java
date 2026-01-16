package dev.ninesliced.exploration;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ninesliced.BetterMap;
import dev.ninesliced.components.ExplorationComponent;

import javax.annotation.Nonnull;

/**
 * specific System that attaches the ExplorationComponent to players.
 */
public class ExplorationPlayerSetupSystem extends RefSystem<EntityStore> {

    @Nonnull
    private final ComponentType<EntityStore, ExplorationComponent> explorationComponentType;
    @Nonnull
    private final ComponentType<EntityStore, Player> playerComponentType;
    @Nonnull
    private final Query<EntityStore> query;

    /**
     * Constructs the setup system.
     */
    public ExplorationPlayerSetupSystem() {
        this.explorationComponentType = BetterMap.get().getExplorationComponentType();
        this.playerComponentType = Player.getComponentType();
        this.query = Query.and(playerComponentType);
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return this.query;
    }

    /**
     * Called when a player entity is added. Ensures they have the ExplorationComponent.
     *
     * @param ref           The entity reference.
     * @param reason        Reason for addition.
     * @param store         The entity store.
     * @param commandBuffer The command buffer.
     */
    @Override
    public void onEntityAdded(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull AddReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        commandBuffer.ensureComponent(ref, this.explorationComponentType);
    }

    @Override
    public void onEntityRemove(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull RemoveReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
    }
}
