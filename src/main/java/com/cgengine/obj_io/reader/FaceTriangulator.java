package com.cgengine.obj_io.reader;

import com.cgengine.math.Vector3f;
import com.cgengine.model.Model;
import com.cgengine.model.Polygon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Ear clipping in a face's dominant plane; only triangles enter the working model. */
final class FaceTriangulator {
    private FaceTriangulator() { }

    static List<Polygon> triangulate(Model model, List<Integer> vertices, List<Integer> textures,
                                     List<Integer> normals, int line) {
        int count = vertices.size();
        if (count == 3) return List.of(triangle(vertices, textures, normals, 0, 1, 2));
        if (new HashSet<>(vertices).size() != count) throw error(line);

        double nx = 0, ny = 0, nz = 0;
        for (int i = 0; i < count; i++) {
            Vector3f a = model.vertices.get(vertices.get(i));
            Vector3f b = model.vertices.get(vertices.get((i + 1) % count));
            nx += (double) (a.y() - b.y()) * (a.z() + b.z());
            ny += (double) (a.z() - b.z()) * (a.x() + b.x());
            nz += (double) (a.x() - b.x()) * (a.y() + b.y());
        }
        int axis = Math.abs(nx) >= Math.abs(ny) && Math.abs(nx) >= Math.abs(nz) ? 0
                : Math.abs(ny) >= Math.abs(nz) ? 1 : 2;
        double[] x = new double[count], y = new double[count];
        for (int i = 0; i < count; i++) {
            Vector3f v = model.vertices.get(vertices.get(i));
            x[i] = axis == 0 ? v.y() : v.x();
            y[i] = axis == 2 ? v.y() : v.z();
        }
        double area = 0;
        for (int i = 0; i < count; i++) {
            int next = (i + 1) % count;
            area += x[i] * y[next] - x[next] * y[i];
        }
        if (!Double.isFinite(area) || area == 0) throw error(line);
        for (int i = 0; i < count; i++) {
            int nextI = (i + 1) % count;
            for (int j = i + 1; j < count; j++) {
                int nextJ = (j + 1) % count;
                if (nextI == j || nextJ == i) continue;
                if (intersects(x, y, i, nextI, j, nextJ)) throw error(line);
            }
        }
        int winding = area > 0 ? 1 : -1;

        List<Integer> remaining = new ArrayList<>(count);
        for (int i = 0; i < count; i++) remaining.add(i);
        List<Polygon> result = new ArrayList<>(count - 2);
        while (remaining.size() > 3) {
            boolean clipped = false;
            for (int i = 0; i < remaining.size(); i++) {
                int prev = remaining.get((i + remaining.size() - 1) % remaining.size());
                int corner = remaining.get(i);
                int next = remaining.get((i + 1) % remaining.size());
                if (cross(x, y, prev, corner, next) * winding <= 0) continue;
                boolean occupied = false;
                for (int other : remaining) {
                    if (other != prev && other != corner && other != next
                            && inside(x, y, prev, corner, next, other, winding)) {
                        occupied = true;
                        break;
                    }
                }
                if (occupied) continue;
                result.add(triangle(vertices, textures, normals, prev, corner, next));
                remaining.remove(i);
                clipped = true;
                break;
            }
            if (!clipped) throw error(line);
        }
        result.add(triangle(vertices, textures, normals, remaining.get(0), remaining.get(1), remaining.get(2)));
        return result;
    }

    private static boolean inside(double[] x, double[] y, int a, int b, int c, int p, int winding) {
        return cross(x, y, a, b, p) * winding >= 0
                && cross(x, y, b, c, p) * winding >= 0
                && cross(x, y, c, a, p) * winding >= 0;
    }

    private static double cross(double[] x, double[] y, int a, int b, int c) {
        return (x[b] - x[a]) * (y[c] - y[a]) - (y[b] - y[a]) * (x[c] - x[a]);
    }

    private static boolean intersects(double[] x, double[] y, int a, int b, int c, int d) {
        double abC = cross(x, y, a, b, c), abD = cross(x, y, a, b, d);
        double cdA = cross(x, y, c, d, a), cdB = cross(x, y, c, d, b);
        if (abC == 0 && onSegment(x, y, a, b, c)) return true;
        if (abD == 0 && onSegment(x, y, a, b, d)) return true;
        if (cdA == 0 && onSegment(x, y, c, d, a)) return true;
        if (cdB == 0 && onSegment(x, y, c, d, b)) return true;
        return (abC > 0) != (abD > 0) && (cdA > 0) != (cdB > 0);
    }

    private static boolean onSegment(double[] x, double[] y, int a, int b, int p) {
        return x[p] >= Math.min(x[a], x[b]) && x[p] <= Math.max(x[a], x[b])
                && y[p] >= Math.min(y[a], y[b]) && y[p] <= Math.max(y[a], y[b]);
    }

    private static Polygon triangle(List<Integer> vertices, List<Integer> textures, List<Integer> normals,
                                    int a, int b, int c) {
        Polygon result = new Polygon();
        result.setVertexIndices(new ArrayList<>(List.of(vertices.get(a), vertices.get(b), vertices.get(c))));
        if (!textures.isEmpty()) result.setTextureVertexIndices(new ArrayList<>(List.of(textures.get(a), textures.get(b), textures.get(c))));
        if (!normals.isEmpty()) result.setNormalIndices(new ArrayList<>(List.of(normals.get(a), normals.get(b), normals.get(c))));
        return result;
    }

    private static ObjReaderException error(int line) {
        return new ObjReaderException("Face cannot be triangulated (degenerate or non-simple polygon)", line);
    }
}
