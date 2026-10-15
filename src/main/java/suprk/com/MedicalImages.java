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

import javax.imageio.ImageIO;

public class MedicalImages implements ModInitializer {
	public static final String MOD_ID = "medicalimages";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	MedicalImage img;
	String loaded_filename;

	@Override
	public void onInitialize() {

		try {

			OpenCvLoader.load();

		} catch (Throwable e) {

			LOGGER.error(
					"Failed to load OpenCV native library",
					e
			);
		}

		ImageIO.scanForPlugins();

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

															if (img.isDicom()) {

																DicomSeriesInfo series =
																		img.getSelectedDicomSeriesInfo();

																int seriesCount =
																		img.getDicomSeriesInfos()
																				.size();


																context.getSource().sendSuccess(
																		() -> Component.literal(
																				"DICOM series "
																						+ series.index()
																						+ "/"
																						+ seriesCount
																						+ ": "
																						+ series.description()
																						+ " ["
																						+ series.modality()
																						+ "]"
																						+ ", "
																						+ series.sliceCount()
																						+ " slices"
																		),
																		false
																);


																if (seriesCount > 1) {

																	context.getSource().sendSuccess(
																			() -> Component.literal(
																					"Multiple series detected. "
																							+ "Use /mi_series to list them."
																			),
																			false
																	);
																}
															}

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

							if (img.isDicom()) {

								DicomSeriesInfo series =
										img.getSelectedDicomSeriesInfo();

								context.getSource().sendSuccess(
										() -> Component.literal(
												"DICOM series: "
														+ series.index()
														+ "/"
														+ img.getDicomSeriesInfos().size()
														+ "\n"
														+ "SeriesNumber: "
														+ (
														series.seriesNumber() >= 0
																? series.seriesNumber()
																: "?"
												)
														+ "\nDescription: "
														+ series.description()
														+ "\nModality: "
														+ series.modality()
														+ "\nSlices: "
														+ series.sliceCount()
														+ "\nResolution: "
														+ series.columns()
														+ " x "
														+ series.rows()
										),
										false
								);
							}
						}
						else {
							context.getSource().sendSuccess(() -> Component.literal("Nothing is loaded - use /mi_load command."), false);
						}

						return 1;

					}));
		});

		CommandRegistrationCallback.EVENT.register(
				(dispatcher, registryAccess, environment) -> {

					dispatcher.register(
							Commands.literal("mi_series")

									// If no arguments list series
									.executes(context -> {

										if (img == null) {

											context.getSource().sendFailure(
													Component.literal(
															"Nothing is loaded - use /mi_load."
													)
											);

											return 0;
										}


										if (!img.isDicom()) {

											context.getSource().sendFailure(
													Component.literal(
															"Loaded image is not DICOM."
													)
											);

											return 0;
										}


										List<DicomSeriesInfo> seriesList =
												img.getDicomSeriesInfos();


										context.getSource().sendSuccess(
												() -> Component.literal(
														"DICOM series in "
																+ loaded_filename
																+ ":"
												),
												false
										);


										for (DicomSeriesInfo series : seriesList) {

											String marker =
													series.selected()
															? "* "
															: "  ";

											String seriesNumber =
													series.seriesNumber() >= 0
															? Integer.toString(series.seriesNumber())
															: "?";

											String acquisitionNumber =
													series.acquisitionNumber() >= 0
															? Integer.toString(series.acquisitionNumber())
															: "?";

											String tr =
													Double.isNaN(series.repetitionTime())
															? "?"
															: "%.2f".formatted(
															series.repetitionTime()
													);

											String te =
													Double.isNaN(series.echoTime())
															? "?"
															: "%.2f".formatted(
															series.echoTime()
													);

											String ti =
													Double.isNaN(series.inversionTime())
															? "?"
															: "%.2f".formatted(
															series.inversionTime()
													);


											String line1 =
													marker
															+ "["
															+ series.index()
															+ "] "
															+ series.description()
															+ " ["
															+ series.modality()
															+ "]";

											String line2 =
													"    Series="
															+ seriesNumber
															+ " | Acquisition="
															+ acquisitionNumber
															+ " | "
															+ series.sliceCount()
															+ " slices | "
															+ series.columns()
															+ "x"
															+ series.rows();

											String line3 =
													"    TR="
															+ tr
															+ " | TE="
															+ te
															+ " | TI="
															+ ti
															+ " | UID "
															+ shortUid(
															series.uid()
													);

											String line4 =
													"    ImageType: "
															+ series.imageType();


											context.getSource().sendSuccess(
													() -> Component.literal(line1),
													false
											);

											context.getSource().sendSuccess(
													() -> Component.literal(line2),
													false
											);

											context.getSource().sendSuccess(
													() -> Component.literal(line3),
													false
											);

											context.getSource().sendSuccess(
													() -> Component.literal(line4),
													false
											);
										}

										return 1;
									})


									// If argument change selected series
									.then(
											Commands.argument(
															"index",
															IntegerArgumentType.integer(1)
													)

													/*
													 * Podpowiadanie:
													 *
													 * 1
													 * 2
													 * 3
													 * ...
													 */
													.suggests(
															(context, builder) -> {

																if (img != null
																		&& img.isDicom()) {

																	for (
																			DicomSeriesInfo series :
																			img.getDicomSeriesInfos()
																	) {

																		builder.suggest(
																				series.index()
																		);
																	}
																}


																return builder.buildFuture();
															}
													)

													.executes(context -> {

														if (img == null
																|| loaded_filename == null) {

															context.getSource().sendFailure(
																	Component.literal(
																			"Nothing is loaded."
																	)
															);

															return 0;
														}


														if (!img.isDicom()) {

															context.getSource().sendFailure(
																	Component.literal(
																			"Loaded image is not DICOM."
																	)
															);

															return 0;
														}


														int index =
																IntegerArgumentType.getInteger(
																		context,
																		"index"
																);


														int seriesCount =
																img.getDicomSeriesInfos()
																		.size();


														if (index < 1
																|| index > seriesCount) {

															context.getSource().sendFailure(
																	Component.literal(
																			"Series index must be between 1 and "
																					+ seriesCount
																	)
															);

															return 0;
														}


														try {

															img =
																	CTLoader.load(
																			loaded_filename,
																			index
																	);


															DicomSeriesInfo selected =
																	img.getSelectedDicomSeriesInfo();


															context.getSource().sendSuccess(
																	() -> Component.literal(
																			"Selected DICOM series "
																					+ selected.index()
																					+ "/"
																					+ img.getDicomSeriesInfos().size()
																					+ ": "
																					+ selected.description()
																					+ " ["
																					+ selected.modality()
																					+ "]"
																					+ ", "
																					+ selected.sliceCount()
																					+ " slices"
																					+ ", "
																					+ selected.columns()
																					+ "x"
																					+ selected.rows()
																	),
																	false
															);


															context.getSource().sendSuccess(
																	() -> Component.literal(
																			"Image dimensions: "
																					+ img.sizeX
																					+ " x "
																					+ img.sizeY
																					+ " x "
																					+ img.sizeZ
																	),
																	false
															);


															return 1;


														} catch (IOException e) {

															context.getSource().sendFailure(
																	Component.literal(
																			"Couldn't load series "
																					+ index
																					+ ": "
																					+ e.getMessage()
																	)
															);

															return 0;
														}
													})
									)
					);
				}
		);

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
									.then(Commands.argument("layer", IntegerArgumentType.integer(1))
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

																		int layerNumber = IntegerArgumentType.getInteger(
																				context,
																				"layer"
																		);

																		int layer = layerNumber - 1;

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

																		int blocksPlaced = 0;

																		try {

																			for (int i = 0; i < sizeA; i++) {
																				for (int j = 0; j < sizeB; j++) {

																					double value =
																							switch (axis) {

																								case "x" ->
																										img.get(
																												layer,
																												i,
																												j
																										);

																								case "y" ->
																										img.get(
																												i,
																												layer,
																												j
																										);

																								case "z" ->
																										img.get(
																												i,
																												j,
																												layer
																										);

																								default ->
																										throw new IllegalStateException();
																							};

																					if (value > valueMin
																							&& value < valueMax) {

																						BlockPos bp =
																								new BlockPos(
																										(int) pos.x - i,
																										(int) pos.y,
																										(int) pos.z - j
																								);

																						block.place(
																								world,
																								bp,
																								2
																						);

																						blocksPlaced++;
																					}
																				}
																			}

																		} catch (Exception e) {

																			LOGGER.error(
																					"Error while placing medical image layer",
																					e
																			);

																			context.getSource().sendFailure(
																					Component.literal(
																							"Failed: "
																									+ e.getClass().getSimpleName()
																									+ ": "
																									+ e.getMessage()
																					)
																			);

																			return 0;
																		}

																		int finalBlocksPlaced =
																				blocksPlaced;

																		context.getSource().sendSuccess(
																				() -> Component.literal(
																						"Finished. Placed "
																								+ finalBlocksPlaced
																								+ " blocks."
																				),
																				false
																		);

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
									.then(Commands.argument("start_layer", IntegerArgumentType.integer(1))
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

																						int layerNumber = IntegerArgumentType.getInteger(
																								context,
																								"start_layer"
																						);

																						int layer = layerNumber - 1;

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

																						// Size of layers & axis
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

																						if (n <= 0) {

																							context.getSource().sendFailure(
																									Component.literal(
																											"n_layers must be greater than 0."
																									)
																							);

																							return 0;
																						}

																						if (layer < 0
																								|| layer + n > layerCount) {

																							context.getSource().sendFailure(
																									Component.literal(
																											"Requested layers exceed image bounds. "
																													+ "Axis "
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
																										"Placing "
																												+ n
																												+ " layers from "
																												+ layerNumber
																												+ " along "
																												+ axis
																												+ " axis."
																								),
																								false
																						);

																						BlockPos playerPos =
																								Objects.requireNonNull(
																										context.getSource().getPlayer()
																								).blockPosition();

																						ServerLevel world =
																								context.getSource().getLevel();

																						int baseY =
																								playerPos.getY();

																						int totalBlocks =
																								0;

																						try {

																							for (int offset = 0;
																							     offset < n;
																							     offset++) {

																								int worldY =
																										baseY + offset;

																								int sourceLayer =
																										upwards
																												? layer + offset
																												: layer + (n - 1 - offset);

																								for (int i = 0;
																								     i < sizeA;
																								     i++) {

																									for (int j = 0;
																									     j < sizeB;
																									     j++) {

																										double value =
																												switch (axis) {

																													case "x" ->
																															img.get(
																																	sourceLayer,
																																	i,
																																	j
																															);

																													case "y" ->
																															img.get(
																																	i,
																																	sourceLayer,
																																	j
																															);

																													case "z" ->
																															img.get(
																																	i,
																																	j,
																																	sourceLayer
																															);

																													default ->
																															throw new IllegalStateException();
																												};

																										if (value > valueMin
																												&& value < valueMax) {

																											BlockPos bp =
																													new BlockPos(
																															playerPos.getX() - i,
																															worldY,
																															playerPos.getZ() - j
																													);

																											block.place(
																													world,
																													bp,
																													2
																											);

																											totalBlocks++;
																										}
																									}
																								}
																							}

																						} catch (Exception e) {

																							LOGGER.error(
																									"Error while placing medical image layers",
																									e
																							);

																							context.getSource().sendFailure(
																									Component.literal(
																											"Failed: "
																													+ e.getClass().getSimpleName()
																													+ ": "
																													+ e.getMessage()
																									)
																							);

																							return 0;
																						}

																						int finalTotalBlocks =
																								totalBlocks;

																						context.getSource().sendSuccess(
																								() -> Component.literal(
																										"Finished. Placed "
																												+ finalTotalBlocks
																												+ " blocks."
																								),
																								false
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

									.then(Commands.argument(
													"layer",
													IntegerArgumentType.integer(1)
											)

											.then(Commands.argument(
															"value_min",
															IntegerArgumentType.integer()
													)

													.then(Commands.argument(
																	"value_max",
																	IntegerArgumentType.integer()
															)

															.then(Commands.argument(
																			"selection",
																			StringArgumentType.word()
																	)
																	.suggests((context, builder) -> {
																		builder.suggest("center");
																		builder.suggest("largest");
																		builder.suggest("largest_near_center");
																		return builder.buildFuture();
																	})

																	.then(Commands.argument(
																					"connectivity",
																					IntegerArgumentType.integer(4, 8)
																			)

																			.then(Commands.argument(
																							"min_size",
																							IntegerArgumentType.integer(1)
																					)

																					.then(Commands.argument(
																									"center_radius",
																									IntegerArgumentType.integer(0)
																							)

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

																int layerNumber = IntegerArgumentType.getInteger(
																		context,
																		"layer"
																);

																int layer = layerNumber - 1;

																int valueMin = IntegerArgumentType.getInteger(
																		context, "value_min"
																);

																int valueMax = IntegerArgumentType.getInteger(
																		context, "value_max"
																);

																BlockInput block = BlockStateArgument.getBlock(
																		context, "block"
																);

																String selection =
																		StringArgumentType.getString(
																				context,
																				"selection"
																		).toLowerCase();

																int connectivity =
																		IntegerArgumentType.getInteger(
																				context,
																				"connectivity"
																		);

																int minSize =
																		IntegerArgumentType.getInteger(
																				context,
																				"min_size"
																		);

																int centerRadius =
																		IntegerArgumentType.getInteger(
																				context,
																				"center_radius"
																		);

																// Checking arguments

																if (connectivity != 4
																		&& connectivity != 8) {

																	context.getSource().sendFailure(
																			Component.literal(
																					"Connectivity must be 4 or 8."
																			)
																	);

																	return 0;
																}

																if (!selection.equals("center")
																		&& !selection.equals("largest")
																		&& !selection.equals("largest_near_center")) {

																	context.getSource().sendFailure(
																			Component.literal(
																					"Selection must be center, largest or largest_near_center."
																			)
																	);

																	return 0;
																}

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

																	// 2. Central component
																	boolean[][] central =
																			findCentralComponent(
																					mask,
																					selection,
																					connectivity,
																					minSize,
																					centerRadius
																			);

																	Vec3 pos = Objects.requireNonNull(
																			context.getSource().getPlayer()
																	).position();

																	ServerLevel world =
																			context.getSource().getLevel();

																	int blocksPlaced = 0;

																	// 3. Only central
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
													)))));
		});

		CommandRegistrationCallback.EVENT.register(
				(dispatcher, registryAccess, environment) -> {

					dispatcher.register(
							Commands.literal("mi_layers_central")

									.then(Commands.argument(
															"axis",
															StringArgumentType.word()
													)
													.suggests((context, builder) -> {

														builder.suggest("x");
														builder.suggest("y");
														builder.suggest("z");

														return builder.buildFuture();
													})

													.then(Commands.argument(
																			"start_layer",
																			IntegerArgumentType.integer(1)
																	)

																	.then(Commands.argument(
																							"value_min",
																							IntegerArgumentType.integer()
																					)

																					.then(Commands.argument(
																											"value_max",
																											IntegerArgumentType.integer()
																									)

																									.then(Commands.argument(
																															"n_layers",
																															IntegerArgumentType.integer(1)
																													)

																													.then(Commands.argument(
																																			"selection",
																																			StringArgumentType.word()
																																	)
																																	.suggests((context, builder) -> {

																																		builder.suggest("center");
																																		builder.suggest("largest");
																																		builder.suggest("largest_near_center");

																																		return builder.buildFuture();
																																	})

																																	.then(Commands.argument(
																																							"connectivity",
																																							IntegerArgumentType.integer(4, 8)
																																					)

																																					.then(Commands.argument(
																																											"min_size",
																																											IntegerArgumentType.integer(1)
																																									)

																																									.then(Commands.argument(
																																															"center_radius",
																																															IntegerArgumentType.integer(0)
																																													)

																																													.then(Commands.argument(
																																																			"block",
																																																			BlockStateArgument.block(registryAccess)
																																																	)

																																																	.then(Commands.argument(
																																																							"upwards",
																																																							BoolArgumentType.bool()
																																																					)

																																																					.executes(context -> {

																																																						if (img == null) {

																																																							context.getSource().sendFailure(
																																																									Component.literal(
																																																											"Nothing is loaded - use /mi_load."
																																																									)
																																																							);

																																																							return 0;
																																																						}

																																																						// ARGUMENTS

																																																						String axis =
																																																								StringArgumentType
																																																										.getString(context, "axis")
																																																										.toLowerCase();


																																																						int layerNumber =
																																																								IntegerArgumentType.getInteger(
																																																										context,
																																																										"start_layer"
																																																								);

																																																						// user starts from 1, code from 0
																																																						int startLayer =
																																																								layerNumber - 1;


																																																						int valueMin =
																																																								IntegerArgumentType.getInteger(
																																																										context,
																																																										"value_min"
																																																								);

																																																						int valueMax =
																																																								IntegerArgumentType.getInteger(
																																																										context,
																																																										"value_max"
																																																								);

																																																						int n =
																																																								IntegerArgumentType.getInteger(
																																																										context,
																																																										"n_layers"
																																																								);


																																																						String selection =
																																																								StringArgumentType
																																																										.getString(
																																																												context,
																																																												"selection"
																																																										)
																																																										.toLowerCase();


																																																						int connectivity =
																																																								IntegerArgumentType.getInteger(
																																																										context,
																																																										"connectivity"
																																																								);


																																																						int minSize =
																																																								IntegerArgumentType.getInteger(
																																																										context,
																																																										"min_size"
																																																								);


																																																						int centerRadius =
																																																								IntegerArgumentType.getInteger(
																																																										context,
																																																										"center_radius"
																																																								);


																																																						BlockInput block =
																																																								BlockStateArgument.getBlock(
																																																										context,
																																																										"block"
																																																								);


																																																						boolean upwards =
																																																								BoolArgumentType.getBool(
																																																										context,
																																																										"upwards"
																																																								);

																																																						// VALIDATION

																																																						if (connectivity != 4
																																																								&& connectivity != 8) {

																																																							context.getSource().sendFailure(
																																																									Component.literal(
																																																											"Connectivity must be 4 or 8."
																																																									)
																																																							);

																																																							return 0;
																																																						}

																																																						if (!selection.equals("center")
																																																								&& !selection.equals("largest")
																																																								&& !selection.equals("largest_near_center")) {

																																																							context.getSource().sendFailure(
																																																									Component.literal(
																																																											"Selection must be center, largest or largest_near_center."
																																																									)
																																																							);

																																																							return 0;
																																																						}

																																																						// IMAGE DIMENSIONS

																																																						int layerCount;
																																																						int sizeA;
																																																						int sizeB;

																																																						switch (axis) {

																																																							case "x" -> {

																																																								layerCount =
																																																										img.sizeX;

																																																								sizeA =
																																																										img.sizeY;

																																																								sizeB =
																																																										img.sizeZ;
																																																							}


																																																							case "y" -> {

																																																								layerCount =
																																																										img.sizeY;

																																																								sizeA =
																																																										img.sizeX;

																																																								sizeB =
																																																										img.sizeZ;
																																																							}


																																																							case "z" -> {

																																																								layerCount =
																																																										img.sizeZ;

																																																								sizeA =
																																																										img.sizeX;

																																																								sizeB =
																																																										img.sizeY;
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

																																																						if (startLayer < 0
																																																								|| startLayer + n > layerCount) {

																																																							context.getSource().sendFailure(
																																																									Component.literal(
																																																											"Requested layers exceed image bounds. "
																																																													+ "Axis "
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
																																																										"Processing "
																																																												+ n
																																																												+ " layers from "
																																																												+ layerNumber
																																																												+ " along "
																																																												+ axis
																																																												+ " axis."
																																																								),
																																																								false
																																																						);

																																																						// WORLD

																																																						BlockPos playerPos =
																																																								Objects.requireNonNull(
																																																										context.getSource()
																																																												.getPlayer()
																																																								).blockPosition();


																																																						ServerLevel world =
																																																								context.getSource()
																																																										.getLevel();


																																																						int baseY =
																																																								playerPos.getY();


																																																						int totalBlocks =
																																																								0;

																																																						// PROCESS LAYERS

																																																						try {

																																																							for (
																																																									int offset = 0;
																																																									offset < n;
																																																									offset++
																																																							) {

																																																								int worldY =
																																																										baseY + offset;

																																																								// upwards changes starting end
																																																								int sourceLayer =
																																																										upwards
																																																												? startLayer + offset
																																																												: startLayer + (n - 1 - offset);

																																																								// 1. THRESHOLD

																																																								boolean[][] mask =
																																																										new boolean[sizeA][sizeB];


																																																								for (
																																																										int i = 0;
																																																										i < sizeA;
																																																										i++
																																																								) {

																																																									for (
																																																											int j = 0;
																																																											j < sizeB;
																																																											j++
																																																									) {


																																																										double value =
																																																												switch (axis) {

																																																													case "x" ->
																																																															img.get(
																																																																	sourceLayer,
																																																																	i,
																																																																	j
																																																															);

																																																													case "y" ->
																																																															img.get(
																																																																	i,
																																																																	sourceLayer,
																																																																	j
																																																															);

																																																													case "z" ->
																																																															img.get(
																																																																	i,
																																																																	j,
																																																																	sourceLayer
																																																															);

																																																													default ->
																																																															throw new IllegalStateException();
																																																												};


																																																										mask[i][j] =
																																																												value > valueMin
																																																														&& value < valueMax;
																																																									}
																																																								}

																																																								// 2. COMPONENT

																																																								boolean[][] central =
																																																										findCentralComponent(
																																																												mask,
																																																												selection,
																																																												connectivity,
																																																												minSize,
																																																												centerRadius
																																																										);

																																																								// 3. PLACE

																																																								int blocksThisLayer =
																																																										0;


																																																								for (
																																																										int i = 0;
																																																										i < sizeA;
																																																										i++
																																																								) {

																																																									for (
																																																											int j = 0;
																																																											j < sizeB;
																																																											j++
																																																									) {


																																																										if (!central[i][j]) {
																																																											continue;
																																																										}


																																																										BlockPos bp =
																																																												new BlockPos(
																																																														playerPos.getX() - i,
																																																														worldY,
																																																														playerPos.getZ() - j
																																																												);


																																																										block.place(
																																																												world,
																																																												bp,
																																																												2
																																																										);


																																																										blocksThisLayer++;
																																																									}
																																																								}


																																																								totalBlocks +=
																																																										blocksThisLayer;


																																																								int displayLayer =
																																																										sourceLayer + 1;


																																																								int finalBlocksThisLayer =
																																																										blocksThisLayer;


																																																								context.getSource().sendSuccess(
																																																										() -> Component.literal(
																																																												"Layer "
																																																														+ displayLayer
																																																														+ ": "
																																																														+ finalBlocksThisLayer
																																																														+ " blocks."
																																																										),
																																																										false
																																																								);
																																																							}


																																																						} catch (Exception e) {

																																																							LOGGER.error(
																																																									"Error while processing central-component layers",
																																																									e
																																																							);


																																																							context.getSource().sendFailure(
																																																									Component.literal(
																																																											"Failed: "
																																																													+ e.getClass()
																																																													.getSimpleName()
																																																													+ ": "
																																																													+ e.getMessage()
																																																									)
																																																							);


																																																							return 0;
																																																						}

																																																						// FINISHED

																																																						int finalTotalBlocks =
																																																								totalBlocks;


																																																						context.getSource().sendSuccess(
																																																								() -> Component.literal(
																																																										"Finished. Placed "
																																																												+ finalTotalBlocks
																																																												+ " blocks in "
																																																												+ n
																																																												+ " layers."
																																																								),
																																																								false
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
																					)
																	)
													)
									));
				}
		);

		// Command that can auto-color
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
									.then(Commands.argument(
															"start_layer",
															IntegerArgumentType.integer(1)
													)
													.then(Commands.argument(
																			"value_min",
																			IntegerArgumentType.integer()
																	)
																	.then(Commands.argument(
																							"value_max",
																							IntegerArgumentType.integer()
																					)
																					.then(Commands.argument(
																											"n_layers",
																											IntegerArgumentType.integer(1)
																									)
																									.then(Commands.argument(
																															"n_colors",
																															IntegerArgumentType.integer(2, 7)
																													)
																													.then(Commands.argument(
																																			"upwards",
																																			BoolArgumentType.bool()
																																	)
																																	.executes(context -> {

																																		if (img == null) {
																																			context.getSource().sendFailure(
																																					Component.literal(
																																							"Nothing is loaded - use /mi_load command"
																																					)
																																			);
																																			return 0;
																																		}

																																		String axis =
																																				StringArgumentType
																																						.getString(context, "axis")
																																						.toLowerCase();

																																		int layerNumber =
																																				IntegerArgumentType.getInteger(
																																						context,
																																						"start_layer"
																																				);

																																		int layer =
																																				layerNumber - 1;

																																		int valueMin =
																																				IntegerArgumentType.getInteger(
																																						context,
																																						"value_min"
																																				);

																																		int valueMax =
																																				IntegerArgumentType.getInteger(
																																						context,
																																						"value_max"
																																				);

																																		int n =
																																				IntegerArgumentType.getInteger(
																																						context,
																																						"n_layers"
																																				);

																																		int nColors =
																																				IntegerArgumentType.getInteger(
																																						context,
																																						"n_colors"
																																				);

																																		boolean upwards =
																																				BoolArgumentType.getBool(
																																						context,
																																						"upwards"
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

																																		if (layer < 0
																																				|| layer + n > layerCount) {

																																			context.getSource().sendFailure(
																																					Component.literal(
																																							"Requested layers exceed image bounds. "
																																									+ "Axis "
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
																																						"Placing "
																																								+ n
																																								+ " layers from "
																																								+ layerNumber
																																								+ " along "
																																								+ axis
																																								+ " axis."
																																				),
																																				false
																																		);

																																		BlockPos playerPos =
																																				Objects.requireNonNull(
																																						context.getSource()
																																								.getPlayer()
																																				).blockPosition();

																																		ServerLevel world =
																																				context.getSource()
																																						.getLevel();

																																		int baseY =
																																				playerPos.getY();

																																		int blocksPlaced = 0;

																																		try {

																																			for (int offset = 0;
																																			     offset < n;
																																			     offset++) {

																																				int worldY =
																																						baseY + offset;

																																				int sourceLayer =
																																						upwards
																																								? layer + offset
																																								: layer + (n - 1 - offset);

																																				for (int i = 0;
																																				     i < sizeA;
																																				     i++) {

																																					for (int j = 0;
																																					     j < sizeB;
																																					     j++) {

																																						double value =
																																								switch (axis) {

																																									case "x" ->
																																											img.get(
																																													sourceLayer,
																																													i,
																																													j
																																											);

																																									case "y" ->
																																											img.get(
																																													i,
																																													sourceLayer,
																																													j
																																											);

																																									case "z" ->
																																											img.get(
																																													i,
																																													j,
																																													sourceLayer
																																											);

																																									default ->
																																											throw new IllegalStateException();
																																								};

																																						if (value > valueMin
																																								&& value < valueMax) {

																																							BlockPos bp =
																																									new BlockPos(
																																											playerPos.getX() - i,
																																											worldY,
																																											playerPos.getZ() - j
																																									);

																																							Block block =
																																									getScaleColorBlock(
																																											valueMin,
																																											valueMax,
																																											value,
																																											nColors
																																									);

																																							world.setBlock(
																																									bp,
																																									block.defaultBlockState(),
																																									2
																																							);

																																							blocksPlaced++;
																																						}
																																					}
																																				}
																																			}

																																		} catch (Exception e) {

																																			LOGGER.error(
																																					"Error while placing auto-colored medical image layers",
																																					e
																																			);

																																			context.getSource().sendFailure(
																																					Component.literal(
																																							"Failed: "
																																									+ e.getClass().getSimpleName()
																																									+ ": "
																																									+ e.getMessage()
																																					)
																																			);

																																			return 0;
																																		}

																																		int finalBlocksPlaced =
																																				blocksPlaced;

																																		context.getSource().sendSuccess(
																																				() -> Component.literal(
																																						"Finished. Placed "
																																								+ finalBlocksPlaced
																																								+ " blocks."
																																				),
																																				false
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
									.then(Commands.argument("start_layer", IntegerArgumentType.integer(1))
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

																						int layerNumber = IntegerArgumentType.getInteger(
																								context,
																								"start_layer"
																						);

																						int layer = layerNumber - 1;

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
																												.formatted(n, layerNumber, axis)
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

	// Function to return block for auto-color methods
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

		int colorStep = (int) Math.floor(
				normalized * nColors
		);

		if (colorStep == nColors) {
			colorStep = nColors - 1;
		}

		int paletteIndex = (int) Math.round(
				colorStep * 6.0 / (nColors - 1)
		);

		return HEAT_SCALE[paletteIndex];
	}

	private static boolean[][] findCentralComponent(
			boolean[][] mask,
			String selection,
			int connectivity,
			int minSize,
			int centerRadius
	) {

		int width =
				mask.length;

		int height =
				mask[0].length;

		boolean[][] visited =
				new boolean[width][height];

		double centerX =
				(width - 1) / 2.0;

		double centerY =
				(height - 1) / 2.0;


		int[][] directions4 = {
				{-1, 0},
				{1, 0},
				{0, -1},
				{0, 1}
		};

		int[][] directions8 = {
				{-1, -1},
				{-1, 0},
				{-1, 1},

				{0, -1},
				{0, 1},

				{1, -1},
				{1, 0},
				{1, 1}
		};

		int[][] directions =
				connectivity == 4
						? directions4
						: directions8;


		List<int[]> bestComponent =
				null;

		double bestDistance =
				Double.POSITIVE_INFINITY;

		int bestSize =
				-1;


		double maximumCenterDistanceSquared =
				centerRadius * centerRadius;


		for (int x = 0;
		     x < width;
		     x++) {

			for (int y = 0;
			     y < height;
			     y++) {


				if (!mask[x][y]
						|| visited[x][y]) {

					continue;
				}


				List<int[]> component =
						new ArrayList<>();

				ArrayDeque<int[]> queue =
						new ArrayDeque<>();


				queue.addLast(
						new int[]{x, y}
				);

				visited[x][y] =
						true;


				double componentDistance =
						Double.POSITIVE_INFINITY;


				while (!queue.isEmpty()) {

					int[] p =
							queue.removeFirst();

					int px =
							p[0];

					int py =
							p[1];


					component.add(p);

					double dx =
							px - centerX;

					double dy =
							py - centerY;

					double distance =
							dx * dx
									+ dy * dy;

					if (distance
							< componentDistance) {

						componentDistance =
								distance;
					}

					for (int[] direction :
							directions) {

						int nx =
								px
										+ direction[0];

						int ny =
								py
										+ direction[1];


						if (nx < 0
								|| nx >= width
								|| ny < 0
								|| ny >= height) {

							continue;
						}


						if (!visited[nx][ny]
								&& mask[nx][ny]) {

							visited[nx][ny] =
									true;

							queue.addLast(
									new int[]{
											nx,
											ny
									}
							);
						}
					}
				}

				// Ommit small noise
				if (component.size()
						< minSize) {

					continue;
				}


				switch (selection) {

					// Closest to center
					case "center" -> {

						if (componentDistance
								< bestDistance) {

							bestDistance =
									componentDistance;

							bestComponent =
									component;
						}
					}

					// Largest
					case "largest" -> {

						if (component.size()
								> bestSize) {

							bestSize =
									component.size();

							bestComponent =
									component;
						}
					}

					// Both
					case "largest_near_center" -> {

						boolean nearCenter =
								centerRadius == 0
										|| componentDistance
										<= maximumCenterDistanceSquared;


						if (nearCenter
								&& component.size()
								> bestSize) {

							bestSize =
									component.size();

							bestDistance =
									componentDistance;

							bestComponent =
									component;
						}
					}


					default ->
							throw new IllegalArgumentException(
									"Unknown component selection mode: "
											+ selection
							);
				}
			}
		}


		boolean[][] result =
				new boolean[width][height];


		if (bestComponent != null) {

			for (int[] p :
					bestComponent) {

				result[p[0]][p[1]] =
						true;
			}
		}


		return result;
	}

	// Function to not show full UID
	private static String shortUid(String uid) {

		if (uid == null || uid.isBlank()) {
			return "?";
		}

		int length = 16;

		if (uid.length() <= length) {
			return uid;
		}

		return "..." + uid.substring(
				uid.length() - length
		);
	}

}
