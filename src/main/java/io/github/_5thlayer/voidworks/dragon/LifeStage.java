// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * A grown Void Dragon's life stage, by the cargo it has unloaded over its life ({@link LifeStages}):
 * every dragon lives all four, in order, and dies once the last runs out.
 */
public enum LifeStage {

    YOUNG,
    ADULT,
    SENIOR,
    OLD;

    /** @return this stage's name in lower case, as commands and translation keys spell it */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** @return the stage spelt {@code id}, if any */
    public static Optional<LifeStage> byId(String id) {
        return Arrays.stream(values()).filter(stage -> stage.id().equals(id)).findFirst();
    }
}
