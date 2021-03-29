package com.example.weatherapp.models

import java.io.Serializable

data class Sys (
    val type: Int,
    val message: String,
    val country: String,
    val sunrise: Long,
    val sunset: Long
): Serializable