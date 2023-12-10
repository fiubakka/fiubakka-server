package server.domain.structs

final case class GameEntityPosition(
    x: Float,
    y: Float
)

final case class GameEntityVelocity(
    velX: Float,
    velY: Float
)

final case class GameEntityState(
    position: GameEntityPosition,
    velocity: GameEntityVelocity
)

final case class GameEntity(
    id: String,
    state: GameEntityState
)
