//
// This file is a part of the Chunk Stories Core codebase
// Check out README.md for more information
// Website: http://chunkstories.xyz
//

package xyz.chunkstories.core.entity.traits

import xyz.chunkstories.api.entity.Entity
import xyz.chunkstories.api.entity.traits.Trait
import xyz.chunkstories.api.entity.traits.TraitCollidable
import xyz.chunkstories.api.entity.traits.serializable.TraitHealth
import xyz.chunkstories.api.entity.traits.serializable.TraitRotation
import xyz.chunkstories.api.entity.traits.serializable.TraitVelocity
import xyz.chunkstories.api.world.cell.CellData
import xyz.chunkstories.core.voxel.VoxelLiquid
import org.joml.Vector2d
import org.joml.Vector2f
import org.joml.Vector3d
import org.joml.Vector3dc

open class TraitBasicMovement(entity: Entity) : Trait(entity) {

    var acceleration = Vector3d()
    var targetVelocity = Vector3d(0.0)

    val isInWater: Boolean
        get() {
            for (cell in entity.world.getVoxelsWithin(entity.getTranslatedBoundingBox())) {
                if (cell is VoxelLiquid)
                    return true
            }
            return false
        }

    open fun tick() {
        val collisions = entity.traits[TraitCollidable::class.java]

        val entityVelocity = entity.traits[TraitVelocity::class.java]
        val entityRotation = entity.traits[TraitRotation::class.java]

        if (collisions == null || entityVelocity == null || entityRotation == null)
            return

        // Unloaded chunk ? nothing moves!
        if (entity.world.getChunkWorldCoordinates(entity.location) == null) {
            entityVelocity.setVelocity(Vector3d(0.0))
            acceleration.set(0.0)
            return
        }

        collisions.unstuck()

        val ogVelocity = entityVelocity.velocity
        val velocity = Vector3d(ogVelocity)

        // Applies head movement force and falloff
        val headRotationVelocity = entityRotation.tickInpulse()
        entityRotation.addRotation(headRotationVelocity.x().toDouble(), headRotationVelocity.y().toDouble())

        val inWater = isInWater

        if(entity.traits[TraitHealth::class]?.let { it.isDead } == true)
            targetVelocity = Vector3d(0.0)

        acceleration = Vector3d(targetVelocity.x() - velocity.x(), 0.0, targetVelocity.z() - velocity.z())

        // Limit maximal acceleration depending if we're on the groud or not, we
        // accelerate 2x faster on ground
        var maxAcceleration = if (collisions.isOnGround) 0.010 else 0.005
        if (inWater)
            maxAcceleration = 0.005
        if (acceleration.length() > maxAcceleration) {
            acceleration.normalize()
            acceleration.mul(maxAcceleration)
        }

        // Gravity
        val terminalVelocity = if (inWater) -0.05 else -0.5
        if (velocity.y() > terminalVelocity)
            velocity.y = velocity.y() - 0.008
        if (velocity.y() < terminalVelocity)
            velocity.y = terminalVelocity

        // Water limits your overall movement
        // TODO cleanup & generalize
        val targetSpeedInWater = 0.02
        if (inWater) {
            if (velocity.length() > targetSpeedInWater) {
                var decelerationThen = Math.pow(velocity.length() - targetSpeedInWater, 1.0)

                val maxDeceleration = 0.006
                if (decelerationThen > maxDeceleration)
                    decelerationThen = maxDeceleration

                acceleration.add(Vector3d(velocity).normalize().negate().mul(decelerationThen))
            }
        }

        // Acceleration
        velocity.add(acceleration)

        // Eventually moves
        val remainingToMove = entity.world.collisionsManager.tryMovingEntityWithCollisions(entity,
                entity.location, velocity)
        var remaining2d = Vector2d(remainingToMove.x(), remainingToMove.z())

        // Auto-step logic
        if (remaining2d.length() > 0.001 && collisions.isOnGround) {
            // Cap max speed we can get through the bump ?
            if (remaining2d.length() > 0.20) {
                println("Too fast, capping")
                remaining2d.normalize()
                remaining2d.mul(0.20)
            }

            // Get whatever we are colliding with
            // Test if setting yourself on top would be ok
            // Do it if possible
            // TODO remake proper
            val blockedMomentum = Vector3d(remaining2d.x(), 0.0, remaining2d.y())
            var d = 0.25
            while (d < 0.5) {
                // I don't want any of this to reflect on the object, because it causes ugly
                // jumps in the animation
                val canMoveUp = entity.world.collisionsManager.runEntityAgainstWorldVoxelsAndEntities(entity,
                        entity.location, Vector3d(0.0, d, 0.0))
                // It can go up that bit
                if (canMoveUp.length() == 0.0) {
                    // Would it help with being stuck ?
                    val tryFromHigher = Vector3d(entity.location)
                    tryFromHigher.add(Vector3d(0.0, d, 0.0))
                    val blockedMomentumRemaining = entity.world.collisionsManager
                            .runEntityAgainstWorldVoxelsAndEntities(entity, tryFromHigher, blockedMomentum)
                    // If length of remaining momentum < of what we requested it to do, that means
                    // it *did* go a bit further away
                    if (blockedMomentumRemaining.length() < blockedMomentum.length()) {
                        // Where would this land ?
                        val afterJump = Vector3d(tryFromHigher)
                        afterJump.add(blockedMomentum)
                        afterJump.sub(blockedMomentumRemaining)

                        // land distance = whatever is left of our -0.55 delta when it hits the ground
                        val landDistance = entity.world.collisionsManager
                                .runEntityAgainstWorldVoxelsAndEntities(entity, afterJump, Vector3d(0.0, -d, 0.0))
                        afterJump.add(Vector3d(0.0, -d, 0.0))
                        afterJump.sub(landDistance)

                        entity.traitLocation.set(afterJump)
                        // this.setLocation(new Location(world, afterJump));

                        remaining2d = Vector2d(blockedMomentumRemaining.x(), blockedMomentumRemaining.z())
                        break
                    }
                }
                d += 0.05
            }
        }

        // Collisions, snap to axises
        if (Math.abs(remaining2d.x()) >= 0.001)
            velocity.x = 0.0
        if (Math.abs(remaining2d.y()) >= 0.001)
            velocity.z = 0.0
        // Stap it
        if (collisions.isOnGround)
            velocity.y = 0.0

        entityVelocity.setVelocity(velocity)
    }

    fun jump(force: Double) {
        entity.traits[TraitVelocity::class]?.let {ev ->
            val velocity = ev.velocity
            velocity.y += force
        }
    }
}