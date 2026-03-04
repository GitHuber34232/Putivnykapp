package ua.kyiv.putivnyk.domain.usecase.routing

import ua.kyiv.putivnyk.data.model.Route
import ua.kyiv.putivnyk.data.model.RoutePoint
import ua.kyiv.putivnyk.domain.geo.NativeGeoEngine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RouteOptimizer @Inject constructor() {

    fun optimizeWaypoints(route: Route): Route {
        val waypoints = route.waypoints
        if (waypoints.size < 2) return RouteMetricsCalculator.recompute(route)

        val start = route.startPoint
        val end = route.endPoint

        val optimized = twoOptImprove(
            nearestNeighborOrder(start, waypoints, end),
            start,
            end
        )

        return RouteMetricsCalculator.recompute(route.copy(waypoints = optimized))
    }

    fun buildOptimalRoute(
        name: String,
        places: List<RoutePoint>,
        description: String? = null
    ): Route? {
        if (places.size < 2) return null

        val start = places.first()
        val end = places.last()
        val intermediates = if (places.size > 2) places.subList(1, places.size - 1) else emptyList()

        val optimized = if (intermediates.size >= 2) {
            twoOptImprove(nearestNeighborOrder(start, intermediates, end), start, end)
        } else {
            intermediates
        }

        val route = Route(
            name = name,
            description = description,
            startPoint = start,
            endPoint = end,
            waypoints = optimized,
            distance = 0.0,
            estimatedDuration = 0
        )

        return RouteMetricsCalculator.recompute(route)
    }

    private fun nearestNeighborOrder(
        start: RoutePoint,
        waypoints: List<RoutePoint>,
        end: RoutePoint
    ): List<RoutePoint> {
        if (waypoints.isEmpty()) return emptyList()

        val remaining = waypoints.toMutableList()
        val ordered = mutableListOf<RoutePoint>()
        var current = start

        while (remaining.isNotEmpty()) {
            val nearest = remaining.minBy { point ->
                distBetween(current, point)
            }
            ordered.add(nearest)
            remaining.remove(nearest)
            current = nearest
        }

        return ordered
    }

    private fun twoOptImprove(
        waypoints: List<RoutePoint>,
        start: RoutePoint,
        end: RoutePoint
    ): List<RoutePoint> {
        if (waypoints.size < 3) return waypoints

        val n = waypoints.size

        val all = ArrayList<RoutePoint>(n + 2)
        all.add(start)
        all.addAll(waypoints)
        all.add(end)
        val sz = all.size

        val dist = Array(sz) { DoubleArray(sz) }
        for (i in 0 until sz) {
            for (j in i + 1 until sz) {
                val d = distBetween(all[i], all[j])
                dist[i][j] = d
                dist[j][i] = d
            }
        }

        val order = IntArray(n) { it + 1 }

        var improved = true
        var iterations = 0
        val maxIterations = 100

        while (improved && iterations < maxIterations) {
            improved = false
            iterations++

            for (i in 0 until n - 1) {
                for (j in i + 1 until n) {

                    val pred = if (i == 0) 0 else order[i - 1]
                    val succ = if (j == n - 1) sz - 1 else order[j + 1]

                    val oldCost = dist[pred][order[i]] + dist[order[j]][succ]
                    val newCost = dist[pred][order[j]] + dist[order[i]][succ]

                    if (newCost < oldCost - 1e-9) {

                        var left = i; var right = j
                        while (left < right) {
                            val tmp = order[left]
                            order[left] = order[right]
                            order[right] = tmp
                            left++; right--
                        }
                        improved = true
                    }
                }
            }
        }

        return order.map { all[it] }
    }

    private fun distBetween(a: RoutePoint, b: RoutePoint): Double =
        NativeGeoEngine.distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
}
