// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

/**
 * A grown Void Dragon's life stage, by the cargo it has unloaded over its life ({@link LifeStages}):
 * every dragon lives all four, in order, and dies once the last runs out.
 */
public enum LifeStage {

    YOUNG("voidworks.jade.dragon.young", 0.8F),
    ADULT("voidworks.jade.dragon.adult", 1.0F),
    SENIOR("voidworks.jade.dragon.senior", 1.1F),
    OLD("voidworks.jade.dragon.old", 1.2F);

    private final String key;
    private final float size;

    LifeStage(String key, float size) {
        this.key = key;
        this.size = size;
    }

    /** @return the translation key of its name, as Jade shows it */
    public String key() {
        return key;
    }

    /** @return how much its presence is scaled from a grown dragon's size */
    public float size() {
        return size;
    }
}
