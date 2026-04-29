package com.roideuniverse.loghound.plugindsl

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

fun <T> stateOf(initial: T): MutableState<T> = mutableStateOf(initial)
