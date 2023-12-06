package server.domain.structs

final case class GameEntityPosition(
    x: Float,
    y: Float
)

final case class GameEntityState(
    position: GameEntityPosition
)

final case class GameEntity(
    id: String,
    state: GameEntityState
)
