// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.client;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

import io.github._5thlayer.voidworks.dragon.VoidDragon;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/**
 * How each Void Dragon's presence last drew what it hangs, from where it stands ({@link VoidDragonRenderer}):
 * where under its body, how big and turned which way, as its model posed it. An item a Matter-line dragon
 * is seen loading or unloading ({@link PassingParticle}) comes and goes there, drawn the same.
 */
final class HungPoses {

    /** Forgotten with the presence. */
    private static final Map<VoidDragon, Matrix4f> POSES = new WeakHashMap<>();

    private HungPoses() {
    }

    /** {@code dragon} drew what it hangs with {@code pose}, from where it stands. */
    static void drawn(VoidDragon dragon, Matrix4fc pose) {
        POSES.put(dragon, new Matrix4f(pose));
    }

    /** @return how {@code dragon} last drew what it hangs, from where it stands; empty until it is drawn */
    static Optional<Matrix4fc> of(VoidDragon dragon) {
        return Optional.ofNullable(POSES.get(dragon));
    }
}
