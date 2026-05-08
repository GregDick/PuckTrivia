package com.example.pucktrivia.model

enum class QuestionType(
    val statKey: String,
    val positionGroup: PositionGroup?,
    private val regularSeasonText: String,
    private val playoffsText: String,
    val unitLabel: String,
    val poolFraction: Double = 0.5,
) {
    DEFENDERS_POINTS(
        statKey = "points",
        positionGroup = PositionGroup.DEFENDERS,
        regularSeasonText = "Which of these defenders currently has the most points?",
        playoffsText = "Which of these defenders has the most playoff points?",
        unitLabel = "pts",
    ),
    FORWARDS_POINTS(
        statKey = "points",
        positionGroup = PositionGroup.FORWARDS,
        regularSeasonText = "Which of these forwards currently has the most points?",
        playoffsText = "Which of these forwards has the most playoff points?",
        unitLabel = "pts",
    ),
    DEFENDERS_GOALS(
        statKey = "goals",
        positionGroup = PositionGroup.DEFENDERS,
        regularSeasonText = "Which of these defenders currently has the most goals?",
        playoffsText = "Which of these defenders has the most playoff goals?",
        unitLabel = "g",
    ),
    FORWARDS_GOALS(
        statKey = "goals",
        positionGroup = PositionGroup.FORWARDS,
        regularSeasonText = "Which of these forwards currently has the most goals?",
        playoffsText = "Which of these forwards has the most playoff goals?",
        unitLabel = "g",
    ),
    GOALIES_SAVE_PCT(
        statKey = "savePctg",
        positionGroup = null,
        regularSeasonText = "Which of these goalies currently has the highest save percentage?",
        playoffsText = "Which of these goalies has the highest playoff save percentage?",
        unitLabel = "",
        poolFraction = 1.0,
    );

    fun questionText(mode: SeasonMode): String =
        when (mode) {
            SeasonMode.RegularSeason -> regularSeasonText
            SeasonMode.Playoffs -> playoffsText
        }
}
