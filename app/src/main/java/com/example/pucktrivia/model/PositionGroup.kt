package com.example.pucktrivia.model

enum class PositionGroup {
    DEFENDERS,
    FORWARDS,
}

fun SkaterStatLeader.positionGroup(): PositionGroup? =
    when (position) {
        "D" -> PositionGroup.DEFENDERS
        "C",
        "L",
        "R" -> PositionGroup.FORWARDS
        else -> null
    }
