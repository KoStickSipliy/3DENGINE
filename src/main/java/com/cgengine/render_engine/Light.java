package com.cgengine.render_engine;

import com.cgengine.math.Vector3f;
import com.cgengine.math.VectorMath;

/** Fixed camera-space directional Lambert light defined by D-04. */
public final class Light {
    public static final Light CAMERA_DIRECTIONAL = new Light(new Vector3f(0, 0, -1), .15f);
    private final Vector3f direction;
    private final float ambient;
    public Light(Vector3f direction, float ambient) {
        if (direction == null || !VectorMath.isFinite(direction) || VectorMath.length(direction) == 0 || !Float.isFinite(ambient) || ambient < 0 || ambient > 1) throw new IllegalArgumentException("Light direction and ambient component must be valid");
        this.direction = VectorMath.normalize(direction); this.ambient = ambient;
    }
    public float brightness(Vector3f normal) { if (normal == null || !VectorMath.isFinite(normal) || VectorMath.length(normal) == 0) return ambient; return ambient + (1 - ambient) * Math.max(0, VectorMath.dot(VectorMath.normalize(normal), direction)); }
    public int shade(int argb, Vector3f normal) { return shade(argb, brightness(normal)); }
    public int shade(int argb, float factor) { return ((argb >>> 24) << 24) | (Math.round(((argb >>> 16) & 0xFF) * factor) << 16) | (Math.round(((argb >>> 8) & 0xFF) * factor) << 8) | Math.round((argb & 0xFF) * factor); }
}
