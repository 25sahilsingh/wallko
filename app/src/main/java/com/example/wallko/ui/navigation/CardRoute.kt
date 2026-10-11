package com.example.wallko.ui.navigation

import kotlinx.serialization.Serializable

/** Full-page plant card (ui/card/CardScreen) for an owned plant id. */
@Serializable
data class CardRoute(val plantId: Long)
