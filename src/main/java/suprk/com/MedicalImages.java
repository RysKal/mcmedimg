package suprk.com;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import niftijio.NiftiVolume;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;

public class MedicalImages implements ModInitializer {
	public static final String MOD_ID = "medicalimages";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	NiftiVolume volume;

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

        try {
			volume = CTLoader.load();

        	int sizeX = volume.data.sizeX();
			int sizeY = volume.data.sizeY();
			int sizeZ = volume.data.sizeZ();

			System.out.println(
					"NIfTI size: "
							+ sizeX + " x "
							+ sizeY + " x "
							+ sizeZ
			);

		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		// Registering command to generate layer
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(Commands.literal("mi_layer")
					.then(Commands.argument("layer", IntegerArgumentType.integer(1,611))
					.executes(context -> {

						int layer = IntegerArgumentType.getInteger(context, "layer");

						context.getSource().sendSuccess(() -> Component.literal("Placing %s layer.".formatted(layer)), false);

						Vec3 pos = Objects.requireNonNull(context.getSource().getPlayer()).position();

						ServerLevel world = context.getSource().getLevel();

						for (int i = 0; i < 512; i++) {
							for (int j = 0; j < 512; j++) {
								if (volume.data.get(layer, i, j, 0) > 300) {
									BlockPos bp = new BlockPos((int) (pos.x - i), (int) (pos.y), (int) (pos.z - j));
									world.setBlock(bp, Blocks.BONE_BLOCK.defaultBlockState(), 2);
									System.out.println("Placed block at " + bp);
								}
							}
						}

						return 1;

					})));
		});

		// Registering command to generate layers
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(Commands.literal("mi_layers")
					.then(Commands.argument("start_layer", IntegerArgumentType.integer(1,611))
							.then(Commands.argument("n_layers", IntegerArgumentType.integer(1,611))
							.executes(context -> {

								int layer = IntegerArgumentType.getInteger(context, "start_layer");
								int n = IntegerArgumentType.getInteger(context, "n_layers");

								context.getSource().sendSuccess(() -> Component.literal("Placing layers starting from %s.".formatted(layer)), false);

								Vec3 pos = Objects.requireNonNull(context.getSource().getPlayer()).position();

								ServerLevel world = context.getSource().getLevel();

								for (int l = layer; l < layer + n; l++) {
									for (int i = 0; i < 512; i++) {
										for (int j = 0; j < 512; j++) {
											if (volume.data.get(l, i, j, 0) > 300) {
												BlockPos bp = new BlockPos((int) (pos.x - i), (int) (pos.y + layer + n - l), (int) (pos.z - j));
												world.setBlock(bp, Blocks.BONE_BLOCK.defaultBlockState(), 2);
												System.out.println("Placed block at " + bp);
											}
										}
									}
								}

								Objects.requireNonNull(context.getSource().getPlayer()).sendSystemMessage(Component.literal("Finished placing blocks"));


								return 1;

							}))));
		});

		// Command to check voxel HU
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(Commands.literal("mi_voxel_hu")
					.then(Commands.argument("x", IntegerArgumentType.integer(1,512))
							.then(Commands.argument("y", IntegerArgumentType.integer(1,512))
									.then(Commands.argument("z", IntegerArgumentType.integer(1,512))
							.executes(context -> {

								int x = IntegerArgumentType.getInteger(context, "x");
								int y = IntegerArgumentType.getInteger(context, "y");
								int z = IntegerArgumentType.getInteger(context, "z");

								Objects.requireNonNull(context.getSource().getPlayer()).sendSystemMessage(Component.literal(String.valueOf(volume.data.get(x, y, z, 0))));

								return 1;

							})))));
		});

		LOGGER.info("MedicalImages loaded!");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
