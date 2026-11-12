package net.nextfur.fwb.generator;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BlockPlacementQueue {
    public static final int BLOCKS_PER_TICK = 500;
    private static final int MAX_UNDO_HISTORY = 15;

    public record PlannedBlock(
            BlockPos pos,
            BlockState state,
            Optional<CompoundTag> blockEntityTag
    ) {
        public PlannedBlock(BlockPos pos, BlockState state) {
            this(pos.immutable(), state, Optional.empty());
        }
    }

    public record UndoSnapshot(
            String description,
            ResourceKey<Level> dimension,
            List<PlannedBlock> previousStates
    ) {
    }

    private static class ActiveJob {
        final UUID playerId;
        final ResourceKey<Level> dimension;
        final String description;
        final GenerationFlags flags;
        final Queue<PlannedBlock> queue;
        final List<PlannedBlock> recordedUndo;
        final boolean isUndoJob;
        int placedCount = 0;

        ActiveJob(
                UUID playerId,
                ResourceKey<Level> dimension,
                String description,
                GenerationFlags flags,
                List<PlannedBlock> blocks,
                boolean isUndoJob
        ) {
            this.playerId = playerId;
            this.dimension = dimension;
            this.description = description;
            this.flags = flags;
            this.queue = new ArrayDeque<>(blocks);
            this.recordedUndo = new ArrayList<>(blocks.size());
            this.isUndoJob = isUndoJob;
        }
    }

    private static final List<ActiveJob> ACTIVE_JOBS = Collections.synchronizedList(new ArrayList<>());
    private static final Map<UUID, Deque<UndoSnapshot>> UNDO_HISTORY = new ConcurrentHashMap<>();

    public static class Builder {
        private final Map<BlockPos, PlannedBlock> staged = new LinkedHashMap<>();

        public void setBlock(BlockPos pos, BlockState state) {
            staged.put(pos.immutable(), new PlannedBlock(pos.immutable(), state, Optional.empty()));
        }

        public void setBlock(BlockPos pos, BlockState state, CompoundTag blockEntityTag) {
            staged.put(pos.immutable(), new PlannedBlock(pos.immutable(), state, Optional.ofNullable(blockEntityTag)));
        }

        public int size() {
            return staged.size();
        }

        public List<PlannedBlock> buildList() {
            return new ArrayList<>(staged.values());
        }
    }

    public static void enqueueJob(
            ServerPlayer player,
            ServerLevel level,
            String description,
            GenerationFlags flags,
            List<PlannedBlock> blocks
    ) {
        if (blocks.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.furbuild.generate.empty").withStyle(ChatFormatting.YELLOW));
            return;
        }
        ActiveJob job = new ActiveJob(player.getUUID(), level.dimension(), description, flags, blocks, false);
        ACTIVE_JOBS.add(job);
        player.sendSystemMessage(
                Component.translatable("message.furbuild.generate.queued",
                        blocks.size(),
                        Component.literal(description).withStyle(ChatFormatting.AQUA)
                ).withStyle(ChatFormatting.GREEN)
        );
    }

    public static boolean enqueueUndo(ServerPlayer player) {
        Deque<UndoSnapshot> history = UNDO_HISTORY.get(player.getUUID());
        if (history == null || history.isEmpty()) {
            return false;
        }
        UndoSnapshot snapshot = history.pop();
        ServerLevel targetLevel = player.server.getLevel(snapshot.dimension());
        if (targetLevel == null) {
            return false;
        }

        List<PlannedBlock> reversed = new ArrayList<>(snapshot.previousStates());
        Collections.reverse(reversed);

        ActiveJob undoJob = new ActiveJob(
                player.getUUID(),
                snapshot.dimension(),
                "Undo: " + snapshot.description(),
                GenerationFlags.DEFAULT,
                reversed,
                true
        );
        ACTIVE_JOBS.add(undoJob);
        player.sendSystemMessage(
                Component.translatable("message.furbuild.undo.started",
                        reversed.size(),
                        Component.literal(snapshot.description()).withStyle(ChatFormatting.AQUA)
                ).withStyle(ChatFormatting.GOLD)
        );
        return true;
    }

    public static void tick(MinecraftServer server) {
        if (ACTIVE_JOBS.isEmpty()) {
            return;
        }

        synchronized (ACTIVE_JOBS) {
            Iterator<ActiveJob> iterator = ACTIVE_JOBS.iterator();
            while (iterator.hasNext()) {
                ActiveJob job = iterator.next();
                ServerLevel level = server.getLevel(job.dimension);
                if (level == null) {
                    iterator.remove();
                    continue;
                }

                int budget = BLOCKS_PER_TICK;
                BlockPos lastPlacedPos = null;
                BlockState lastPlacedState = null;

                while (budget > 0 && !job.queue.isEmpty()) {
                    PlannedBlock next = job.queue.poll();
                    budget--;

                    BlockPos pos = next.pos();
                    if (level.isOutsideBuildHeight(pos)) {
                        continue;
                    }

                    BlockState existing = level.getBlockState(pos);
                    if (!job.isUndoJob && !job.flags.canOverwrite(existing)) {
                        continue;
                    }

                    if (existing.equals(next.state()) && next.blockEntityTag().isEmpty()) {
                        continue;
                    }

                    if (!job.isUndoJob) {
                        CompoundTag oldBeTag = null;
                        BlockEntity existingBe = level.getBlockEntity(pos);
                        if (existingBe != null) {
                            oldBeTag = existingBe.saveWithFullMetadata(level.registryAccess());
                        }
                        job.recordedUndo.add(new PlannedBlock(pos, existing, Optional.ofNullable(oldBeTag)));
                    }

                    level.setBlock(pos, next.state(), Block.UPDATE_CLIENTS);
                    if (next.blockEntityTag().isPresent()) {
                        BlockEntity newBe = level.getBlockEntity(pos);
                        if (newBe != null) {
                            newBe.loadWithComponents(next.blockEntityTag().get(), level.registryAccess());
                            newBe.setChanged();
                        }
                    }

                    job.placedCount++;
                    lastPlacedPos = pos;
                    lastPlacedState = next.state();

                    // Spawn subtle placement particles periodically without flooding packets
                    if (job.placedCount % 25 == 0 && !next.state().isAir()) {
                        level.sendParticles(
                                new BlockParticleOption(ParticleTypes.BLOCK, next.state()),
                                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                                4, 0.25, 0.25, 0.25, 0.05
                        );
                    }
                }

                if (lastPlacedPos != null && lastPlacedState != null) {
                    SoundType soundType = lastPlacedState.getSoundType(level, lastPlacedPos, null);
                    level.playSound(
                            null,
                            lastPlacedPos,
                            soundType.getPlaceSound(),
                            SoundSource.BLOCKS,
                            (soundType.getVolume() + 1.0F) / 2.5F,
                            soundType.getPitch() * 0.9F
                    );
                }

                if (job.queue.isEmpty()) {
                    iterator.remove();
                    if (!job.isUndoJob && !job.recordedUndo.isEmpty()) {
                        Deque<UndoSnapshot> stack = UNDO_HISTORY.computeIfAbsent(job.playerId, k -> new ArrayDeque<>());
                        stack.push(new UndoSnapshot(job.description, job.dimension, job.recordedUndo));
                        while (stack.size() > MAX_UNDO_HISTORY) {
                            stack.removeLast();
                        }
                    }

                    ServerPlayer player = server.getPlayerList().getPlayer(job.playerId);
                    if (player != null) {
                        String key = job.isUndoJob
                                ? "message.furbuild.undo.completed"
                                : "message.furbuild.generate.completed";
                        player.sendSystemMessage(
                                Component.translatable(key,
                                        job.placedCount,
                                        Component.literal(job.description).withStyle(ChatFormatting.AQUA)
                                ).withStyle(ChatFormatting.GREEN)
                        );
                    }
                }
            }
        }
    }

    public static void clearAll() {
        ACTIVE_JOBS.clear();
        UNDO_HISTORY.clear();
    }
}
