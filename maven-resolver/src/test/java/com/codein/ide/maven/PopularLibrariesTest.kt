package com.codein.ide.maven

import com.codein.ide.maven.resolve.PopularLibraries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PopularLibrariesTest {

    @Test
    fun plainNamesFindTheirLibraries() {
        assertEquals("retrofit", PopularLibraries.match("retrofit").first().artifact)
        assertEquals("glide", PopularLibraries.match("Glide").first().artifact)
        assertEquals("room-runtime", PopularLibraries.match("room").first().artifact)
        val images = PopularLibraries.match("image loading").map { it.artifact }
        assertTrue(images.containsAll(listOf("glide", "coil")))
        assertTrue(PopularLibraries.match("com.squareup:retrofit").isEmpty())
        assertTrue(PopularLibraries.match("zzzz").isEmpty())
    }
}
