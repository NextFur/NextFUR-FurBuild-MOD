package net.nextfur.fwb.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class SplineMath {

    /**
     * Generates a smooth Catmull-Rom spline passing through all control points.
     *
     * @param waypointsAtBlockCenters Control points (BlockPos)
     * @param stepDistance            Approximate distance between sampled points along the curve
     * @return List of interpolated Vec3 points
     */
    public static List<Vec3> sampleCatmullRom(List<BlockPos> waypointsAtBlockCenters, double stepDistance) {
        List<Vec3> result = new ArrayList<>();
        if (waypointsAtBlockCenters == null || waypointsAtBlockCenters.isEmpty()) {
            return result;
        }

        List<Vec3> pts = waypointsAtBlockCenters.stream()
                .map(p -> new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5))
                .toList();

        if (pts.size() == 1) {
            result.add(pts.get(0));
            return result;
        }

        if (pts.size() == 2) {
            Vec3 a = pts.get(0);
            Vec3 b = pts.get(1);
            double dist = a.distanceTo(b);
            int steps = Math.max(2, (int) Math.ceil(dist / Math.max(0.1, stepDistance)));
            for (int i = 0; i <= steps; i++) {
                double t = (double) i / steps;
                result.add(a.lerp(b, t));
            }
            return result;
        }

        int n = pts.size();
        for (int i = 0; i < n - 1; i++) {
            Vec3 p0 = i == 0 ? pts.get(0) : pts.get(i - 1);
            Vec3 p1 = pts.get(i);
            Vec3 p2 = pts.get(i + 1);
            Vec3 p3 = (i + 2 < n) ? pts.get(i + 2) : pts.get(n - 1);

            double chord = p1.distanceTo(p2);
            int segments = Math.max(4, (int) Math.ceil(chord / Math.max(0.1, stepDistance)));
            int limit = (i == n - 2) ? segments : segments - 1;

            for (int s = 0; s <= limit; s++) {
                double t = (double) s / segments;
                result.add(catmullRomPoint(p0, p1, p2, p3, t));
            }
        }

        return result;
    }

    public static Vec3 catmullRomPoint(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;

        double x = 0.5 * ((2.0 * p1.x)
                + (-p0.x + p2.x) * t
                + (2.0 * p0.x - 5.0 * p1.x + 4.0 * p2.x - p3.x) * t2
                + (-p0.x + 3.0 * p1.x - 3.0 * p2.x + p3.x) * t3);

        double y = 0.5 * ((2.0 * p1.y)
                + (-p0.y + p2.y) * t
                + (2.0 * p0.y - 5.0 * p1.y + 4.0 * p2.y - p3.y) * t2
                + (-p0.y + 3.0 * p1.y - 3.0 * p2.y + p3.y) * t3);

        double z = 0.5 * ((2.0 * p1.z)
                + (-p0.z + p2.z) * t
                + (2.0 * p0.z - 5.0 * p1.z + 4.0 * p2.z - p3.z) * t2
                + (-p0.z + 3.0 * p1.z - 3.0 * p2.z + p3.z) * t3);

        return new Vec3(x, y, z);
    }
}
