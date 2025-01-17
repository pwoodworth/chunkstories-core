package xyz.chunkstories.core.entity

import xyz.chunkstories.api.entity.Entity
import xyz.chunkstories.api.entity.traits.TraitCollidable
import xyz.chunkstories.api.world.cell.CellData

fun Entity.blocksWithin(): Collection<CellData> {
    val entityBox = traits[TraitCollidable::class]?.translatedBoundingBox ?: return emptyList()

    return world.getVoxelsWithin(entityBox).mapNotNull {
        if (it == null)
            return@mapNotNull null

        val cell = world.peekSafely(it.x, it.y, it.z)

        for (voxelBox in cell.voxel.getTranslatedCollisionBoxes(cell) ?: return@mapNotNull null) {
            if (voxelBox.collidesWith(entityBox))
                return@mapNotNull cell
        }
        null
    }
}