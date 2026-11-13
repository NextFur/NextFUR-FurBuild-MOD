package net.nextfur.fwb.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.nextfur.fwb.data.CityPlot;
import net.nextfur.fwb.data.PlayerSelectionData;
import net.nextfur.fwb.data.SelectionMode;
import net.nextfur.fwb.init.FwDataComponents;
import net.nextfur.fwb.items.GenerateWandItem;
import net.nextfur.fwb.items.SelectWandItem;
import net.nextfur.fwb.items.StyleWandItem;
import net.nextfur.fwb.util.SplineMath;
import org.joml.Matrix4f;

import java.util.List;

public class SelectionRenderer {

    public static void onRenderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (mc.level == null || player == null) {
            return;
        }

        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();

        boolean holdingStyleWand = mainHand.getItem() instanceof StyleWandItem || offHand.getItem() instanceof StyleWandItem;
        boolean holdingSelectWand = mainHand.getItem() instanceof SelectWandItem || offHand.getItem() instanceof SelectWandItem;
        boolean holdingGenerateWand = mainHand.getItem() instanceof GenerateWandItem || offHand.getItem() instanceof GenerateWandItem;

        if (!holdingStyleWand && !holdingSelectWand && !holdingGenerateWand) {
            return;
        }

        PlayerSelectionData selection = ClientSelectionHolder.get();
        SelectionMode activeMode = selection.mode();
        if ((holdingStyleWand || holdingSelectWand) && !holdingGenerateWand) {
            activeMode = SelectionMode.CUBOID;
        } else if (mainHand.getItem() instanceof GenerateWandItem) {
            activeMode = mainHand.getOrDefault(FwDataComponents.WAND_MODE, activeMode);
        } else if (offHand.getItem() instanceof GenerateWandItem) {
            activeMode = offHand.getOrDefault(FwDataComponents.WAND_MODE, activeMode);
        }

        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        switch (activeMode) {
            case CUBOID -> renderCuboid(poseStack, consumer, selection);
            case POLYGON -> renderPolygon(poseStack, consumer, selection);
            case CYLINDER -> renderCylinder(poseStack, consumer, selection);
            case SPLINE -> renderSpline(poseStack, consumer, selection);
        }

        // Render Select Wand object selection & mapped objects when holding Style Wand or Select Wand
        if (holdingStyleWand || holdingSelectWand) {
            renderSelectWandMapping(poseStack, consumer, selection);
        }

        // Render saved city plots if present
        if (!selection.savedCityPlots().isEmpty()) {
            for (CityPlot plot : selection.savedCityPlots()) {
                AABB plotBox = new AABB(
                        plot.minX(), plot.minY(), plot.minZ(),
                        plot.maxX() + 1.0, plot.maxY() + 1.0, plot.maxZ() + 1.0
                );
                LevelRenderer.renderLineBox(poseStack, consumer, plotBox, 0.7f, 0.35f, 1.0f, 0.75f);
            }
        }

        poseStack.popPose();
        bufferSource.endBatch(RenderType.lines());
    }

    private static void renderSelectWandMapping(PoseStack poseStack, VertexConsumer consumer, PlayerSelectionData selection) {
        if (selection.selectPosA().isPresent()) {
            BlockPos a = selection.selectPosA().get();
            LevelRenderer.renderLineBox(poseStack, consumer, new AABB(a).inflate(0.008), 0.1f, 0.95f, 1.0f, 0.95f);
        }
        if (selection.selectPosB().isPresent()) {
            BlockPos b = selection.selectPosB().get();
            LevelRenderer.renderLineBox(poseStack, consumer, new AABB(b).inflate(0.008), 1.0f, 0.2f, 0.95f, 0.95f);
        }
        if (selection.selectPosA().isPresent() && selection.selectPosB().isPresent()) {
            BoundingBox bb = BoundingBox.fromCorners(selection.selectPosA().get(), selection.selectPosB().get());
            AABB objBox = new AABB(
                    bb.minX(), bb.minY(), bb.minZ(),
                    bb.maxX() + 1.0, bb.maxY() + 1.0, bb.maxZ() + 1.0
            ).inflate(0.006);
            LevelRenderer.renderLineBox(poseStack, consumer, objBox, 0.95f, 0.25f, 0.95f, 0.95f);
        }
        for (CityPlot mapped : selection.mappedObjects()) {
            AABB mappedBox = new AABB(
                    mapped.minX(), mapped.minY(), mapped.minZ(),
                    mapped.maxX() + 1.0, mapped.maxY() + 1.0, mapped.maxZ() + 1.0
            ).inflate(0.004);
            LevelRenderer.renderLineBox(poseStack, consumer, mappedBox, 0.15f, 0.95f, 0.35f, 0.85f);
        }
    }

    private static void renderCuboid(PoseStack poseStack, VertexConsumer consumer, PlayerSelectionData selection) {
        if (selection.posA().isPresent()) {
            BlockPos a = selection.posA().get();
            AABB boxA = new AABB(a).inflate(0.005);
            LevelRenderer.renderLineBox(poseStack, consumer, boxA, 0.2f, 1.0f, 0.3f, 0.95f);
        }
        if (selection.posB().isPresent()) {
            BlockPos b = selection.posB().get();
            AABB boxB = new AABB(b).inflate(0.005);
            LevelRenderer.renderLineBox(poseStack, consumer, boxB, 0.2f, 0.8f, 1.0f, 0.95f);
        }
        if (selection.posA().isPresent() && selection.posB().isPresent()) {
            BoundingBox bb = BoundingBox.fromCorners(selection.posA().get(), selection.posB().get());
            AABB fullBox = new AABB(
                    bb.minX(), bb.minY(), bb.minZ(),
                    bb.maxX() + 1.0, bb.maxY() + 1.0, bb.maxZ() + 1.0
            ).inflate(0.002);
            LevelRenderer.renderLineBox(poseStack, consumer, fullBox, 1.0f, 0.85f, 0.2f, 0.9f);
        }
    }

    private static void renderPolygon(PoseStack poseStack, VertexConsumer consumer, PlayerSelectionData selection) {
        List<BlockPos> pts = selection.points();
        if (pts.isEmpty()) {
            return;
        }
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int i = 0; i < pts.size(); i++) {
            BlockPos p = pts.get(i);
            minY = Math.min(minY, p.getY());
            maxY = Math.max(maxY, p.getY());
            float r = i == 0 ? 0.2f : 0.2f;
            float g = i == 0 ? 1.0f : 0.75f;
            float b = i == 0 ? 0.3f : 1.0f;
            LevelRenderer.renderLineBox(poseStack, consumer, new AABB(p).inflate(0.004), r, g, b, 0.95f);
        }

        if (maxY == minY) {
            maxY = minY + 3; // Preview height if all polygon points are on the same Y plane
        }

        double y0 = minY;
        double y1 = maxY + 1.0;

        for (int i = 0; i < pts.size(); i++) {
            BlockPos curr = pts.get(i);
            BlockPos next = pts.get((i + 1) % pts.size());

            double x1 = curr.getX() + 0.5;
            double z1 = curr.getZ() + 0.5;
            double x2 = next.getX() + 0.5;
            double z2 = next.getZ() + 0.5;

            // Vertical pillar at vertex
            drawLine(poseStack, consumer, x1, y0, z1, x1, y1, z1, 1.0f, 0.8f, 0.2f, 0.9f);

            if (pts.size() >= 2 && (i < pts.size() - 1 || pts.size() >= 3)) {
                // Bottom edge
                drawLine(poseStack, consumer, x1, y0, z1, x2, y0, z2, 0.2f, 0.95f, 0.9f, 0.95f);
                // Top edge
                drawLine(poseStack, consumer, x1, y1, z1, x2, y1, z2, 1.0f, 0.85f, 0.2f, 0.9f);
            }
        }
    }

    private static void renderCylinder(PoseStack poseStack, VertexConsumer consumer, PlayerSelectionData selection) {
        if (selection.posA().isEmpty()) {
            return;
        }
        BlockPos center = selection.posA().get();
        LevelRenderer.renderLineBox(poseStack, consumer, new AABB(center).inflate(0.005), 0.2f, 1.0f, 0.3f, 0.95f);

        if (selection.posB().isEmpty()) {
            return;
        }
        BlockPos edge = selection.posB().get();
        LevelRenderer.renderLineBox(poseStack, consumer, new AABB(edge).inflate(0.005), 0.2f, 0.8f, 1.0f, 0.95f);

        double cx = center.getX() + 0.5;
        double cz = center.getZ() + 0.5;
        double dx = edge.getX() - center.getX();
        double dz = edge.getZ() - center.getZ();
        double radius = Math.max(1.0, Math.sqrt(dx * dx + dz * dz)) + 0.5;

        double y0 = Math.min(center.getY(), edge.getY());
        double y1 = Math.max(center.getY(), edge.getY()) + 1.0;

        // Radius line
        drawLine(poseStack, consumer, cx, y0 + 0.5, cz, edge.getX() + 0.5, y0 + 0.5, edge.getZ() + 0.5, 0.3f, 1.0f, 0.5f, 0.9f);

        int segments = 48;
        for (int i = 0; i < segments; i++) {
            double a1 = (2.0 * Math.PI * i) / segments;
            double a2 = (2.0 * Math.PI * (i + 1)) / segments;

            double x1 = cx + Math.cos(a1) * radius;
            double z1 = cz + Math.sin(a1) * radius;
            double x2 = cx + Math.cos(a2) * radius;
            double z2 = cz + Math.sin(a2) * radius;

            drawLine(poseStack, consumer, x1, y0, z1, x2, y0, z2, 0.2f, 0.9f, 1.0f, 0.9f);
            drawLine(poseStack, consumer, x1, y1, z1, x2, y1, z2, 1.0f, 0.85f, 0.2f, 0.9f);

            if (i % (segments / 8) == 0) {
                drawLine(poseStack, consumer, x1, y0, z1, x1, y1, z1, 0.9f, 0.7f, 0.2f, 0.8f);
            }
        }
    }

    private static void renderSpline(PoseStack poseStack, VertexConsumer consumer, PlayerSelectionData selection) {
        List<BlockPos> waypoints = selection.points();
        if (waypoints.isEmpty()) {
            return;
        }

        for (int i = 0; i < waypoints.size(); i++) {
            BlockPos wp = waypoints.get(i);
            float r = i == 0 ? 0.2f : 1.0f;
            float g = i == 0 ? 1.0f : 0.65f;
            float b = i == 0 ? 0.3f : 0.2f;
            LevelRenderer.renderLineBox(poseStack, consumer, new AABB(wp).inflate(0.005), r, g, b, 0.95f);
        }

        if (waypoints.size() >= 2) {
            List<Vec3> curve = SplineMath.sampleCatmullRom(waypoints, 0.35);
            for (int i = 0; i < curve.size() - 1; i++) {
                Vec3 p1 = curve.get(i);
                Vec3 p2 = curve.get(i + 1);
                drawLine(poseStack, consumer,
                        p1.x, p1.y + 0.5, p1.z,
                        p2.x, p2.y + 0.5, p2.z,
                        0.2f, 1.0f, 0.85f, 0.95f);
            }
        }
    }

    private static void drawLine(
            PoseStack poseStack,
            VertexConsumer consumer,
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            float r, float g, float b, float a
    ) {
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        float dx = (float) (x2 - x1);
        float dy = (float) (y2 - y1);
        float dz = (float) (z2 - z1);
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0e-5f) {
            dx = 1.0f;
            dy = 0.0f;
            dz = 0.0f;
        } else {
            dx /= len;
            dy /= len;
            dz /= len;
        }

        consumer.addVertex(matrix, (float) x1, (float) y1, (float) z1)
                .setColor(r, g, b, a)
                .setNormal(pose, dx, dy, dz);
        consumer.addVertex(matrix, (float) x2, (float) y2, (float) z2)
                .setColor(r, g, b, a)
                .setNormal(pose, dx, dy, dz);
    }
}
