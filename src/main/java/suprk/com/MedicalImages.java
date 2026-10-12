package suprk.com;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class MedicalImages implements ModInitializer {
	public static final String MOD_ID = "medicalimages";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	MedicalImage img;
	String loaded_filename;

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		// Registering command to load image in nifti
		CommandRegistrationCallback.EVENT.register(
				(dispatcher, registryAccess, environment) -> {

					dispatcher.register(
							Commands.literal("mi_load")

									// /mi_load
									.executes(context -> {

										Path directory = FabricLoader.getInstance()
												.getGameDir()
												.resolve("medicalimages");

										try {
											Files.createDirectories(directory);

											try (Stream<Path> files = Files.list(directory)) {

												List<String> niftiFiles = files
														.filter(path -> {

															if (Files.isDirectory(path)) {
																return true;
															}

															String name = path.getFileName()
																	.toString()
																	.toLowerCase(Locale.ROOT);

															return name.endsWith(".nii")
																	|| name.endsWith(".nii.gz");
														})
														.map(path -> path.getFileName().toString())
														.sorted()
														.toList();

												if (niftiFiles.isEmpty()) {
													context.getSource().sendSuccess(
															() -> Component.literal(
																	"No NIfTI files found."
															),
															false
													);
												} else {
													context.getSource().sendSuccess(
															() -> Component.literal(
																	"Available NIfTI files:\n"
																			+ String.join("\n", niftiFiles)
															),
															false
													);
												}
											}

										} catch (IOException e) {
											context.getSource().sendFailure(
													Component.literal(e.getMessage())
											);

											return 0;
										}

										return 1;
									})

									// /mi_load <file_name>
									.then(
											Commands.argument(
															"file_name",
															StringArgumentType.string()
													)

													// TAB suggestions
													.suggests((context, builder) -> {

														Path directory = FabricLoader.getInstance()
																.getGameDir()
																.resolve("medicalimages");

														try {
															Files.createDirectories(directory);

															try (Stream<Path> files =
																		 Files.list(directory)) {

																files
																		.filter(path -> {

																			if (Files.isDirectory(path)) {
																				return true;
																			}

																			String name = path.getFileName()
																					.toString()
																					.toLowerCase(Locale.ROOT);

																			return name.endsWith(".nii")
																					|| name.endsWith(".nii.gz");
																		})
																		.map(path -> path.getFileName().toString())
																		.sorted()
																		.forEach(builder::suggest);
															}

														} catch (IOException e) {
															e.printStackTrace();
														}

														return builder.buildFuture();
													})

													.executes(context -> {

														String filename =
																StringArgumentType.getString(
																		context,
																		"file_name"
																);

														try {
															img = CTLoader.load(filename);

															loaded_filename = filename;

															int sizeX = img.sizeX;
															int sizeY = img.sizeY;
															int sizeZ = img.sizeZ;

															context.getSource().sendSuccess(
																	() -> Component.literal(
																			"Loaded %s: %d x %d x %d"
																					.formatted(
																							filename,
																							sizeX,
																							sizeY,
																							sizeZ
																					)
																	),
																	false
															);

														} catch (IOException e) {
															context.getSource().sendFailure(
																	Component.literal(
																			"Couldn't load "
																					+ filename
																					+ ": "
																					+ e.getMessage()
																	)
															);

															return 0;
														}

														return 1;
													})
									)
					);
				});

		// Registering command to check if loaded
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(Commands.literal("mi_info")
					.executes(context -> {

						if (img != null) {
							int sizeX = img.sizeX;
							int sizeY = img.sizeY;
							int sizeZ = img.sizeZ;

							context.getSource().sendSuccess(
									() -> Component.literal(
											"Loaded %s: %d x %d x %d"
													.formatted(
															loaded_filename,
															sizeX,
															sizeY,
															sizeZ
													)
									),
									false
							);
						}
						else {
							context.getSource().sendSuccess(() -> Component.literal("Nothing is loaded - use /mi_load command."), false);
						}

						return 1;

					}));
		});

		// Registering command to unload
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(Commands.literal("mi_unload")
					.executes(context -> {

						img = null;
						loaded_filename = null;

						context.getSource().sendSuccess(() -> Component.literal("Unloaded file."), false);

						return 1;

					}));
		});

		// Registering command to generate layer
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
					Commands.literal("mi_layer")
							.then(Commands.argument("axis", StringArgumentType.word())
									.suggests((context, builder) -> {
										builder.suggest("x");
										builder.suggest("y");
										builder.suggest("z");
										return builder.buildFuture();
									})
									.then(Commands.argument("layer", IntegerArgumentType.integer())
											.then(Commands.argument("value_min", IntegerArgumentType.integer())
													.then(Commands.argument("value_max", IntegerArgumentType.integer())
															.then(Commands.argument("block", BlockStateArgument.block(registryAccess))
																	.executes(context -> {

																		if (img == null) {
																			context.getSource().sendSuccess(
																					() -> Component.literal(
																							"Nothing is loaded - use /mi_load command"
																					),
																					false
																			);
																			return 1;
																		}

																		String axis = StringArgumentType
																				.getString(context, "axis")
																				.toLowerCase();

																		int layer = IntegerArgumentType.getInteger(
																				context, "layer"
																		);

																		int valueMin = IntegerArgumentType.getInteger(
																				context, "value_min"
																		);

																		int valueMax = IntegerArgumentType.getInteger(
																				context, "value_max"
																		);

																		BlockInput block = BlockStateArgument.getBlock(
																				context, "block"
																		);

																		int layerCount;
																		int sizeA;
																		int sizeB;

																		switch (axis) {
																			case "x" -> {
																				layerCount = img.sizeX;
																				sizeA = img.sizeY;
																				sizeB = img.sizeZ;
																			}

																			case "y" -> {
																				layerCount = img.sizeY;
																				sizeA = img.sizeX;
																				sizeB = img.sizeZ;
																			}

																			case "z" -> {
																				layerCount = img.sizeZ;
																				sizeA = img.sizeX;
																				sizeB = img.sizeY;
																			}

																			default -> {
																				context.getSource().sendFailure(
																						Component.literal(
																								"Invalid axis. Use x, y or z."
																						)
																				);
																				return 0;
																			}
																		}

																		if (layer < 0 || layer >= layerCount) {
																			context.getSource().sendFailure(
																					Component.literal(
																							"Layer out of bounds. Axis "
																									+ axis
																									+ " has "
																									+ layerCount
																									+ " layers."
																					)
																			);
																			return 0;
																		}

																		context.getSource().sendSuccess(
																				() -> Component.literal(
																						"Placing layer %d along %s axis."
																								.formatted(layer, axis)
																				),
																				false
																		);

																		Vec3 pos = Objects.requireNonNull(
																				context.getSource().getPlayer()
																		).position();

																		ServerLevel world =
																				context.getSource().getLevel();

																		for (int i = 0; i < sizeA; i++) {
																			for (int j = 0; j < sizeB; j++) {

																				try {
																					double value;

																					switch (axis) {
																						case "x" ->
																								value = img.get(layer, i, j);

																						case "y" ->
																								value = img.get(i, layer, j);

																						case "z" ->
																								value = img.get(i, j, layer);

																						default ->
																								throw new IllegalStateException();
																					}

																					if (value > valueMin
																							&& value < valueMax) {

																						BlockPos bp = new BlockPos(
																								(int) pos.x - i,
																								(int) pos.y,
																								(int) pos.z - j
																						);

																						block.place(world, bp, 2);
																					}

																				} catch (IOException e) {
																					throw new RuntimeException(e);
																				}
																			}
																		}

																		return 1;
																	})
															)
													)
											)
									)
							)
			);
		});

		// Registering command to generate layers
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
					Commands.literal("mi_layers")
							.then(Commands.argument("axis", StringArgumentType.word())
									.suggests((context, builder) -> {
										builder.suggest("x");
										builder.suggest("y");
										builder.suggest("z");
										return builder.buildFuture();
									})
									.then(Commands.argument("start_layer", IntegerArgumentType.integer())
											.then(Commands.argument("value_min", IntegerArgumentType.integer())
													.then(Commands.argument("value_max", IntegerArgumentType.integer())
															.then(Commands.argument("n_layers", IntegerArgumentType.integer())
																	.then(Commands.argument("block", BlockStateArgument.block(registryAccess))
																			.then(Commands.argument("upwards", BoolArgumentType.bool())
																					.executes(context -> {

																						if (img == null) {
																							context.getSource().sendSuccess(
																									() -> Component.literal(
																											"Nothing is loaded - use /mi_load command"
																									),
																									false
																							);
																							return 1;
																						}

																						String axis = StringArgumentType
																								.getString(context, "axis")
																								.toLowerCase();

																						int layer = IntegerArgumentType.getInteger(
																								context,
																								"start_layer"
																						);

																						int valueMin = IntegerArgumentType.getInteger(
																								context,
																								"value_min"
																						);

																						int valueMax = IntegerArgumentType.getInteger(
																								context,
																								"value_max"
																						);

																						int n = IntegerArgumentType.getInteger(
																								context,
																								"n_layers"
																						);

																						BlockInput block = BlockStateArgument.getBlock(
																								context,
																								"block"
																						);

																						boolean upwards = BoolArgumentType.getBool(
																								context,
																								"upwards"
																						);

																						// Rozmiar osi warstw oraz dwóch osi przekroju
																						int layerCount;
																						int sizeA;
																						int sizeB;

																						switch (axis) {
																							case "x" -> {
																								layerCount = img.sizeX;
																								sizeA = img.sizeY;
																								sizeB = img.sizeZ;
																							}

																							case "y" -> {
																								layerCount = img.sizeY;
																								sizeA = img.sizeX;
																								sizeB = img.sizeZ;
																							}

																							case "z" -> {
																								layerCount = img.sizeZ;
																								sizeA = img.sizeX;
																								sizeB = img.sizeY;
																							}

																							default -> {
																								context.getSource().sendFailure(
																										Component.literal(
																												"Invalid axis. Use x, y or z."
																										)
																								);
																								return 0;
																							}
																						}

																						if (layer < 0 || layer + n > layerCount) {
																							context.getSource().sendFailure(
																									Component.literal(
																											"Requested layers exceed image bounds. "
																													+ "Axis " + axis
																													+ " has " + layerCount
																													+ " layers."
																									)
																							);
																							return 0;
																						}

																						context.getSource().sendSuccess(
																								() -> Component.literal(
																										"Placing %d layers from %d along %s axis."
																												.formatted(n, layer, axis)
																								),
																								false
																						);

																						Vec3 pos = Objects.requireNonNull(
																								context.getSource().getPlayer()
																						).position();

																						ServerLevel world =
																								context.getSource().getLevel();

																						for (int l = layer; l < layer + n; l++) {

																							int offset = l - layer;

																							int worldY = upwards
																									? (int) pos.y + offset
																									: (int) pos.y - offset;

																							for (int i = 0; i < sizeA; i++) {
																								for (int j = 0; j < sizeB; j++) {

																									try {
																										double value;

																										switch (axis) {
																											case "x" ->
																													value = img.get(l, i, j);

																											case "y" ->
																													value = img.get(i, l, j);

																											case "z" ->
																													value = img.get(i, j, l);

																											default ->
																													throw new IllegalStateException();
																										}

																										if (value > valueMin
																												&& value < valueMax) {

																											BlockPos bp = new BlockPos(
																													(int) pos.x - i,
																													worldY,
																													(int) pos.z - j
																											);

																											block.place(world, bp, 2);
																										}

																									} catch (IOException e) {
																										throw new RuntimeException(e);
																									}
																								}
																							}
																						}

																						Objects.requireNonNull(
																								context.getSource().getPlayer()
																						).sendSystemMessage(
																								Component.literal(
																										"Finished placing blocks"
																								)
																						);

																						return 1;
																					})
																			)
																	)
															)
													)
											)
									)
							)
			);
		});

		// Command to check voxel HU
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(Commands.literal("mi_voxel_value")
					.then(Commands.argument("x", IntegerArgumentType.integer(1,512))
							.then(Commands.argument("y", IntegerArgumentType.integer(1,512))
									.then(Commands.argument("z", IntegerArgumentType.integer(1,512))
							.executes(context -> {

								if (img == null) {
									context.getSource().sendSuccess(() -> Component.literal("Nothing is loaded - use /mi_load command"), false);
									return 1;
								}

								int x = IntegerArgumentType.getInteger(context, "x");
								int y = IntegerArgumentType.getInteger(context, "y");
								int z = IntegerArgumentType.getInteger(context, "z");

                                try {
                                    Objects.requireNonNull(context.getSource().getPlayer()).sendSystemMessage(Component.literal("%s %s %s: %s".formatted(x,y,z,img.get(x, y, z))));
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }

                                return 1;

							})))));
		});

		// ADVANCED

		// Command using central component method for brain images
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
					Commands.literal("mi_layer_central")
							.then(Commands.argument("axis", StringArgumentType.word())
									.suggests((context, builder) -> {
										builder.suggest("x");
										builder.suggest("y");
										builder.suggest("z");
										return builder.buildFuture();
									})
									.then(Commands.argument("layer", IntegerArgumentType.integer())
											.then(Commands.argument("value_min", IntegerArgumentType.integer())
													.then(Commands.argument("value_max", IntegerArgumentType.integer())
															.then(Commands.argument(
																	"block",
																	BlockStateArgument.block(registryAccess)
															).executes(context -> {

																if (img == null) {
																	context.getSource().sendFailure(
																			Component.literal(
																					"Nothing is loaded - use /mi_load."
																			)
																	);
																	return 0;
																}

																String axis = StringArgumentType
																		.getString(context, "axis")
																		.toLowerCase();

																int layer = IntegerArgumentType.getInteger(
																		context, "layer"
																);

																int valueMin = IntegerArgumentType.getInteger(
																		context, "value_min"
																);

																int valueMax = IntegerArgumentType.getInteger(
																		context, "value_max"
																);

																BlockInput block = BlockStateArgument.getBlock(
																		context, "block"
																);

																int layerCount;
																int sizeA;
																int sizeB;

																switch (axis) {
																	case "x" -> {
																		layerCount = img.sizeX;
																		sizeA = img.sizeY;
																		sizeB = img.sizeZ;
																	}

																	case "y" -> {
																		layerCount = img.sizeY;
																		sizeA = img.sizeX;
																		sizeB = img.sizeZ;
																	}

																	case "z" -> {
																		layerCount = img.sizeZ;
																		sizeA = img.sizeX;
																		sizeB = img.sizeY;
																	}

																	default -> {
																		context.getSource().sendFailure(
																				Component.literal(
																						"Invalid axis. Use x, y or z."
																				)
																		);
																		return 0;
																	}
																}

																if (layer < 0 || layer >= layerCount) {
																	context.getSource().sendFailure(
																			Component.literal(
																					"Layer out of bounds."
																			)
																	);
																	return 0;
																}

																boolean[][] mask =
																		new boolean[sizeA][sizeB];

																try {

																	// 1. Threshold
																	for (int i = 0; i < sizeA; i++) {
																		for (int j = 0; j < sizeB; j++) {

																			double value = switch (axis) {
																				case "x" ->
																						img.get(layer, i, j);

																				case "y" ->
																						img.get(i, layer, j);

																				case "z" ->
																						img.get(i, j, layer);

																				default ->
																						throw new IllegalStateException();
																			};

																			mask[i][j] =
																					value > valueMin
																							&& value < valueMax;
																		}
																	}

																	// 2. Znajdź centralną składową
																	boolean[][] central =
																			findCentralComponent(mask);

																	Vec3 pos = Objects.requireNonNull(
																			context.getSource().getPlayer()
																	).position();

																	ServerLevel world =
																			context.getSource().getLevel();

																	int blocksPlaced = 0;

																	// 3. Postaw tylko centralną składową
																	for (int i = 0; i < sizeA; i++) {
																		for (int j = 0; j < sizeB; j++) {

																			if (!central[i][j]) {
																				continue;
																			}

																			BlockPos bp = new BlockPos(
																					(int) pos.x - i,
																					(int) pos.y,
																					(int) pos.z - j
																			);

																			block.place(world, bp, 2);
																			blocksPlaced++;
																		}
																	}

																	int finalBlocksPlaced = blocksPlaced;

																	context.getSource().sendSuccess(
																			() -> Component.literal(
																					"Placed central component: "
																							+ finalBlocksPlaced
																							+ " blocks."
																			),
																			false
																	);

																	return 1;

																} catch (IOException e) {
																	throw new RuntimeException(e);
																}
															}))
													)
											)
									)
							)
			);
		});

		// Command that can auto-color
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
					Commands.literal("mi_layer_auto_color")
							.then(Commands.argument("axis", StringArgumentType.word())
									.suggests((context, builder) -> {
										builder.suggest("x");
										builder.suggest("y");
										builder.suggest("z");
										return builder.buildFuture();
									})
									.then(Commands.argument("layer", IntegerArgumentType.integer())
											.then(Commands.argument("value_min", IntegerArgumentType.integer())
													.then(Commands.argument("value_max", IntegerArgumentType.integer())
															.then(Commands.argument("n_colors", IntegerArgumentType.integer(2,7))
																	.executes(context -> {

																		if (img == null) {
																			context.getSource().sendSuccess(
																					() -> Component.literal(
																							"Nothing is loaded - use /mi_load command"
																					),
																					false
																			);
																			return 1;
																		}

																		String axis = StringArgumentType
																				.getString(context, "axis")
																				.toLowerCase();

																		int layer = IntegerArgumentType.getInteger(
																				context, "layer"
																		);

																		int valueMin = IntegerArgumentType.getInteger(
																				context, "value_min"
																		);

																		int valueMax = IntegerArgumentType.getInteger(
																				context, "value_max"
																		);

																		int n_colors = IntegerArgumentType.getInteger(
																				context, "n_colors"
																		);

																		int layerCount;
																		int sizeA;
																		int sizeB;

																		switch (axis) {
																			case "x" -> {
																				layerCount = img.sizeX;
																				sizeA = img.sizeY;
																				sizeB = img.sizeZ;
																			}

																			case "y" -> {
																				layerCount = img.sizeY;
																				sizeA = img.sizeX;
																				sizeB = img.sizeZ;
																			}

																			case "z" -> {
																				layerCount = img.sizeZ;
																				sizeA = img.sizeX;
																				sizeB = img.sizeY;
																			}

																			default -> {
																				context.getSource().sendFailure(
																						Component.literal(
																								"Invalid axis. Use x, y or z."
																						)
																				);
																				return 0;
																			}
																		}

																		if (layer < 0 || layer >= layerCount) {
																			context.getSource().sendFailure(
																					Component.literal(
																							"Layer out of bounds. Axis "
																									+ axis
																									+ " has "
																									+ layerCount
																									+ " layers."
																					)
																			);
																			return 0;
																		}

																		context.getSource().sendSuccess(
																				() -> Component.literal(
																						"Placing layer %d along %s axis."
																								.formatted(layer, axis)
																				),
																				false
																		);

																		Vec3 pos = Objects.requireNonNull(
																				context.getSource().getPlayer()
																		).position();

																		ServerLevel world =
																				context.getSource().getLevel();

																		for (int i = 0; i < sizeA; i++) {
																			for (int j = 0; j < sizeB; j++) {

																				try {
																					double value;

																					switch (axis) {
																						case "x" ->
																								value = img.get(layer, i, j);

																						case "y" ->
																								value = img.get(i, layer, j);

																						case "z" ->
																								value = img.get(i, j, layer);

																						default ->
																								throw new IllegalStateException();
																					}

																					if (value > valueMin
																							&& value < valueMax) {

																						BlockPos bp = new BlockPos(
																								(int) pos.x - i,
																								(int) pos.y,
																								(int) pos.z - j
																						);

																						Block block = getScaleColorBlock(valueMin, valueMax, value, n_colors);

																						world.setBlock(
																								bp,
																								block.defaultBlockState(),
																								2
																						);
																					}

																				} catch (IOException e) {
																					throw new RuntimeException(e);
																				}
																			}
																		}

																		return 1;
																	})
															)
													)
											)
									)
							)
			);
		});

		// Registering command to generate layers with color
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
					Commands.literal("mi_layers_auto_color")
							.then(Commands.argument("axis", StringArgumentType.word())
									.suggests((context, builder) -> {
										builder.suggest("x");
										builder.suggest("y");
										builder.suggest("z");
										return builder.buildFuture();
									})
									.then(Commands.argument("start_layer", IntegerArgumentType.integer())
											.then(Commands.argument("value_min", IntegerArgumentType.integer())
													.then(Commands.argument("value_max", IntegerArgumentType.integer())
															.then(Commands.argument("n_layers", IntegerArgumentType.integer())
																	.then(Commands.argument("n_colors", IntegerArgumentType.integer(2,7))
																			.then(Commands.argument("upwards", BoolArgumentType.bool())
																					.executes(context -> {

																						if (img == null) {
																							context.getSource().sendSuccess(
																									() -> Component.literal(
																											"Nothing is loaded - use /mi_load command"
																									),
																									false
																							);
																							return 1;
																						}

																						String axis = StringArgumentType
																								.getString(context, "axis")
																								.toLowerCase();

																						int layer = IntegerArgumentType.getInteger(
																								context,
																								"start_layer"
																						);

																						int valueMin = IntegerArgumentType.getInteger(
																								context,
																								"value_min"
																						);

																						int valueMax = IntegerArgumentType.getInteger(
																								context,
																								"value_max"
																						);

																						int n = IntegerArgumentType.getInteger(
																								context,
																								"n_layers"
																						);

																						int n_colors = IntegerArgumentType.getInteger(
																								context, "n_colors"
																						);

																						boolean upwards = BoolArgumentType.getBool(
																								context,
																								"upwards"
																						);

																						// Rozmiar osi warstw oraz dwóch osi przekroju
																						int layerCount;
																						int sizeA;
																						int sizeB;

																						switch (axis) {
																							case "x" -> {
																								layerCount = img.sizeX;
																								sizeA = img.sizeY;
																								sizeB = img.sizeZ;
																							}

																							case "y" -> {
																								layerCount = img.sizeY;
																								sizeA = img.sizeX;
																								sizeB = img.sizeZ;
																							}

																							case "z" -> {
																								layerCount = img.sizeZ;
																								sizeA = img.sizeX;
																								sizeB = img.sizeY;
																							}

																							default -> {
																								context.getSource().sendFailure(
																										Component.literal(
																												"Invalid axis. Use x, y or z."
																										)
																								);
																								return 0;
																							}
																						}

																						if (layer < 0 || layer + n > layerCount) {
																							context.getSource().sendFailure(
																									Component.literal(
																											"Requested layers exceed image bounds. "
																													+ "Axis " + axis
																													+ " has " + layerCount
																													+ " layers."
																									)
																							);
																							return 0;
																						}

																						context.getSource().sendSuccess(
																								() -> Component.literal(
																										"Placing %d layers from %d along %s axis."
																												.formatted(n, layer, axis)
																								),
																								false
																						);

																						Vec3 pos = Objects.requireNonNull(
																								context.getSource().getPlayer()
																						).position();

																						ServerLevel world =
																								context.getSource().getLevel();

																						for (int l = layer; l < layer + n; l++) {

																							int offset = l - layer;

																							int worldY = upwards
																									? (int) pos.y + offset
																									: (int) pos.y - offset;

																							for (int i = 0; i < sizeA; i++) {
																								for (int j = 0; j < sizeB; j++) {

																									try {
																										double value;

																										switch (axis) {
																											case "x" ->
																													value = img.get(l, i, j);

																											case "y" ->
																													value = img.get(i, l, j);

																											case "z" ->
																													value = img.get(i, j, l);

																											default ->
																													throw new IllegalStateException();
																										}

																										if (value > valueMin
																												&& value < valueMax) {

																											BlockPos bp = new BlockPos(
																													(int) pos.x - i,
																													worldY,
																													(int) pos.z - j
																											);

																											Block block = getScaleColorBlock(valueMin, valueMax, value, n_colors);

																											world.setBlock(
																													bp,
																													block.defaultBlockState(),
																													2
																											);
																										}

																									} catch (IOException e) {
																										throw new RuntimeException(e);
																									}
																								}
																							}
																						}

																						Objects.requireNonNull(
																								context.getSource().getPlayer()
																						).sendSystemMessage(
																								Component.literal(
																										"Finished placing blocks"
																								)
																						);

																						return 1;
																					})
																			)
																	)
															)
													)
											)
									)
							)
			);
		});

		LOGGER.info("MedicalImages loaded!");
	}

	private static final Block[] HEAT_SCALE = {
			Blocks.CONCRETE.blue(),
			Blocks.CONCRETE.lightBlue(),
			Blocks.CONCRETE.green(),
			Blocks.CONCRETE.yellow(),
			Blocks.CONCRETE.orange(),
			Blocks.CONCRETE.red(),
			Blocks.CONCRETE.black()
	};

	private static Block getScaleColorBlock(
			int min,
			int max,
			double value,
			int nColors
	) {
		nColors = Math.clamp(nColors, 2, 7);

		double normalized =
				(double) (value - min) / (max - min);

		normalized = Math.clamp(normalized, 0.0, 1.0);

		// przedział 0 ... nColors-1
		int colorStep = (int) Math.floor(
				normalized * nColors
		);

		if (colorStep == nColors) {
			colorStep = nColors - 1;
		}

		// mapowanie np. 3 kolorów na indeksy 0, 3, 6
		int paletteIndex = (int) Math.round(
				colorStep * 6.0 / (nColors - 1)
		);

		return HEAT_SCALE[paletteIndex];
	}

	private static boolean[][] findCentralComponent(boolean[][] mask) {

		int width = mask.length;
		int height = mask[0].length;

		boolean[][] visited = new boolean[width][height];

		double centerX = (width - 1) / 2.0;
		double centerY = (height - 1) / 2.0;

		List<int[]> bestComponent = null;
		double bestDistance = Double.POSITIVE_INFINITY;

		int[][] directions = {
				{-1, -1}, {-1, 0}, {-1, 1},
				{ 0, -1},          { 0, 1},
				{ 1, -1}, { 1, 0}, { 1, 1}
		};

		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {

				if (!mask[x][y] || visited[x][y]) {
					continue;
				}

				List<int[]> component = new ArrayList<>();
				ArrayDeque<int[]> queue = new ArrayDeque<>();

				queue.add(new int[]{x, y});
				visited[x][y] = true;

				double componentDistance =
						Double.POSITIVE_INFINITY;

				while (!queue.isEmpty()) {

					int[] p = queue.removeFirst();

					int px = p[0];
					int py = p[1];

					component.add(p);

					double dx = px - centerX;
					double dy = py - centerY;

					double distance =
							dx * dx + dy * dy;

					if (distance < componentDistance) {
						componentDistance = distance;
					}

					for (int[] direction : directions) {

						int nx = px + direction[0];
						int ny = py + direction[1];

						if (nx < 0 || nx >= width ||
								ny < 0 || ny >= height) {
							continue;
						}

						if (!visited[nx][ny] &&
								mask[nx][ny]) {

							visited[nx][ny] = true;
							queue.addLast(
									new int[]{nx, ny}
							);
						}
					}
				}

				if (componentDistance < bestDistance) {
					bestDistance = componentDistance;
					bestComponent = component;
				}
			}
		}

		boolean[][] result =
				new boolean[width][height];

		if (bestComponent != null) {
			for (int[] p : bestComponent) {
				result[p[0]][p[1]] = true;
			}
		}

		return result;
	}

}
