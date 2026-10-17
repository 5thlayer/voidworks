// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.command;

import java.util.Arrays;
import java.util.Optional;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.VoidworksConfig;
import io.github._5thlayer.voidworks.butterfly.MoteFlight;
import io.github._5thlayer.voidworks.dragon.DragonPresences;
import io.github._5thlayer.voidworks.dragon.LifeStage;
import io.github._5thlayer.voidworks.dragon.Line;
import io.github._5thlayer.voidworks.dragon.Place;
import io.github._5thlayer.voidworks.dragon.SavedHatchery;
import io.github._5thlayer.voidworks.dragon.SavedVoidDragons;
import io.github._5thlayer.voidworks.dragon.VoidDragon;
import io.github._5thlayer.voidworks.energy.MoteResource;
import io.github._5thlayer.voidworks.energy.VoidEnergy;
import io.github._5thlayer.voidworks.network.MoteNetwork;
import io.github._5thlayer.voidworks.network.VoidstoneNetworks;
import io.github._5thlayer.voidworks.pressure.VoidPressure;
import io.github._5thlayer.voidworks.well.FillTarget;
import io.github._5thlayer.voidworks.well.VoidWellBlockEntity;
import io.github._5thlayer.voidworks.well.WellHatchBlockEntity;
import io.github._5thlayer.voidworks.well.WellHatches;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The {@code /voidworks} commands, a way to check the mote mechanic by hand in game before any
 * machine exists. They are for operators only.
 *
 * <ul>
 *   <li>{@code /voidworks pressure} reports the Void Pressure where the player stands, the open
 *       void's extra step included, and the dimension's harvest kind.</li>
 *   <li>{@code /voidworks butterflies <grade> <count>} shows {@code count} motes of {@code grade}
 *       flying to the player as butterflies.</li>
 *   <li>{@code /voidworks mote <pos> <grade> [count]} puts {@code count} motes of {@code grade}
 *       into the network of the Voidstone at {@code pos}, as many as it has room for, or with no
 *       count fills it.</li>
 *   <li>{@code /voidworks showcase} builds a line of Voidstone ahead of the player, motes at its
 *       near end and a Dormant Mutation Chamber at its far end, to watch motes conduct
 *       ({@link Showcase}).</li>
 *   <li>{@code /voidworks well <pos> <target>} readies the Void Well core at {@code pos} for whoever
 *       runs it: theirs, with that fill target, its Dormant cost paid, and in its network at once
 *       when the well around it is whole.</li>
 *   <li>{@code /voidworks dragon <pos> [mote|matter]} adds a grown Void Dragon of that Line, the Mote
 *       line by default, owned by whoever runs it, resting at the Well core at {@code pos}. It is not
 *       hatched, so it doubles no egg price.</li>
 *   <li>{@code /voidworks dragon remove <from> <to>} removes every dragon, whoever's, resting in the
 *       box, in the runner's dimension, or on a trip to or from a place in it, with its presence.</li>
 *   <li>{@code /voidworks hatch <pos> (provider|requester) <item> <stacks>} readies the Well hatch at
 *       {@code pos} for whoever runs it: joined to the Well it stands against, theirs, and a provider
 *       holding {@code stacks} full stacks of {@code item} or a requester asking for that many.</li>
 *   <li>{@code /voidworks dragon age <stage>} sets the grown dragon the runner looks at to the start
 *       of that life stage, owing no egg, to see each stage without carrying a life's motes.</li>
 * </ul>
 *
 * <p>{@code well}, {@code dragon}, {@code dragon remove} and {@code hatch} are what a Gallery pack
 * scene builds a working dragon and matter link with (ADR 0008). A
 * refusal is an error, as a vanilla command's is, so a function's trace names it.
 */
public final class VoidworksCommands {

    /** The most motes one {@code /voidworks butterflies} shows. */
    static final int MAX_COUNT = 4096;

    private static final DynamicCommandExceptionType NOT_VOIDSTONE =
            new DynamicCommandExceptionType(pos -> Component.translatable("commands.voidworks.mote.not_voidstone", pos));
    private static final DynamicCommandExceptionType WELL_NOT_A_WELL =
            new DynamicCommandExceptionType(pos -> Component.translatable("commands.voidworks.well.not_a_well", pos));
    private static final DynamicCommandExceptionType DRAGON_NOT_A_WELL =
            new DynamicCommandExceptionType(pos -> Component.translatable("commands.voidworks.dragon.not_a_well", pos));

    private static final DynamicCommandExceptionType NOT_A_HATCH =
            new DynamicCommandExceptionType(pos -> Component.translatable("commands.voidworks.hatch.not_a_hatch", pos));
    private static final DynamicCommandExceptionType NOT_YOUR_WELL =
            new DynamicCommandExceptionType(pos -> Component.translatable("commands.voidworks.hatch.not_your_well", pos));

    private static final DynamicCommandExceptionType NOT_A_STAGE =
            new DynamicCommandExceptionType(name -> Component.translatable("commands.voidworks.dragon.age.not_a_stage", name));
    private static final SimpleCommandExceptionType NOT_LOOKING_AT_A_DRAGON =
            new SimpleCommandExceptionType(Component.translatable("commands.voidworks.dragon.age.not_looking"));

    /** How far {@code /voidworks dragon age} looks for a dragon, in blocks. */
    private static final double LOOK_REACH = 64;

    private VoidworksCommands() {
    }

    public static void register() {
        // RegisterCommandsEvent is a game-bus event, so it does not go through the mod bus.
        NeoForge.EVENT_BUS.addListener(VoidworksCommands::registerCommands);
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        var wellAt = Commands.argument("pos", BlockPosArgument.blockPos());
        for (var target : FillTarget.values()) {
            wellAt.then(Commands.literal(target.getSerializedName()).executes(context -> well(context, target)));
        }
        var dragonAt = Commands.argument("pos", BlockPosArgument.blockPos()).executes(context -> dragon(context, Line.MOTE));
        for (var line : Line.values()) {
            dragonAt.then(Commands.literal(line.getSerializedName()).executes(context -> dragon(context, line)));
        }
        var hatchAt = Commands.argument("pos", BlockPosArgument.blockPos());
        for (var mode : WellHatches.Mode.values()) {
            hatchAt.then(Commands.literal(mode.getSerializedName())
                    .then(Commands.argument("item", ItemArgument.item(event.getBuildContext()))
                            .then(Commands.argument("stacks", IntegerArgumentType.integer(1))
                                    .executes(context -> hatch(context, mode)))));
        }
        event.getDispatcher().register(Commands.literal("voidworks")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("showcase")
                        .executes(VoidworksCommands::showcase))
                .then(Commands.literal("pressure")
                        .executes(VoidworksCommands::pressure))
                .then(Commands.literal("well").then(wellAt))
                .then(Commands.literal("dragon")
                        .then(Commands.literal("age")
                                .then(Commands.argument("stage", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(LifeStage.values()).map(LifeStage::id), builder))
                                        .executes(VoidworksCommands::age)))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("from", BlockPosArgument.blockPos())
                                        .then(Commands.argument("to", BlockPosArgument.blockPos())
                                                .executes(VoidworksCommands::removeDragons))))
                        .then(dragonAt))
                .then(Commands.literal("hatch").then(hatchAt))
                .then(Commands.literal("butterflies")
                        .then(Commands.argument("grade", IntegerArgumentType.integer(VoidEnergy.MIN_GRADE, VoidEnergy.MAX_GRADE))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, MAX_COUNT))
                                        .executes(VoidworksCommands::butterflies))))
                .then(Commands.literal("mote")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("grade", IntegerArgumentType.integer(VoidEnergy.MIN_GRADE, VoidEnergy.MAX_GRADE))
                                        .executes(context -> mote(context, Integer.MAX_VALUE))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                                .executes(context -> mote(context, IntegerArgumentType.getInteger(context, "count"))))))));
    }

    /** Fills the network at a Voidstone, from it outward. @return how many motes went in */
    private static int mote(CommandContext<CommandSourceStack> context, int count) throws CommandSyntaxException {
        var level = context.getSource().getLevel();
        var pos = BlockPosArgument.getLoadedBlockPos(context, level, "pos");
        int grade = IntegerArgumentType.getInteger(context, "grade");
        var networks = VoidstoneNetworks.of(level);
        Optional<MoteNetwork> network = level.getBlockState(pos).is(VoidworksBlocks.VOIDSTONE.get())
                ? networks.at(pos)
                : Optional.empty();
        if (network.isEmpty()) {
            throw NOT_VOIDSTONE.create(pos.toShortString());
        }
        int inserted;
        try (var transaction = Transaction.openRoot()) {
            inserted = networks.deposit(pos, MoteResource.of(grade), count, transaction);
            transaction.commit();
        }
        long holds = network.get().total();
        long capacity = network.get().capacity();
        context.getSource().sendSuccess(() -> Component.translatable("commands.voidworks.mote",
                inserted, grade, pos.toShortString(), holds, capacity), false);
        return inserted;
    }

    /** Readies the Well core at {@code pos} for whoever runs it, with {@code target}. @return 1 */
    private static int well(CommandContext<CommandSourceStack> context, FillTarget target) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        var level = context.getSource().getLevel();
        var well = wellAt(context, WELL_NOT_A_WELL);
        well.ready(level, player, target);
        context.getSource().sendSuccess(() -> Component.translatable("commands.voidworks.well",
                well.label(), target.label(), player.getName()), false);
        return 1;
    }

    /** Gives whoever runs it a grown Void Dragon of {@code line}, resting at the Well core at {@code pos}. @return 1 */
    private static int dragon(CommandContext<CommandSourceStack> context, Line line) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        var level = context.getSource().getLevel();
        var well = wellAt(context, DRAGON_NOT_A_WELL);
        SavedVoidDragons.of(level.getServer()).add(player.getUUID(), line, Place.of(level, well.getBlockPos()));
        DragonPresences.show(level.getServer());
        context.getSource().sendSuccess(() -> Component.translatable("commands.voidworks.dragon", well.label(), player.getName()), false);
        return 1;
    }

    /**
     * Readies the Well hatch at {@code pos} for whoever runs it, a {@code mode}: a provider filled with
     * so many full stacks of the item, as far as it has room, or a requester asking for so many, up to
     * as many as it has slots.
     *
     * @return how many stacks it holds or asks for
     */
    private static int hatch(CommandContext<CommandSourceStack> context, WellHatches.Mode mode) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        var level = context.getSource().getLevel();
        var pos = BlockPosArgument.getLoadedBlockPos(context, level, "pos");
        if (!(level.getBlockEntity(pos) instanceof WellHatchBlockEntity entity)) {
            throw NOT_A_HATCH.create(pos.toShortString());
        }
        var hatch = entity.ready(level, player, mode).orElseThrow(() -> NOT_YOUR_WELL.create(pos.toShortString()));
        var resource = ItemResource.of(ItemArgument.getItem(context, "item").item());
        int stacks = IntegerArgumentType.getInteger(context, "stacks");
        var name = resource.getHoverName();
        int readied;
        Component told;
        if (mode == WellHatches.Mode.PROVIDER) {
            try (var transaction = Transaction.openRoot()) {
                readied = hatch.contents().insert(resource, stacks * resource.getMaxStackSize(), transaction) / resource.getMaxStackSize();
                transaction.commit();
            }
            told = Component.translatable("commands.voidworks.hatch", pos.toShortString(), readied, name, player.getName());
        } else {
            readied = ask(hatch, resource.value(), stacks);
            told = Component.translatable("commands.voidworks.hatch.requester", pos.toShortString(), readied, name, player.getName());
        }
        context.getSource().sendSuccess(() -> told, false);
        return readied;
    }

    /**
     * Has the requester {@code hatch} ask for {@code stacks} of {@code item}, a stack at a time, as its
     * owner's uses of the item do, so it asks for no more than it has slots.
     *
     * @return how many stacks it asks for
     */
    private static int ask(WellHatches.Hatch hatch, Item item, int stacks) {
        int asked = 0;
        for (int stack = 0; stack < stacks; stack++) {
            asked = hatch.ask(item).stacks();
        }
        return asked;
    }

    /** Sets the grown dragon the runner looks at to the start of a life stage. @return 1 */
    private static int age(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(context, "stage");
        var stage = LifeStage.byId(name).orElseThrow(() -> NOT_A_STAGE.create(name));
        var server = player.level().getServer();
        var dragons = SavedVoidDragons.of(server);
        var record = lookedAtDragon(player).flatMap(VoidDragon::dragonId).flatMap(dragons::get)
                .orElseThrow(NOT_LOOKING_AT_A_DRAGON::create);
        dragons.update(record.agedTo(VoidworksConfig.lifeStages(record.line()).start(stage)));
        DragonPresences.show(server);
        context.getSource().sendSuccess(() -> Component.translatable("commands.voidworks.dragon.age", name), false);
        return 1;
    }

    /** @return the Void Dragon presence within {@link #LOOK_REACH} blocks along the player's look, if any */
    private static Optional<VoidDragon> lookedAtDragon(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(LOOK_REACH));
        var hit = ProjectileUtil.getEntityHitResult(player, eye, end,
                player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1), entity -> entity instanceof VoidDragon, LOOK_REACH * LOOK_REACH);
        return hit == null ? Optional.empty() : Optional.of((VoidDragon) hit.getEntity());
    }

    /** @return the Void Well core at the command's {@code pos}; else it refuses with {@code notAWell} */
    private static VoidWellBlockEntity wellAt(CommandContext<CommandSourceStack> context, DynamicCommandExceptionType notAWell) throws CommandSyntaxException {
        var level = context.getSource().getLevel();
        var pos = BlockPosArgument.getLoadedBlockPos(context, level, "pos");
        if (!(level.getBlockEntity(pos) instanceof VoidWellBlockEntity well)) {
            throw notAWell.create(pos.toShortString());
        }
        return well;
    }

    /** Removes every dragon resting in the box, in the runner's dimension, or on a trip to or from it. @return how many */
    private static int removeDragons(CommandContext<CommandSourceStack> context) {
        var level = context.getSource().getLevel();
        var box = BoundingBox.fromCorners(BlockPosArgument.getBlockPos(context, "from"), BlockPosArgument.getBlockPos(context, "to"));
        var dimension = level.dimension().identifier();
        var server = level.getServer();
        var removed = SavedVoidDragons.of(server).removeAt(place -> place.dimension().equals(dimension) && box.isInside(place.pos()));
        // A dragon taken out no longer counts toward its owner's next egg, as one that died does not.
        removed.forEach(dragon -> SavedHatchery.of(server).lose(dragon.owner()));
        DragonPresences.show(server);
        context.getSource().sendSuccess(() -> Component.translatable("commands.voidworks.dragon.remove", removed.size()), false);
        return removed.size();
    }

    /** Builds the conduction showcase two blocks ahead of the player, at their feet. @return how many motes went in */
    private static int showcase(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        var facing = player.getDirection();
        var start = player.blockPosition().relative(facing, 2);
        int motes = Showcase.build(player.level(), start, facing);
        if (motes < 0) {
            context.getSource().sendFailure(Component.translatable("commands.voidworks.showcase.pressure_too_high"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.voidworks.showcase",
                Showcase.LENGTH, motes, start.toShortString()), false);
        return motes;
    }

    /** @return the Void Pressure at the player's feet */
    private static int pressure(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int pressure = VoidPressure.at(player.level(), player.blockPosition());
        String harvestKind = VoidPressure.harvestKind(player.level()).getSerializedName();
        context.getSource().sendSuccess(() -> Component.translatable("commands.voidworks.pressure", pressure, harvestKind), false);
        return pressure;
    }

    /** Shows the motes flying to the player from four blocks ahead of them. @return how many butterflies */
    private static int butterflies(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int grade = IntegerArgumentType.getInteger(context, "grade");
        int count = IntegerArgumentType.getInteger(context, "count");
        Vec3 ahead = player.getEyePosition().add(player.getLookAngle().multiply(4, 0, 4));
        int butterflies = MoteFlight.toEntity(player.level(), ahead, player, grade, count);
        context.getSource().sendSuccess(
                () -> Component.translatable("commands.voidworks.butterflies", butterflies, count, grade), false);
        return butterflies;
    }
}
