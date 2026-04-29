package com.example.pucktrivia.model

data class GoalieStatLeader(
    override val id: Int,
    override val firstName: String,
    override val lastName: String,
    override val sweaterNumber: Int?,
    override val teamAbbrev: String,
    override val value: Double,
) : StatLeader {
    override val displayValue: String
        get() = "%.3f".format(value)
}
