package com.example.pucktrivia.model

enum class QuestionType(
    val statKey: String,
    val positionGroup: PositionGroup?,
    val questionText: String,
    val unitLabel: String,
    val poolFraction: Double = 0.5,
    val minWins: Int = 0,
) {
    DEFENDERS_POINTS(
        statKey = "points",
        positionGroup = PositionGroup.DEFENDERS,
        questionText = "Which of these defenders currently has the most points?",
        unitLabel = "pts",
    ),
    FORWARDS_POINTS(
        statKey = "points",
        positionGroup = PositionGroup.FORWARDS,
        questionText = "Which of these forwards currently has the most points?",
        unitLabel = "pts",
    ),
    DEFENDERS_GOALS(
        statKey = "goals",
        positionGroup = PositionGroup.DEFENDERS,
        questionText = "Which of these defenders currently has the most goals?",
        unitLabel = "g",
    ),
    FORWARDS_GOALS(
        statKey = "goals",
        positionGroup = PositionGroup.FORWARDS,
        questionText = "Which of these forwards currently has the most goals?",
        unitLabel = "g",
    ),
    GOALIES_SAVE_PCT(
        statKey = "savePctg",
        positionGroup = null,
        questionText = "Which of these goalies currently has the highest save percentage?",
        unitLabel = "",
        poolFraction = 1.0,
        minWins = 10,
    ),
}
