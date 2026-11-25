package com.codein.ide.maven.resolve

import com.codein.ide.maven.model.MavenRepository
import com.codein.ide.maven.model.SearchHit
import com.codein.ide.maven.net.MavenHttpClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Online artifact search:
 * - Maven Central via the Solr search API.
 * - Google Maven via `master-index.xml` + per-group `group-index.xml` (no search API exists).
 * - JitPack via its `com.github.<user>` build API when the query looks like `user/repo`.
 */
@Singleton
class MavenSearchService @Inject constructor(private val http: MavenHttpClient) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var googleGroups: List<String>? = null

    suspend fun search(query: String, repositories: Set<String>, limit: Int = 30): List<SearchHit> = coroutineScope {
        val q = query.trim()
        if (q.length < 2) return@coroutineScope emptyList()
        val tasks = buildList {
            if (MavenRepository.Central.id in repositories) add(async { runCatching { searchCentral(q, limit) }.getOrDefault(emptyList()) })
            if (MavenRepository.Google.id in repositories) add(async { runCatching { searchGoogle(q, limit) }.getOrDefault(emptyList()) })
            if (MavenRepository.JitPack.id in repositories) add(async { runCatching { searchJitPack(q) }.getOrDefault(emptyList()) })
        }
        // Everyday names ("retrofit", "image loading") first, with the repository's latest version when it answers.
        val popular = PopularLibraries.match(q).take(6).map { lib ->
            async {
                val latest = runCatching { latestVersion(lib.group, lib.artifact, lib.repository) }.getOrNull()
                SearchHit(lib.group, lib.artifact, latest ?: lib.fallbackVersion, lib.repository, description = lib.description)
            }
        }
        val popularHits = popular.awaitAll()
        val all = tasks.awaitAll().flatten()
        val lower = q.lowercase()
        val popularKeys = popularHits.map { "${it.group}:${it.artifact}" }.toSet()
        popularHits + all.filter { "${it.group}:${it.artifact}" !in popularKeys }.distinctBy { "${it.group}:${it.artifact}" }
            .sortedWith(
                compareByDescending<SearchHit> { "${it.group}:${it.artifact}".lowercase() == lower }
                    .thenByDescending { it.artifact.lowercase() == lower.substringAfterLast(':') }
                    .thenByDescending { it.artifact.lowercase().startsWith(lower.substringAfterLast(':')) }
                    .thenBy { it.artifact.length },
            )
            .take((limit - popularHits.size).coerceAtLeast(0))
    }

    /** Newest stable version from the repository's `maven-metadata.xml`, or null. */
    private suspend fun latestVersion(group: String, artifact: String, repository: MavenRepository): String? {
        val text = http.getText("${repository.baseUrl}/${group.replace('.', '/')}/$artifact/maven-metadata.xml") ?: return null
        val versions = Regex("""<version>([^<]+)</version>""").findAll(text).map { it.groupValues[1].trim() }.toList()
        return versions.filter(MavenVersion::isStable).maxWithOrNull(MavenVersion) ?: versions.maxWithOrNull(MavenVersion)
    }

    private suspend fun searchCentral(query: String, limit: Int): List<SearchHit> {
        val solrQuery = if (query.contains(':')) {
            val (g, a) = query.split(':', limit = 2)
            "g:\"$g\"" + if (a.isNotBlank()) " AND a:\"$a\"" else ""
        } else query
        val url = "https://search.maven.org/solrsearch/select?q=" + URLEncoder.encode(solrQuery, "UTF-8") + "&rows=$limit&wt=json"
        val text = http.getText(url) ?: return emptyList()
        val docs = json.parseToJsonElement(text).jsonObject["response"]?.jsonObject?.get("docs")?.jsonArray ?: return emptyList()
        return docs.mapNotNull { element ->
            val doc = element as? JsonObject ?: return@mapNotNull null
            SearchHit(
                group = doc["g"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                artifact = doc["a"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                latestVersion = doc["latestVersion"]?.jsonPrimitive?.content ?: doc["v"]?.jsonPrimitive?.content ?: "+",
                repository = MavenRepository.Central,
                packaging = doc["p"]?.jsonPrimitive?.content,
                versionCount = doc["versionCount"]?.jsonPrimitive?.int,
            )
        }
    }

    private suspend fun searchGoogle(query: String, limit: Int): List<SearchHit> {
        val groups = googleGroups ?: run {
            val text = http.getText("${MavenRepository.Google.baseUrl}/master-index.xml") ?: return emptyList()
            Regex("""<([\w.\-]+)\s*/>""").findAll(text).map { it.groupValues[1] }.toList().also { googleGroups = it }
        }
        val lower = query.lowercase()
        val groupPart = lower.substringBefore(':')
        val artifactPart = if (lower.contains(':')) lower.substringAfter(':') else lower
        val candidateGroups = groups.filter { g ->
            g.contains(groupPart) || groupPart.contains(g) || artifactPart.split('-', '.').any { token -> token.length > 2 && g.contains(token) }
        }.sortedBy { it.length }.take(12)
        val hits = mutableListOf<SearchHit>()
        for (group in candidateGroups) {
            val text = http.getText("${MavenRepository.Google.baseUrl}/${group.replace('.', '/')}/group-index.xml") ?: continue
            Regex("""<([\w.\-]+)\s+versions="([^"]+)"\s*/>""").findAll(text).forEach { m ->
                val artifact = m.groupValues[1]
                val versions = m.groupValues[2].split(',').map { it.trim() }
                if (artifact.contains(artifactPart) || group.contains(lower) || "$group:$artifact".contains(lower)) {
                    val latest = versions.filter(MavenVersion::isStable).maxWithOrNull(MavenVersion) ?: versions.maxWith(MavenVersion)
                    hits += SearchHit(group, artifact, latest, MavenRepository.Google, versionCount = versions.size)
                }
            }
            if (hits.size >= limit) break
        }
        return hits
    }

    private suspend fun searchJitPack(query: String): List<SearchHit> {
        val slug = query.removePrefix("https://").removePrefix("github.com/").trim('/')
        if (!slug.contains('/')) return emptyList()
        val (user, repo) = slug.split('/', limit = 2)
        val group = "com.github.$user"
        val text = http.getText("${MavenRepository.JitPack.baseUrl}/api/builds/$group/$repo/latestOk")
            ?: http.getText("${MavenRepository.JitPack.baseUrl}/api/builds/$group/$repo/latest")
            ?: return emptyList()
        val version = runCatching { json.parseToJsonElement(text).jsonObject["version"]?.jsonPrimitive?.content }.getOrNull() ?: return emptyList()
        return listOf(SearchHit(group, repo, version, MavenRepository.JitPack))
    }
}
