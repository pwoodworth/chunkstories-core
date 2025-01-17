package xyz.chunkstories.core.voxel

import org.joml.Matrix4f
import xyz.chunkstories.api.graphics.MeshMaterial
import xyz.chunkstories.api.graphics.representation.Model
import xyz.chunkstories.api.voxel.Voxel
import xyz.chunkstories.api.voxel.VoxelDefinition
import xyz.chunkstories.api.voxel.VoxelSide

class VoxelSmallPlant(definition: VoxelDefinition) : Voxel(definition) {
    val model: Model = definition.store.parent().models["voxels/blockmodels/grass_prop/grass_prop.dae"]

    init {

        val mappedOverrides = mapOf(0 to MeshMaterial("material", mapOf("albedoTexture" to "voxels/textures/${this.voxelTextures[VoxelSide.FRONT.ordinal].name}.png")))

        customRenderingRoutine = { cell ->
            val matrix = Matrix4f()

            matrix.translate(0.5f, 0f, 0.5f)
            matrix.rotate(Math.PI.toFloat() * 2f * Math.random().toFloat(), 0f, 1f, 0f)
            matrix.translate(-0.5f, 0f, -0.5f)

            matrix.translate((-0.5f + Math.random().toFloat()) * 0.25f, 0f, (-0.5f + Math.random().toFloat()) * 0.25f)

            addModel(model, matrix, mappedOverrides)
        }
    }
}