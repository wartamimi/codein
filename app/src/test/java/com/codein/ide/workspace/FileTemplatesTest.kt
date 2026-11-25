package com.codein.ide.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileTemplatesTest {
    @Test
    fun viewsActivityGetsLayoutAndManifestEntry() {
        val out = FileTemplates.generate(FileTemplateKind.ViewsActivity, "app/src/main/java/com/example/app/ui", "DetailActivity", "com.example.app")
        val java = out.files.getValue("app/src/main/java/com/example/app/ui/DetailActivity.java")
        assertTrue(java.startsWith("package com.example.app.ui;\n\nimport android.os.Bundle;\n"))
        assertTrue(java.contains("import com.example.app.R;"))
        assertTrue(java.contains("setContentView(R.layout.activity_detail);"))
        assertTrue("app/src/main/res/layout/activity_detail.xml" in out.files)
        assertEquals("com.example.app.ui.DetailActivity", out.activityToRegister)
        val manifest = "<manifest>\n    <application>\n        <activity android:name=\".MainActivity\" />\n    </application>\n</manifest>\n"
        val registered = FileTemplates.registerActivity(manifest, out.activityToRegister!!, "com.example.app")
        assertTrue(registered.contains("<activity android:name=\".ui.DetailActivity\" android:exported=\"false\" />"))
        assertEquals(registered, FileTemplates.registerActivity(registered, out.activityToRegister!!, "com.example.app"))
    }

    @Test
    fun kotlinFragmentInNamespacePackageNeedsNoRImport() {
        val out = FileTemplates.generate(FileTemplateKind.Fragment, "app/src/main/kotlin/com/example/app", "HomeFragment", "com.example.app")
        val kt = out.files.getValue("app/src/main/kotlin/com/example/app/HomeFragment.kt")
        assertEquals("package com.example.app\n\nimport androidx.fragment.app.Fragment\n\nclass HomeFragment : Fragment(R.layout.fragment_home)\n", kt)
    }
}
