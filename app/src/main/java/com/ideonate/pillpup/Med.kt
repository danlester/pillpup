package com.ideonate.pillpup

data class Med(
    val id: String,
    val name: String,
    val hour: Int,
    val minute: Int,
    val createdDay: String
)

enum class DoseStatus { TAKEN, SKIPPED }

data class DoseRecord(val status: DoseStatus, val atMillis: Long)
