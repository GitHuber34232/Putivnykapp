package ua.kyiv.putivnyk.data.repository

import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ua.kyiv.putivnyk.data.local.dao.RouteDao
import ua.kyiv.putivnyk.data.local.entity.RouteEntity
import ua.kyiv.putivnyk.data.model.Route
import ua.kyiv.putivnyk.data.model.RoutePoint
import ua.kyiv.putivnyk.data.model.TransportMode
import ua.kyiv.putivnyk.data.model.toDomain

class RouteRepositoryImportTest {

    private class FakeRouteDao : RouteDao {
        val rows = MutableStateFlow<List<RouteEntity>>(emptyList())
        private var nextId = 1L

        override fun getAllRoutes(): Flow<List<RouteEntity>> = rows
        override fun observeAll(): Flow<List<RouteEntity>> = rows
        override fun getFavoriteRoutes(): Flow<List<RouteEntity>> = rows
        override suspend fun getRouteById(id: Long): RouteEntity? = rows.value.firstOrNull { it.id == id }
        override suspend fun insertRoute(route: RouteEntity): Long {
            val id = if (route.id == 0L) nextId++ else route.id
            rows.value = rows.value.filterNot { it.id == id } + route.copy(id = id)
            return id
        }
        override suspend fun updateRoute(route: RouteEntity) {
            rows.value = rows.value.map { if (it.id == route.id) route else it }
        }
        override suspend fun deleteRoute(route: RouteEntity) {
            rows.value = rows.value.filterNot { it.id == route.id }
        }
        override suspend fun deleteAllRoutes() {
            rows.value = emptyList()
        }
    }

    private val dao = FakeRouteDao()
    private val repository = RouteRepository(dao, Gson())

    @Test
    fun export_then_import_round_trips_routes_with_names() = runTest {
        val route = Route(
            name = "Центр",
            startPoint = RoutePoint(50.4501, 30.5234, "Майдан"),
            endPoint = RoutePoint(50.4529, 30.5145, "Софія"),
            waypoints = listOf(RoutePoint(50.4510, 30.5200, "Хрещатик")),
            distance = 900.0,
            estimatedDuration = 12,
            transportMode = TransportMode.DRIVING
        )
        repository.saveRoute(route)
        val json = repository.exportRoutesJson()
        dao.rows.value = emptyList()

        assertEquals(1, repository.importRoutesJson(json))

        val imported = dao.rows.value.single().toDomain()
        assertEquals("Центр", imported.name)
        assertEquals(TransportMode.DRIVING, imported.transportMode)
        assertEquals("Хрещатик", imported.waypoints.single().name)
        assertEquals("Майдан", imported.startPoint.name)
    }

    @Test
    fun import_accepts_files_from_versions_without_transport_mode() = runTest {
        val legacyJson = """
            [{"name":"Old walk","startPoint":{"latitude":50.45,"longitude":30.52},
              "endPoint":{"latitude":50.46,"longitude":30.53},"distance":1500.0,"estimatedDuration":20}]
        """.trimIndent()

        assertEquals(1, repository.importRoutesJson(legacyJson))

        val imported = dao.rows.value.single().toDomain()
        assertEquals(TransportMode.WALKING, imported.transportMode)
        assertEquals(0, imported.waypoints.size)
        assertNull(imported.startPoint.name)
    }

    @Test
    fun import_skips_broken_entries_and_points() = runTest {
        val json = """
            [null,
             {"name":"","startPoint":{"latitude":50.45,"longitude":30.52},"endPoint":{"latitude":50.46,"longitude":30.53}},
             {"name":"No end","startPoint":{"latitude":50.45,"longitude":30.52}},
             {"name":"Bad waypoint","startPoint":{"latitude":50.45,"longitude":30.52},
              "endPoint":{"latitude":50.46,"longitude":30.53},
              "waypoints":[{"latitude":95.0,"longitude":30.5},null,{"latitude":50.455,"longitude":30.525}]}]
        """.trimIndent()

        assertEquals(1, repository.importRoutesJson(json))
        assertEquals(1, dao.rows.value.single().toDomain().waypoints.size)
    }

    @Test
    fun import_of_garbage_returns_zero() = runTest {
        assertEquals(0, repository.importRoutesJson("not json"))
        assertEquals(0, repository.importRoutesJson("{}"))
    }
}
