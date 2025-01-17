//
// This file is a part of the Chunk Stories Core codebase
// Check out README.md for more information
// Website: http://chunkstories.xyz
//

package xyz.chunkstories.core.generator;

import xyz.chunkstories.api.converter.MinecraftBlocksTranslator;
import xyz.chunkstories.api.math.Math2;
import xyz.chunkstories.api.math.random.SeededSimplexNoiseGenerator;
import xyz.chunkstories.api.voxel.Voxel;
import xyz.chunkstories.api.voxel.structures.McSchematicStructure;
import xyz.chunkstories.api.voxel.structures.Structure;
import xyz.chunkstories.api.world.World;
import xyz.chunkstories.api.world.chunk.Chunk;
import xyz.chunkstories.api.world.generator.WorldGenerator;
import xyz.chunkstories.api.world.generator.WorldGeneratorDefinition;
import org.joml.Vector3i;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class HorizonGenerator extends WorldGenerator {
	SeededSimplexNoiseGenerator ssng;

	int worldSizeInBlocks;

	protected int WATER_HEIGHT, MOUNTAIN_SCALE, BASE_HEIGHT_SCALE, PLATEAU_HEIGHT_SCALE;
	protected double MOUNTAIN_OFFSET;

	private Voxel AIR_VOXEL, STONE_VOXEL, UNDERGROUND_VOXEL, WATER_VOXEL;

	private Voxel GROUND_VOXEL, FOREST_GROUND_VOXEL, DRY_GROUND_VOXEL;

	private Voxel SAND, GRAVEL;

	private Voxel TALLGRASS;
	private Voxel[] SURFACE_DECORATIONS;

	final int OKA_TREE_VARIANTS = 6;
	Structure oakTrees[] = new Structure[OKA_TREE_VARIANTS];

	final int FALLEN_TREE_VARIANTS = 2;
	Structure fallenTrees[] = new Structure[FALLEN_TREE_VARIANTS];

	final int REDWOOD_TREE_VARIANTS = 3;
	Structure redwoodTrees[] = new Structure[REDWOOD_TREE_VARIANTS];

	Structure treeTypes[][] = { oakTrees, fallenTrees, redwoodTrees };

	CaveBuilder caveBuilder;

	public HorizonGenerator(WorldGeneratorDefinition type, World world) {
		super(type, world);
		ssng = new SeededSimplexNoiseGenerator(world.getWorldInfo().getSeed());
		worldSizeInBlocks = world.getSizeInChunks() * 32;

		caveBuilder = new CaveBuilder(world, this);

		this.WATER_HEIGHT = pint(type.resolveProperty("waterHeight"), 48);
		this.MOUNTAIN_OFFSET = pdouble(type.resolveProperty("mountainOffset"), 0.3);
		this.MOUNTAIN_SCALE = pint(type.resolveProperty("mountainScale"), 128);
		this.BASE_HEIGHT_SCALE = pint(type.resolveProperty("baseHeightScale"), 64);
		this.PLATEAU_HEIGHT_SCALE = pint(type.resolveProperty("plateauHeightScale"), 64);

		this.AIR_VOXEL = world.getGameContext().getContent().voxels().air();
		this.STONE_VOXEL = world.getGameContext().getContent().voxels().getVoxel("stone");
		this.WATER_VOXEL = world.getGameContext().getContent().voxels().getVoxel("water");
		this.GROUND_VOXEL = world.getGameContext().getContent().voxels().getVoxel("grass");
		this.FOREST_GROUND_VOXEL = world.getGameContext().getContent().voxels().getVoxel("forestgrass");
		this.DRY_GROUND_VOXEL = world.getGameContext().getContent().voxels().getVoxel("drygrass");
		this.UNDERGROUND_VOXEL = world.getGameContext().getContent().voxels().getVoxel("dirt");
		this.TALLGRASS = world.getGameContext().getContent().voxels().getVoxel("grass_prop");

		this.SAND = world.getGameContext().getContent().voxels().getVoxel("sand");
		this.GRAVEL = world.getGameContext().getContent().voxels().getVoxel("gravel");

		try {
			MinecraftBlocksTranslator translator = new MinecraftBlocksTranslator(world.getGameContext(),
					new File("converter_mapping.txt"));

			for (int i = 1; i <= OKA_TREE_VARIANTS; i++)
				oakTrees[i - 1] = McSchematicStructure
						.fromAsset(world.getContent().getAsset("./structures/oak_tree" + i + ".schematic"), translator);

			for (int i = 1; i <= FALLEN_TREE_VARIANTS; i++)
				fallenTrees[i - 1] = McSchematicStructure.fromAsset(
						world.getContent().getAsset("./structures/oak_fallen" + i + ".schematic"), translator);

			for (int i = 1; i <= REDWOOD_TREE_VARIANTS; i++)
				redwoodTrees[i - 1] = McSchematicStructure.fromAsset(
						world.getContent().getAsset("./structures/redwood_tree" + i + ".schematic"), translator);

			String decorations[] = new String[] { "flower_yellow", "flower_red", "flower_orange", "flower_blue",
					"flower_purple", "flower_white", "mushroom_red", "mushroom_brown" };
			SURFACE_DECORATIONS = new Voxel[decorations.length];
			for (int i = 1; i <= REDWOOD_TREE_VARIANTS; i++)
				SURFACE_DECORATIONS[i] = world.getGameContext().getContent().voxels().getVoxel(decorations[i]);

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private double pdouble(String resolveProperty, double d) {
		try {
			if (resolveProperty != null)
				return Double.parseDouble(resolveProperty);
		} catch (NumberFormatException nfe) {
		}
		return d;
	}

	private int pint(String resolveProperty, int i) {
		try {
			if (resolveProperty != null)
				return Integer.parseInt(resolveProperty);
		} catch (NumberFormatException nfe) {
		}
		return i;
	}

	static class SliceData {
		int heights[] = new int[1024];

		float forestness[] = new float[1024];
		float dryness[] = new float[1024];

		List<StructureToPaste> structures = new ArrayList<>();
	}

	static class StructureToPaste {
		Structure structure;
		Vector3i position;
		int flags;

		public StructureToPaste(Structure structure, Vector3i position, int flags) {
			this.structure = structure;
			this.position = position;
			this.flags = flags;
		}
	}

	@Override
	public void generateWorldSlice(Chunk[] chunks) {

		int cx = chunks[0].getChunkX();
		int cz = chunks[0].getChunkZ();

		Random rnd = new Random();
		SliceData sliceData = new SliceData();

		// Generate the heights in advance!
		for (int x = 0; x < 32; x++)
			for (int z = 0; z < 32; z++) {
				sliceData.heights[x * 32 + z] = getHeightAtInternal(cx * 32 + x, cz * 32 + z);

				sliceData.forestness[x * 32 + z] = getForestness(cx * 32 + x, cz * 32 + z);
			}

		caveBuilder.generateCaves(cx, cz, rnd, sliceData);

		for (int chunkY = 0; chunkY < chunks.length; chunkY++) {
			generateChunk(chunks[chunkY], rnd, sliceData);
		}

		int maxheight = chunks.length * 32 - 1;
		for (int x = 0; x < 32; x++)
			for (int z = 0; z < 32; z++) {
				int y = maxheight;
				int worldY = sliceData.heights[x * 32 + z];

				while (y > 0 && chunks[y / 32].peekSimple(x, y, z) == AIR_VOXEL) {
					y--;
				}
				int groundHeightActual = y;

				// It's flooded!
				if (groundHeightActual < WATER_HEIGHT && worldY < WATER_HEIGHT) {
					int waterY = WATER_HEIGHT;
					while (waterY > 0 && chunks[waterY / 32].peekSimple(x, waterY, z) == AIR_VOXEL) {
						chunks[waterY / 32].pokeSimpleSilently(x, waterY, z, WATER_VOXEL, -1, -1, 0);
						waterY--;
					}
				} else {
					// Top soil
					Voxel topVoxel;
					float forestIntensity = sliceData.forestness[x * 32 + z];
					if (Math.random() < (forestIntensity - 0.5) * 1.5f && forestIntensity > 0.5)
						topVoxel = FOREST_GROUND_VOXEL; // ground
					else
						topVoxel = GROUND_VOXEL;
					Voxel groundVoxel = UNDERGROUND_VOXEL;

					// if we're close to water level make the ground sand
					if (worldY < WATER_HEIGHT + 3) {
						topVoxel = SAND;
						groundVoxel = SAND;
					}

					chunks[groundHeightActual / 32].pokeSimpleSilently(x, groundHeightActual, z, topVoxel, -1, -1, 0);
					// 3 blocks of dirt underneath it
					int undergroundDirt = groundHeightActual - 1;
					while (undergroundDirt >= 0 && undergroundDirt >= groundHeightActual - 3
							&& chunks[undergroundDirt / 32].peekSimple(x, undergroundDirt, z) == STONE_VOXEL) {
						chunks[undergroundDirt / 32].pokeSimpleSilently(x, undergroundDirt, z, groundVoxel, -1, -1, 0);
						undergroundDirt--;
					}

					// Decoration shrubs flowers etc
					int surface = groundHeightActual + 1;
					if (surface > maxheight || topVoxel == SAND)
						continue;

					double bushChance = Math.random();
					if (topVoxel != FOREST_GROUND_VOXEL || Math.random() > 0.8) {
						if (bushChance > 0.5) {
							if (bushChance > 0.95) {
								Voxel surfaceVoxel = SURFACE_DECORATIONS[rnd.nextInt(SURFACE_DECORATIONS.length)];
								chunks[surface / 32].pokeSimpleSilently(x, surface, z, surfaceVoxel, -1, -1, 0);
							} else
								chunks[surface / 32].pokeSimpleSilently(x, surface, z, TALLGRASS, -1, -1, 0);
						}
					}
				}
			}

		sliceData.structures.clear();
		addTrees(cx, cz, rnd, sliceData);

		for (int chunkY = 0; chunkY < chunks.length; chunkY++) {
			final int cy = chunkY;
			sliceData.structures.forEach(stp -> stp.structure.paste(chunks[cy], stp.position, stp.flags));
		}
	}

	@Override
	public void generateChunk(Chunk chunk) {
		throw new UnsupportedOperationException();
	}

	public void generateChunk(Chunk chunk, Random rnd, SliceData sliceData) {

		int cy = chunk.getChunkY();

		if (chunk.getChunkY() * 32 >= 384)
			return;

		Voxel voxel = null;
		for (int x = 0; x < 32; x++)
			for (int z = 0; z < 32; z++) {
				int groundHeight = sliceData.heights[x * 32 + z]/* getHeightAtInternal(cx * 32 + x, cz * 32 + z) */;

				Voxel groundVoxel = UNDERGROUND_VOXEL;

				// if we're close to water level make the ground sand
				if (groundHeight > WATER_HEIGHT - 9 && groundHeight < WATER_HEIGHT) {
					groundVoxel = GRAVEL;
				}
				if (groundHeight > WATER_HEIGHT - 3 && groundHeight < WATER_HEIGHT) {
					groundVoxel = SAND;
				}

				int y = cy * 32;
				while (y < cy * 32 + 32 && y <= groundHeight) {
					if (groundHeight - y > 3) // more than 3 blocks underground => deep ground
						voxel = STONE_VOXEL;
					else
						voxel = groundVoxel;

					chunk.pokeSimpleSilently(x, y, z, voxel, -1, -1, 0);
					y++;
				}
			}

		for (StructureToPaste pm : sliceData.structures) {
			pm.structure.paste(chunk, pm.position, pm.flags);
		}
	}

	private void addTrees(int cx, int cz, Random rnd, SliceData data) {
		// take into account the nearby chunks
		for (int gcx = cx - 1; gcx <= cx + 1; gcx++)
			for (int gcz = cz - 1; gcz <= cz + 1; gcz++) {
				rnd.setSeed(gcx * 32 + gcz + 48716148);

				// how many trees are there supposed to be in that chunk
				// float treenoise = 0.5f + fractalNoise(gcx * 32 + 2, gcz * 32 + 32, 3, 0.25f,
				// 0.85f);

				int ntrees = 25;

				for (int i = 0; i < ntrees; i++) {
					int x = gcx * 32 + rnd.nextInt(32);
					int z = gcz * 32 + rnd.nextInt(32);

					float forestness = getForestness(x, z);
					if (rnd.nextFloat() < forestness) {

						int y = getHeightAtInternal(x, z) - 1;
						if (y > WATER_HEIGHT) {

							int type = rnd.nextInt(treeTypes.length);

							Structure[] treeType = treeTypes[type];

							int variant = rnd.nextInt(treeType.length);
							data.structures.add(new StructureToPaste(treeType[variant], new Vector3i(x, y, z),
									Structure.FLAG_USE_OFFSET | Structure.FLAG_DONT_OVERWRITE_AIR));
							// treeType[variant].paste(chunk, new Vector3i(x, y, z),
							// Structure.FLAG_USE_OFFSET | Structure.FLAG_DONT_OVERWRITE_AIR);
						}

					}
				}
			}
	}

	float fractalNoise(int x, int z, int octaves, float freq, float persistence) {
		float total = 0.0f;
		float maxAmplitude = 0.0f;
		float amplitude = 1.0f;
		freq *= worldSizeInBlocks / (64 * 32);
		for (int i = 0; i < octaves; i++) {
			total += ssng.looped_noise(x * freq, z * freq, worldSizeInBlocks) * amplitude;
			freq *= 2.0f;
			maxAmplitude += amplitude;
			amplitude *= persistence;
		}
		return total / maxAmplitude;
	}

	float ridgedNoise(int x, int z, int octaves, float freq, float persistence) {
		float total = 0.0f;
		float maxAmplitude = 0.0f;
		float amplitude = 1.0f;
		freq *= worldSizeInBlocks / (64 * 32);
		for (int i = 0; i < octaves; i++) {
			total += (1.0f - Math.abs(ssng.looped_noise(x * freq, z * freq, worldSizeInBlocks))) * amplitude;
			freq *= 2.0f;
			maxAmplitude += amplitude;
			amplitude *= persistence;
		}
		return total / maxAmplitude;
	}

	int getHeightAtInternal(int x, int z) {
		float height = 0.0f;

		float baseHeight = ridgedNoise(x, z, 5, 1.0f, 0.5f);

		height += baseHeight * BASE_HEIGHT_SCALE;

		float mountainFactor = fractalNoise(x + 548, z + 330, 3, 0.5f, 0.5f);
		mountainFactor *= 1.0 + 0.125 * ridgedNoise(x + 14, z + 9977, 2, 4.0f, 0.7f);
		mountainFactor -= MOUNTAIN_OFFSET;
		mountainFactor /= (1 - MOUNTAIN_OFFSET);
		mountainFactor = Math2.clamp(mountainFactor, 0.0f, 1.0f);

		height += mountainFactor * MOUNTAIN_SCALE;

		float plateauHeight = Math2.clamp(fractalNoise(x + 225, z + 321, 3, 1, 0.5f) * 32.0f - 8.0f, 0.0f, 1.0f);
		plateauHeight *= Math2.clamp(fractalNoise(x + 3158, z + 9711, 3, 0.125f, 0.5f) * 0.5f + 0.5f, 0.0f, 1.0f);

		if (height >= WATER_HEIGHT)
			height += plateauHeight * PLATEAU_HEIGHT_SCALE;
		else
			height += plateauHeight * baseHeight * PLATEAU_HEIGHT_SCALE;

		// height = 64 + plateauHeight * PLATEAU_HEIGHT_SCALE;

		return (int) height;
	}

	private float getForestness(int x, int z) {

		float mountainFactor = fractalNoise(x + 548, z + 330, 3, 0.5f, 0.5f);
		mountainFactor *= 1.0 + 0.125 * ridgedNoise(x + 14, z + 9977, 2, 4.0f, 0.7f);

		mountainFactor -= 0.3;
		mountainFactor *= 2.0;

		mountainFactor = Math2.clamp(mountainFactor, 0.0f, 2.0f);

		float f = 0.1f + Math2.clamp(4.0 * (fractalNoise(x + 1397, z + 321, 3, 0.5f, 0.5f)), 0.0f, 1.0f);

		f -= mountainFactor * 0.45;

		return Math2.clamp(f, 0, 1);
	}
}
