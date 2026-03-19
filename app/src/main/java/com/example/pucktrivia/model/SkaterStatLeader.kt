package com.example.pucktrivia.model

data class SkaterStatLeader(
    val id: Int,
    val firstName: String,
    val lastName: String,
    val sweaterNumber: Int?,
    val teamAbbrev: String,
    val position: String,
    val value: Double
)
