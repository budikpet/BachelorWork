package cz.budikpet.bachelorwork.screens.main

import org.mockito.Mockito

inline fun <reified T> mock() = Mockito.mock(T::class.java)