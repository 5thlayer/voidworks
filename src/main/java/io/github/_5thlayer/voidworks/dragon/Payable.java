// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

/**
 * How much of what a provider hatch holds its network can pay the {@link Toll} to lift.
 *
 * @param holds how many stacks it holds
 * @param payable for how many of them its network can pay the Toll
 */
public record Payable(long holds, long payable) {
}
