package com.mushroomrealm.client.bb;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Minimal runtime for Blockbench ".bbmodel" files (free/generic model format, cubes with per-face UV).
 * Supports groups (bones), cube rotation, and animations with position / rotation / scale keyframes
 * (linear, catmullrom and step interpolation). Coordinates are Blockbench pixels, 16 px = 1 block,
 * the model front is -Z (north) like in Blockbench.
 */
public final class BbModel {
	// face order: north, east, south, west, up, down
	private static final String[] FACE_NAMES = {"north", "east", "south", "west", "up", "down"};
	// vertex corner selectors: 0 = min, 1 = max  (x, y, z)  - same winding / UV orientation as vanilla block models
	private static final int[][][] FACE_VERTS = {
			{{1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0}}, // north
			{{1, 1, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 0}}, // east
			{{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}}, // south
			{{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}}, // west
			{{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}}, // up
			{{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}}  // down
	};
	private static final float[][] FACE_NORMALS = {
			{0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}
	};
	private static final int[] UV_U = {0, 0, 1, 1};
	private static final int[] UV_V = {0, 1, 1, 0};

	// ------------------------------------------------------------------ data classes

	private static final class Cube {
		final float[] from = new float[3];
		final float[] to = new float[3];
		final float[] origin = new float[3];
		final float[] rot = new float[3];
		boolean hasRot;
		final float[][] uv = new float[6][]; // u0, v0, u1, v1 in pixels, null = no face
	}

	private static final class Bone {
		final String name;
		final float[] origin;
		final float[] rot;
		final List<Bone> children = new ArrayList<>();
		final List<Cube> cubes = new ArrayList<>();

		Bone(String name, float[] origin, float[] rot) {
			this.name = name;
			this.origin = origin;
			this.rot = rot;
		}
	}

	private static final class Key {
		float time;
		final float[] v = new float[3];
		boolean catmull;
		boolean step;
	}

	private static final class Track {
		final List<Key> keys = new ArrayList<>();
	}

	private static final class BoneAnim {
		final Track pos = new Track();
		final Track rot = new Track();
		final Track scale = new Track();
	}

	public static final class Animation {
		public final String name;
		public final float length;
		public final boolean loop;
		private final Map<String, BoneAnim> bones = new HashMap<>();

		Animation(String name, float length, boolean loop) {
			this.name = name;
			this.length = length;
			this.loop = loop;
		}

		float wrap(float time) {
			if (this.length <= 0.0F) {
				return 0.0F;
			}
			if (this.loop) {
				float t = time % this.length;
				return t < 0 ? t + this.length : t;
			}
			return Math.max(0.0F, Math.min(time, this.length));
		}
	}

	/** Lets the renderer tweak a bone after the animation was sampled (head tracking etc). */
	public interface BoneHook {
		void apply(String boneName, float[] pos, float[] rot, float[] scale);
	}

	// ------------------------------------------------------------------ fields

	private final int texW;
	private final int texH;
	private final Bone root;
	private final Map<String, Animation> animations = new HashMap<>();

	private BbModel(int texW, int texH, Bone root) {
		this.texW = texW;
		this.texH = texH;
		this.root = root;
	}

	public static BbModel empty() {
		return new BbModel(16, 16, new Bone("__root", new float[3], new float[3]));
	}

	public Animation getAnimation(String name) {
		return name == null ? null : this.animations.get(name);
	}

	// ------------------------------------------------------------------ parsing

	public static BbModel parse(JsonObject json) {
		int w = 64;
		int h = 64;
		if (json.has("resolution")) {
			JsonObject res = json.getAsJsonObject("resolution");
			w = res.get("width").getAsInt();
			h = res.get("height").getAsInt();
		}

		Map<String, JsonObject> elements = new HashMap<>();
		for (JsonElement e : json.getAsJsonArray("elements")) {
			JsonObject o = e.getAsJsonObject();
			if (!o.has("type") || "cube".equals(o.get("type").getAsString())) {
				elements.put(o.get("uuid").getAsString(), o);
			}
		}

		Bone root = new Bone("__root", new float[3], new float[3]);
		BbModel model = new BbModel(w, h, root);
		Map<String, String> uuidToBone = new HashMap<>();
		parseChildren(json.getAsJsonArray("outliner"), root, elements, uuidToBone);

		if (json.has("animations")) {
			for (JsonElement ae : json.getAsJsonArray("animations")) {
				Animation anim = parseAnimation(ae.getAsJsonObject(), uuidToBone);
				model.animations.put(anim.name, anim);
			}
		}
		return model;
	}

	private static void parseChildren(JsonArray array, Bone parent, Map<String, JsonObject> elements, Map<String, String> uuidToBone) {
		if (array == null) {
			return;
		}
		for (JsonElement child : array) {
			if (child.isJsonPrimitive()) {
				JsonObject el = elements.get(child.getAsString());
				if (el != null) {
					parent.cubes.add(parseCube(el));
				}
			} else if (child.isJsonObject()) {
				JsonObject g = child.getAsJsonObject();
				String name = g.has("name") ? g.get("name").getAsString() : "group_" + uuidToBone.size();
				Bone bone = new Bone(name, floats(g.get("origin")), floats(g.get("rotation")));
				if (g.has("uuid")) {
					uuidToBone.put(g.get("uuid").getAsString(), name);
				}
				parent.children.add(bone);
				parseChildren(g.has("children") ? g.getAsJsonArray("children") : null, bone, elements, uuidToBone);
			}
		}
	}

	private static Cube parseCube(JsonObject o) {
		Cube c = new Cube();
		System.arraycopy(floats(o.get("from")), 0, c.from, 0, 3);
		System.arraycopy(floats(o.get("to")), 0, c.to, 0, 3);
		System.arraycopy(floats(o.get("origin")), 0, c.origin, 0, 3);
		if (o.has("rotation")) {
			System.arraycopy(floats(o.get("rotation")), 0, c.rot, 0, 3);
			c.hasRot = c.rot[0] != 0 || c.rot[1] != 0 || c.rot[2] != 0;
		}
		if (o.has("faces")) {
			JsonObject faces = o.getAsJsonObject("faces");
			for (int i = 0; i < 6; i++) {
				if (!faces.has(FACE_NAMES[i])) {
					continue;
				}
				JsonObject f = faces.getAsJsonObject(FACE_NAMES[i]);
				if (!f.has("uv") || !f.get("uv").isJsonArray()) {
					continue;
				}
				if (f.has("texture") && f.get("texture").isJsonNull()) {
					continue;
				}
				JsonArray uv = f.getAsJsonArray("uv");
				c.uv[i] = new float[]{num(uv.get(0)), num(uv.get(1)), num(uv.get(2)), num(uv.get(3))};
			}
		}
		return c;
	}

	private static Animation parseAnimation(JsonObject a, Map<String, String> uuidToBone) {
		String name = a.get("name").getAsString();
		float length = a.has("length") ? a.get("length").getAsFloat() : 1.0F;
		String loopMode = a.has("loop") ? a.get("loop").getAsString() : "once";
		Animation anim = new Animation(name, length, "loop".equals(loopMode));

		if (!a.has("animators")) {
			return anim;
		}
		JsonObject animators = a.getAsJsonObject("animators");
		for (Map.Entry<String, JsonElement> entry : animators.entrySet()) {
			String boneName = uuidToBone.get(entry.getKey());
			if (boneName == null) {
				continue;
			}
			JsonObject animator = entry.getValue().getAsJsonObject();
			if (!animator.has("keyframes")) {
				continue;
			}
			BoneAnim ba = anim.bones.computeIfAbsent(boneName, k -> new BoneAnim());
			for (JsonElement ke : animator.getAsJsonArray("keyframes")) {
				JsonObject kf = ke.getAsJsonObject();
				String channel = kf.get("channel").getAsString();
				Track track;
				switch (channel) {
					case "position" -> track = ba.pos;
					case "rotation" -> track = ba.rot;
					case "scale" -> track = ba.scale;
					default -> {
						continue;
					}
				}
				JsonArray points = kf.getAsJsonArray("data_points");
				if (points == null || points.size() == 0) {
					continue;
				}
				JsonObject dp = points.get(0).getAsJsonObject();
				Key key = new Key();
				key.time = kf.has("time") ? kf.get("time").getAsFloat() : 0.0F;
				key.v[0] = dp.has("x") ? num(dp.get("x")) : 0.0F;
				key.v[1] = dp.has("y") ? num(dp.get("y")) : 0.0F;
				key.v[2] = dp.has("z") ? num(dp.get("z")) : 0.0F;
				String interp = kf.has("interpolation") ? kf.get("interpolation").getAsString() : "linear";
				key.catmull = "catmullrom".equals(interp);
				key.step = "step".equals(interp);
				track.keys.add(key);
			}
			ba.pos.keys.sort(Comparator.comparingDouble(k -> k.time));
			ba.rot.keys.sort(Comparator.comparingDouble(k -> k.time));
			ba.scale.keys.sort(Comparator.comparingDouble(k -> k.time));
		}
		return anim;
	}

	private static float[] floats(JsonElement e) {
		float[] r = new float[3];
		if (e != null && e.isJsonArray()) {
			JsonArray arr = e.getAsJsonArray();
			for (int i = 0; i < 3 && i < arr.size(); i++) {
				r[i] = num(arr.get(i));
			}
		}
		return r;
	}

	private static float num(JsonElement e) {
		if (e == null || e.isJsonNull()) {
			return 0.0F;
		}
		try {
			return Float.parseFloat(e.getAsString().trim());
		} catch (Exception ex) {
			return 0.0F;
		}
	}

	// ------------------------------------------------------------------ sampling

	private static void sample(Track track, float t, float[] out) {
		List<Key> k = track.keys;
		int n = k.size();
		if (n == 0) {
			return;
		}
		if (n == 1 || t <= k.get(0).time) {
			System.arraycopy(k.get(0).v, 0, out, 0, 3);
			return;
		}
		if (t >= k.get(n - 1).time) {
			System.arraycopy(k.get(n - 1).v, 0, out, 0, 3);
			return;
		}
		int i = 0;
		while (i < n - 2 && k.get(i + 1).time <= t) {
			i++;
		}
		Key a = k.get(i);
		Key b = k.get(i + 1);
		float span = b.time - a.time;
		float f = span <= 0.0F ? 0.0F : (t - a.time) / span;
		if (a.step) {
			System.arraycopy(a.v, 0, out, 0, 3);
		} else if (a.catmull || b.catmull) {
			Key p0 = k.get(Math.max(i - 1, 0));
			Key p3 = k.get(Math.min(i + 2, n - 1));
			for (int ax = 0; ax < 3; ax++) {
				out[ax] = catmull(p0.v[ax], a.v[ax], b.v[ax], p3.v[ax], f);
			}
		} else {
			for (int ax = 0; ax < 3; ax++) {
				out[ax] = a.v[ax] + (b.v[ax] - a.v[ax]) * f;
			}
		}
	}

	private static float catmull(float p0, float p1, float p2, float p3, float t) {
		float t2 = t * t;
		float t3 = t2 * t;
		return 0.5F * ((2.0F * p1) + (-p0 + p2) * t + (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * t2 + (-p0 + 3.0F * p1 - 3.0F * p2 + p3) * t3);
	}

	// ------------------------------------------------------------------ rendering

	public void render(MatrixStack matrices, VertexConsumer vc, int light, int overlay, Animation anim, float time, BoneHook hook) {
		float t = anim == null ? 0.0F : anim.wrap(time);
		this.renderBone(this.root, new float[3], matrices, vc, light, overlay, anim, t, hook);
	}

	private void renderBone(Bone bone, float[] parentOrigin, MatrixStack m, VertexConsumer vc, int light, int overlay, Animation anim, float t, BoneHook hook) {
		float[] pos = {0.0F, 0.0F, 0.0F};
		float[] rot = {0.0F, 0.0F, 0.0F};
		float[] scale = {1.0F, 1.0F, 1.0F};
		if (anim != null) {
			BoneAnim ba = anim.bones.get(bone.name);
			if (ba != null) {
				sample(ba.pos, t, pos);
				sample(ba.rot, t, rot);
				sample(ba.scale, t, scale);
			}
		}
		if (hook != null) {
			hook.apply(bone.name, pos, rot, scale);
		}

		m.push();
		m.translate((bone.origin[0] - parentOrigin[0] + pos[0]) / 16.0F,
				(bone.origin[1] - parentOrigin[1] + pos[1]) / 16.0F,
				(bone.origin[2] - parentOrigin[2] + pos[2]) / 16.0F);
		float rx = bone.rot[0] + rot[0];
		float ry = bone.rot[1] + rot[1];
		float rz = bone.rot[2] + rot[2];
		if (rx != 0.0F || ry != 0.0F || rz != 0.0F) {
			m.multiply(new Quaternionf().rotationZYX(rz * RAD, ry * RAD, rx * RAD));
		}
		if (scale[0] != 1.0F || scale[1] != 1.0F || scale[2] != 1.0F) {
			m.scale(scale[0], scale[1], scale[2]);
		}

		for (Cube cube : bone.cubes) {
			this.renderCube(cube, bone, m, vc, light, overlay);
		}
		for (Bone child : bone.children) {
			this.renderBone(child, bone.origin, m, vc, light, overlay, anim, t, hook);
		}
		m.pop();
	}

	private static final float RAD = (float) (Math.PI / 180.0);

	private void renderCube(Cube c, Bone bone, MatrixStack m, VertexConsumer vc, int light, int overlay) {
		m.push();
		m.translate((c.origin[0] - bone.origin[0]) / 16.0F, (c.origin[1] - bone.origin[1]) / 16.0F, (c.origin[2] - bone.origin[2]) / 16.0F);
		if (c.hasRot) {
			m.multiply(new Quaternionf().rotationZYX(c.rot[2] * RAD, c.rot[1] * RAD, c.rot[0] * RAD));
		}
		MatrixStack.Entry entry = m.peek();
		Matrix4f pm = entry.getPositionMatrix();
		Matrix3f nm = entry.getNormalMatrix();

		float[][] corner = new float[2][3];
		for (int ax = 0; ax < 3; ax++) {
			corner[0][ax] = (Math.min(c.from[ax], c.to[ax]) - c.origin[ax]) / 16.0F;
			corner[1][ax] = (Math.max(c.from[ax], c.to[ax]) - c.origin[ax]) / 16.0F;
		}

		for (int f = 0; f < 6; f++) {
			float[] uv = c.uv[f];
			if (uv == null) {
				continue;
			}
			float[] n = FACE_NORMALS[f];
			float[] us = {uv[0] / this.texW, uv[2] / this.texW};
			float[] vs = {uv[1] / this.texH, uv[3] / this.texH};
			for (int v = 0; v < 4; v++) {
				int[] sel = FACE_VERTS[f][v];
				vc.vertex(pm, corner[sel[0]][0], corner[sel[1]][1], corner[sel[2]][2])
						.color(1.0F, 1.0F, 1.0F, 1.0F)
						.texture(us[UV_U[v]], vs[UV_V[v]])
						.overlay(overlay)
						.light(light)
						.normal(nm, n[0], n[1], n[2])
						.next();
			}
		}
		m.pop();
	}
}
