package server.domain.structs
import server.domain.structs.inventory.Equipment
import server.domain.structs.movement.Position
import server.domain.structs.movement.Velocity

final case class GameEntityState(
    position: Position,
    velocity: Velocity,
    equipment: Equipment
)

final case class GameEntity(
    id: String,
    state: GameEntityState
)
