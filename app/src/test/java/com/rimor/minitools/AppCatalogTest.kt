package com.rimor.minitools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The grid's order is a pure function, so it is tested as one. All of the behaviour worth
 * arguing about — what favourites do to a sort, what hiding does to a favourite — lives here
 * rather than in the composable that draws the result.
 */
class AppCatalogTest {

    private fun app(name: String, installed: Long = 0L) =
        LaunchableApp(
            packageName = "pkg.${name.lowercase()}",
            activityName = "pkg.${name.lowercase()}.Main",
            label = name,
            installedAt = installed,
        )

    private val zebra = app("Zebra", installed = 300)
    private val apple = app("Apple", installed = 100)
    private val mango = app("Mango", installed = 200)
    private val all = listOf(zebra, apple, mango)

    private fun labels(
        order: SortOrder,
        favourites: Set<String> = emptySet(),
        hidden: Set<String> = emptySet(),
        lastUsed: Map<String, Long> = emptyMap(),
    ) = AppCatalog.arrange(all, order, favourites, hidden, lastUsed).map { it.label }

    @Test
    fun `A to Z is alphabetical`() {
        assertEquals(listOf("Apple", "Mango", "Zebra"), labels(SortOrder.AZ))
    }

    @Test
    fun `Z to A is the reverse`() {
        assertEquals(listOf("Zebra", "Mango", "Apple"), labels(SortOrder.ZA))
    }

    @Test
    fun `newest install first`() {
        assertEquals(listOf("Zebra", "Mango", "Apple"), labels(SortOrder.INSTALLED))
    }

    @Test
    fun `most recently used first, and never used falls to the back alphabetically`() {
        val used = mapOf(apple.packageName to 900L, zebra.packageName to 500L)
        assertEquals(listOf("Apple", "Zebra", "Mango"), labels(SortOrder.USED, lastUsed = used))
    }

    /** The point of a favourite: it comes first even when the sort disagrees. */
    @Test
    fun `favourites lead every order`() {
        SortOrder.entries.forEach { order ->
            val result = labels(order, favourites = setOf(zebra.packageName))
            assertEquals("$order did not lift the favourite", "Zebra", result.first())
            assertEquals(3, result.size)
        }
    }

    /** And they are still sorted among themselves, not left in starring order. */
    @Test
    fun `favourites are sorted inside their own group`() {
        val starred = setOf(zebra.packageName, apple.packageName)
        assertEquals(listOf("Apple", "Zebra", "Mango"), labels(SortOrder.AZ, favourites = starred))
        assertEquals(listOf("Zebra", "Apple", "Mango"), labels(SortOrder.ZA, favourites = starred))
    }

    @Test
    fun `hidden apps are not listed`() {
        val result = labels(SortOrder.AZ, hidden = setOf(mango.packageName))
        assertEquals(listOf("Apple", "Zebra"), result)
        assertFalse(result.contains("Mango"))
    }

    /** Hiding wins over starring, or an app could be both favourite and gone. */
    @Test
    fun `hiding beats favouriting`() {
        val result = labels(
            SortOrder.AZ,
            favourites = setOf(mango.packageName),
            hidden = setOf(mango.packageName),
        )
        assertEquals(listOf("Apple", "Zebra"), result)
    }

    @Test
    fun `sort order survives a round trip through its own name`() {
        SortOrder.entries.forEach { assertEquals(it, SortOrder.from(it.name)) }
        assertEquals(SortOrder.AZ, SortOrder.from(null))
        assertEquals(SortOrder.AZ, SortOrder.from("nonsense"))
    }

    @Test
    fun `every order has a label short enough for the header`() {
        SortOrder.entries.forEach {
            assertTrue("${it.name} has no label", it.label.isNotBlank())
            assertTrue("${it.label} is too long for the corner", it.label.length <= 5)
        }
    }
}
