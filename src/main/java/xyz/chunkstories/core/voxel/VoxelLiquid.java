//
// This file is a part of the Chunk Stories Core codebase
// Check out README.md for more information
// Website: http://chunkstories.xyz
//

package xyz.chunkstories.core.voxel;

import xyz.chunkstories.api.voxel.Voxel;
import xyz.chunkstories.api.voxel.VoxelDefinition;
import xyz.chunkstories.api.world.cell.CellData;

public class VoxelLiquid extends Voxel {
	//VoxelWaterRenderer surface;
	//VoxelWaterRenderer inside;

	public VoxelLiquid(VoxelDefinition type) {
		super(type);
		//inside = new VoxelWaterRenderer(store.models().getVoxelModel("water.inside"));
		//surface = new VoxelWaterRenderer(store.models().getVoxelModel("water.surface"));
	}

	/*
	@Override
	public VoxelWaterRenderer getVoxelRenderer(CellData info) {
		// Return the surface only if the top block isn't liquid
		if (!info.getNeightborVoxel(4).getDefinition().isLiquid())
			return surface;
		else
			return inside;
	}*/
}