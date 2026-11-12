package net.nextfur.fwb.generator.engines;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.nextfur.fwb.data.PlayerSelectionData;
import net.nextfur.fwb.generator.BlockPlacementQueue;
import net.nextfur.fwb.generator.GenerationFlags;
import net.nextfur.fwb.generator.TemplatePlacementHelper;
import net.nextfur.fwb.style.BlockPalette;
import net.nextfur.fwb.style.ModuleCategory;
import net.nextfur.fwb.style.StyleDefinition;
import net.nextfur.fwb.style.StyleManager;
import net.nextfur.fwb.style.StyleModuleEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

public class DungeonGeneratorEngine {

    private static final BlockPalette DEFAULT_DUNGEON_PALETTE = new BlockPalette(
            "painting",
            Map.of(
                    ResourceLocation.withDefaultNamespace("stone_bricks"), 0.50,
                    ResourceLocation.withDefaultNamespace("mossy_stone_bricks"), 0.25,
                    ResourceLocation.withDefaultNamespace("cracked_stone_bricks"), 0.20,
                    ResourceLocation.withDefaultNamespace("deepslate_bricks"), 0.05
            )
    );

    private record DungeonRoomNode(
            int id,
            BoundingBox bounds,
            BlockPos center,
            Optional<StyleModuleEntry> moduleEntry
    ) {
        boolean intersectsWithPadding(BoundingBox other, int pad) {
            return bounds.minX() - pad <= other.maxX() && bounds.maxX() + pad >= other.minX()
                    && bounds.minY() - 1 <= other.maxY() && bounds.maxY() + 1 >= other.minY()
                    && bounds.minZ() - pad <= other.maxZ() && bounds.maxZ() + pad >= other.minZ();
        }
    }

    private record GraphEdge(int u, int v, double weight) {
    }

    private record AStarNode(BlockPos pos, double gScore, double fScore) implements Comparable<AStarNode> {
        @Override
        public int compareTo(AStarNode other) {
            return Double.compare(this.fScore, other.fScore);
        }
    }

    public static List<BlockPlacementQueue.PlannedBlock> generate(
            ServerPlayer player,
            ServerLevel level,
            PlayerSelectionData selection,
            StyleDefinition style,
            GenerationFlags flags,
            RandomSource random
    ) {
        Optional<BoundingBox> boxOpt = selection.getBoundingBox();
        if (boxOpt.isEmpty()) {
            return List.of();
        }

        BoundingBox outerBox = boxOpt.get();
        BlockPlacementQueue.Builder builder = new BlockPlacementQueue.Builder();

        Optional<BlockPalette> paintingOpt = StyleManager.loadPalette(level.getServer(), style.styleName(), "painting");
        BlockPalette dungeonPalette = paintingOpt.orElse(DEFAULT_DUNGEON_PALETTE);

        // 1. Room Scatter: Randomly scatter non-overlapping dungeon_room modules inside the 3D selection box
        List<DungeonRoomNode> rooms = scatterRooms(outerBox, style, flags, random);
        if (rooms.isEmpty()) {
            return List.of();
        }

        // Build each scattered room
        for (DungeonRoomNode room : rooms) {
            buildDungeonRoom(level, room, style, paintingOpt, dungeonPalette, flags, random, builder);
        }

        // 2. Graph Connection: Delaunay / Gabriel Triangulation + Minimum Spanning Tree (MST)
        if (rooms.size() >= 2) {
            List<GraphEdge> edges = computeDelaunayAndMstEdges(rooms, random);

            // 3. A* Pathfinding Corridors between connected rooms
            for (GraphEdge edge : edges) {
                DungeonRoomNode roomA = rooms.get(edge.u());
                DungeonRoomNode roomB = rooms.get(edge.v());
                BlockPos startDoor = findDoorwayOnBoundary(roomA, roomB.center());
                BlockPos endDoor = findDoorwayOnBoundary(roomB, roomA.center());

                List<BlockPos> path = findAStarPath3D(startDoor, endDoor, outerBox, rooms, roomA.id(), roomB.id());
                carveAndPlaceCorridor(level, path, startDoor, endDoor, style, paintingOpt, dungeonPalette, flags, random, builder);
            }
        }

        return builder.buildList();
    }

    private static List<DungeonRoomNode> scatterRooms(
            BoundingBox outerBox,
            StyleDefinition style,
            GenerationFlags flags,
            RandomSource random
    ) {
        List<DungeonRoomNode> rooms = new ArrayList<>();
        int spanX = outerBox.getXSpan();
        int spanY = outerBox.getYSpan();
        int spanZ = outerBox.getZSpan();

        int maxAttempts = 60;
        int targetRooms = Math.max(3, Math.min(14, (spanX * spanZ) / 140));

        for (int attempt = 0; attempt < maxAttempts && rooms.size() < targetRooms; attempt++) {
            Optional<StyleModuleEntry> roomMod = flags.isExcluded(ModuleCategory.DUNGEON_ROOM)
                    ? Optional.empty()
                    : style.pickWeightedModule(ModuleCategory.DUNGEON_ROOM, random);

            int rw = roomMod.map(m -> Math.max(5, m.size().getX())).orElseGet(() -> 7 + random.nextInt(5));
            int rh = roomMod.map(m -> Math.max(4, m.size().getY())).orElse(5);
            int rl = roomMod.map(m -> Math.max(5, m.size().getZ())).orElseGet(() -> 7 + random.nextInt(5));

            if (rw >= spanX - 2) rw = Math.max(5, spanX - 2);
            if (rl >= spanZ - 2) rl = Math.max(5, spanZ - 2);
            if (rh > spanY) rh = Math.max(4, spanY);

            int maxOffsetX = Math.max(1, spanX - rw);
            int maxOffsetY = Math.max(1, spanY - rh);
            int maxOffsetZ = Math.max(1, spanZ - rl);

            int rx = outerBox.minX() + random.nextInt(maxOffsetX);
            int ry = outerBox.minY() + (maxOffsetY > 1 ? (random.nextInt(maxOffsetY) / 4) * 4 : 0);
            int rz = outerBox.minZ() + random.nextInt(maxOffsetZ);

            BoundingBox candidate = new BoundingBox(rx, ry, rz, rx + rw - 1, ry + rh - 1, rz + rl - 1);

            boolean overlaps = false;
            for (DungeonRoomNode existing : rooms) {
                if (existing.intersectsWithPadding(candidate, 3)) {
                    overlaps = true;
                    break;
                }
            }

            if (!overlaps) {
                BlockPos center = new BlockPos(
                        (candidate.minX() + candidate.maxX()) / 2,
                        candidate.minY() + 1,
                        (candidate.minZ() + candidate.maxZ()) / 2
                );
                rooms.add(new DungeonRoomNode(rooms.size(), candidate, center, roomMod));
            }
        }

        return rooms;
    }

    private static void buildDungeonRoom(
            ServerLevel level,
            DungeonRoomNode room,
            StyleDefinition style,
            Optional<BlockPalette> paintingOpt,
            BlockPalette dungeonPalette,
            GenerationFlags flags,
            RandomSource random,
            BlockPlacementQueue.Builder builder
    ) {
        BoundingBox b = room.bounds();
        if (room.moduleEntry().isPresent()) {
            TemplatePlacementHelper.placeModuleIntoBuilder(
                    level,
                    style.styleName(),
                    room.moduleEntry().get(),
                    new BlockPos(b.minX(), b.minY(), b.minZ()),
                    Rotation.NONE,
                    Mirror.NONE,
                    paintingOpt,
                    random,
                    builder
            );
            return;
        }

        // Procedural Dungeon Room (walls, floor, ceiling, corner pillars, lighting, and interior_prop)
        for (int y = b.minY(); y <= b.maxY(); y++) {
            for (int x = b.minX(); x <= b.maxX(); x++) {
                for (int z = b.minZ(); z <= b.maxZ(); z++) {
                    boolean isBoundary = (x == b.minX() || x == b.maxX() || z == b.minZ() || z == b.maxZ()
                            || y == b.minY() || y == b.maxY());
                    BlockPos pos = new BlockPos(x, y, z);
                    if (isBoundary) {
                        builder.setBlock(pos, dungeonPalette.sample(random, Blocks.STONE_BRICKS.defaultBlockState()));
                    } else {
                        builder.setBlock(pos, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }

        // Hanging lantern in center ceiling
        if (b.maxY() - b.minY() >= 3) {
            BlockPos lanternPos = new BlockPos(room.center().getX(), b.maxY() - 1, room.center().getZ());
            builder.setBlock(lanternPos, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }

        // Interior Prop inside room
        if (!flags.isExcluded(ModuleCategory.INTERIOR_PROP)) {
            Optional<StyleModuleEntry> propMod = style.pickWeightedModule(ModuleCategory.INTERIOR_PROP, random);
            if (propMod.isPresent()) {
                TemplatePlacementHelper.placeModuleIntoBuilder(
                        level, style.styleName(), propMod.get(),
                        room.center(), Rotation.NONE, Mirror.NONE, paintingOpt, random, builder
                );
            } else {
                BlockState lootOrProp = random.nextBoolean()
                        ? Blocks.CHEST.defaultBlockState()
                        : Blocks.BARREL.defaultBlockState();
                builder.setBlock(room.center(), lootOrProp);
            }
        }
    }

    /**
     * Computes Delaunay / Gabriel graph edges between rooms and extracts the Minimum Spanning Tree (MST)
     * plus a small percentage of extra loop edges.
     */
    private static List<GraphEdge> computeDelaunayAndMstEdges(List<DungeonRoomNode> rooms, RandomSource random) {
        int n = rooms.size();
        List<GraphEdge> delaunayEdges = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            BlockPos a = rooms.get(i).center();
            for (int j = i + 1; j < n; j++) {
                BlockPos b = rooms.get(j).center();
                double distSq = a.distSqr(b);

                // Check Gabriel / Delaunay empty sphere condition:
                // Midpoint between A and B has no other room center closer than dist(A,B)/2
                double midX = (a.getX() + b.getX()) * 0.5;
                double midY = (a.getY() + b.getY()) * 0.5;
                double midZ = (a.getZ() + b.getZ()) * 0.5;
                double radiusSq = distSq * 0.25;

                boolean emptySphere = true;
                for (int k = 0; k < n; k++) {
                    if (k == i || k == j) continue;
                    BlockPos c = rooms.get(k).center();
                    double dx = c.getX() - midX;
                    double dy = c.getY() - midY;
                    double dz = c.getZ() - midZ;
                    if (dx * dx + dy * dy + dz * dz < radiusSq * 0.95) {
                        emptySphere = false;
                        break;
                    }
                }

                if (emptySphere) {
                    delaunayEdges.add(new GraphEdge(i, j, Math.sqrt(distSq)));
                }
            }
        }

        // Ensure complete fallback graph if collinear
        if (delaunayEdges.size() < n - 1) {
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    double d = Math.sqrt(rooms.get(i).center().distSqr(rooms.get(j).center()));
                    delaunayEdges.add(new GraphEdge(i, j, d));
                }
            }
        }

        delaunayEdges.sort(Comparator.comparingDouble(GraphEdge::weight));

        // Kruskal's Minimum Spanning Tree (MST)
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;

        List<GraphEdge> selectedEdges = new ArrayList<>();
        List<GraphEdge> unusedDelaunay = new ArrayList<>();

        for (GraphEdge edge : delaunayEdges) {
            int rootU = findRoot(parent, edge.u());
            int rootV = findRoot(parent, edge.v());
            if (rootU != rootV) {
                parent[rootU] = rootV;
                selectedEdges.add(edge);
            } else {
                unusedDelaunay.add(edge);
            }
        }

        // Add ~15% extra Delaunay edges so the dungeon has occasional loops
        for (GraphEdge extra : unusedDelaunay) {
            if (random.nextDouble() < 0.15) {
                selectedEdges.add(extra);
            }
        }

        return selectedEdges;
    }

    private static int findRoot(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static BlockPos findDoorwayOnBoundary(DungeonRoomNode room, BlockPos target) {
        BoundingBox b = room.bounds();
        int dx = target.getX() - room.center().getX();
        int dz = target.getZ() - room.center().getZ();
        int y = b.minY() + 1;

        if (Math.abs(dx) >= Math.abs(dz)) {
            int doorX = dx >= 0 ? b.maxX() : b.minX();
            return new BlockPos(doorX, y, room.center().getZ());
        } else {
            int doorZ = dz >= 0 ? b.maxZ() : b.minZ();
            return new BlockPos(room.center().getX(), y, doorZ);
        }
    }

    private static List<BlockPos> findAStarPath3D(
            BlockPos start,
            BlockPos goal,
            BoundingBox bounds,
            List<DungeonRoomNode> rooms,
            int roomAId,
            int roomBId
    ) {
        PriorityQueue<AStarNode> openSet = new PriorityQueue<>();
        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        Map<BlockPos, Double> gScores = new HashMap<>();
        Set<BlockPos> closedSet = new HashSet<>();

        openSet.add(new AStarNode(start, 0.0, heuristic(start, goal)));
        gScores.put(start, 0.0);

        int[][] dirs = {
                {1, 0, 0}, {-1, 0, 0},
                {0, 0, 1}, {0, 0, -1},
                {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1},
                {1, -1, 0}, {-1, -1, 0}, {0, -1, 1}, {0, -1, -1}
        };

        int maxIterations = 4000;
        int iter = 0;

        while (!openSet.isEmpty() && iter++ < maxIterations) {
            AStarNode current = openSet.poll();
            if (current.pos().equals(goal) || current.pos().distManhattan(goal) <= 1) {
                return reconstructPath(cameFrom, current.pos(), goal);
            }

            if (!closedSet.add(current.pos())) {
                continue;
            }

            for (int[] d : dirs) {
                BlockPos neighbor = current.pos().offset(d[0], d[1], d[2]);
                if (!bounds.isInside(neighbor) || closedSet.contains(neighbor)) {
                    continue;
                }

                // Penalize cutting through unrelated rooms and vertical climbs
                double moveCost = (d[1] == 0) ? 1.0 : 2.5;
                for (DungeonRoomNode r : rooms) {
                    if (r.id() != roomAId && r.id() != roomBId && r.bounds().isInside(neighbor)) {
                        moveCost += 12.0;
                    }
                }

                double tentativeG = current.gScore() + moveCost;
                if (tentativeG < gScores.getOrDefault(neighbor, Double.POSITIVE_INFINITY)) {
                    cameFrom.put(neighbor, current.pos());
                    gScores.put(neighbor, tentativeG);
                    openSet.add(new AStarNode(neighbor, tentativeG, tentativeG + heuristic(neighbor, goal)));
                }
            }
        }

        // Manhattan L-corridor fallback if A* budget exceeded
        List<BlockPos> fallback = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = start.mutable();
        fallback.add(cursor.immutable());
        while (cursor.getX() != goal.getX()) {
            cursor.move(Integer.compare(goal.getX(), cursor.getX()), 0, 0);
            fallback.add(cursor.immutable());
        }
        while (cursor.getY() != goal.getY()) {
            cursor.move(0, Integer.compare(goal.getY(), cursor.getY()), 0);
            fallback.add(cursor.immutable());
        }
        while (cursor.getZ() != goal.getZ()) {
            cursor.move(0, 0, Integer.compare(goal.getZ(), cursor.getZ()));
            fallback.add(cursor.immutable());
        }
        return fallback;
    }

    private static double heuristic(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) * 2.0 + Math.abs(a.getZ() - b.getZ());
    }

    private static List<BlockPos> reconstructPath(Map<BlockPos, BlockPos> cameFrom, BlockPos current, BlockPos goal) {
        List<BlockPos> path = new ArrayList<>();
        if (!current.equals(goal)) {
            path.add(goal);
        }
        BlockPos cursor = current;
        while (cursor != null) {
            path.add(0, cursor);
            cursor = cameFrom.get(cursor);
        }
        return path;
    }

    private static void carveAndPlaceCorridor(
            ServerLevel level,
            List<BlockPos> path,
            BlockPos startDoor,
            BlockPos endDoor,
            StyleDefinition style,
            Optional<BlockPalette> paintingOpt,
            BlockPalette dungeonPalette,
            GenerationFlags flags,
            RandomSource random,
            BlockPlacementQueue.Builder builder
    ) {
        Optional<StyleModuleEntry> corridorMod = flags.isExcluded(ModuleCategory.DUNGEON_CORRIDOR)
                ? Optional.empty()
                : style.pickWeightedModule(ModuleCategory.DUNGEON_CORRIDOR, random);

        int grid = Math.max(3, style.gridSize());

        // Step 1: Place corridor shell blocks around the path
        for (int idx = 0; idx < path.size(); idx++) {
            BlockPos p = path.get(idx);

            if (corridorMod.isPresent() && idx % grid == 0) {
                Rotation rot = Rotation.NONE;
                if (idx + 1 < path.size()) {
                    BlockPos next = path.get(idx + 1);
                    if (Math.abs(next.getX() - p.getX()) > Math.abs(next.getZ() - p.getZ())) {
                        rot = Rotation.CLOCKWISE_90;
                    }
                }
                TemplatePlacementHelper.placeModuleIntoBuilder(
                        level, style.styleName(), corridorMod.get(),
                        p.offset(-1, -1, -1), rot, Mirror.NONE, paintingOpt, random, builder
                );
            } else {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int dy = -1; dy <= 3; dy++) {
                            BlockPos pos = p.offset(dx, dy, dz);
                            boolean isShell = (dy == -1 || dy == 3 || Math.abs(dx) == 1 || Math.abs(dz) == 1);
                            if (isShell) {
                                builder.setBlock(pos, dungeonPalette.sample(random, Blocks.STONE_BRICKS.defaultBlockState()));
                            }
                        }
                    }
                }
            }
        }

        // Step 2: Carve interior walk space along the entire A* path so corridors are guaranteed open
        for (BlockPos p : path) {
            for (int dy = 0; dy <= 2; dy++) {
                builder.setBlock(p.above(dy), Blocks.AIR.defaultBlockState());
            }
        }

        // Step 3: Place door / archway bars at room entrances if not excluded
        if (!flags.isExcluded(ModuleCategory.DOOR)) {
            Optional<StyleModuleEntry> doorMod = style.pickWeightedModule(ModuleCategory.DOOR, random);
            if (doorMod.isPresent()) {
                TemplatePlacementHelper.placeModuleIntoBuilder(
                        level, style.styleName(), doorMod.get(),
                        startDoor, Rotation.NONE, Mirror.NONE, paintingOpt, random, builder
                );
            } else {
                builder.setBlock(startDoor.above(2), Blocks.IRON_BARS.defaultBlockState());
                builder.setBlock(endDoor.above(2), Blocks.IRON_BARS.defaultBlockState());
            }
        }
    }
}
