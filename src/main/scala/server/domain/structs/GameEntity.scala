package server.domain.structs
import server.domain.structs.movement.Position
import server.domain.structs.movement.Velocity

final case class GameEntityState(
    position: Position,
    velocity: Velocity
)

final case class GameEntity(
    id: String,
    state: GameEntityState
)
