package suprk.com;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import niftijio.NiftiVolume;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class MedicalImages implements ModInitializer {
	public static final String MOD_ID = "medicalimages";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

        try {
            NiftiVolume volume = CTLoader.load();

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

		LOGGER.info("MedicalImages loaded!");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
