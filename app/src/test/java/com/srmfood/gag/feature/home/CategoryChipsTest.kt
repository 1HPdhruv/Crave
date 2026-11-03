package com.srmfood.gag.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryChipsTest {
    private val ten = (1..10).map { "c$it" }

    @Test fun `collapsed shows exactly the first five in order`() =
        assertEquals(listOf("c1", "c2", "c3", "c4", "c5"), visibleCategories(ten, expanded = false))

    @Test fun `expanded shows every category in order`() = assertEquals(ten, visibleCategories(ten, expanded = true))

    @Test fun `five or fewer are never truncated`() {
        assertEquals(ten.take(5), visibleCategories(ten.take(5), expanded = false))
        assertEquals(ten.take(3), visibleCategories(ten.take(3), expanded = false))
        assertEquals(emptyList<String>(), visibleCategories(emptyList<String>(), expanded = false))
    }

    @Test fun `six categories collapse to five`() = assertEquals(5, visibleCategories(ten.take(6), expanded = false).size)
}
