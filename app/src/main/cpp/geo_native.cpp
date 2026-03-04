#include <jni.h>
#include <cmath>
#include <vector>

namespace {
constexpr double kEarthRadiusMeters = 6371000.0;

struct LatLon {
    double lat;
    double lon;
};

double toRadians(double degree) {
    return degree * M_PI / 180.0;
}

double haversineMeters(const LatLon& a, const LatLon& b) {
    const double dLat = toRadians(b.lat - a.lat);
    const double dLon = toRadians(b.lon - a.lon);
    const double aVal = std::sin(dLat / 2.0) * std::sin(dLat / 2.0) +
                        std::cos(toRadians(a.lat)) * std::cos(toRadians(b.lat)) *
                        std::sin(dLon / 2.0) * std::sin(dLon / 2.0);
    const double c = 2.0 * std::atan2(std::sqrt(aVal), std::sqrt(1.0 - aVal));
    return kEarthRadiusMeters * c;
}

std::vector<LatLon> decodePoints(JNIEnv* env, jdoubleArray pointsArray) {
    std::vector<LatLon> points;
    if (pointsArray == nullptr) {
        return points;
    }

    const jsize len = env->GetArrayLength(pointsArray);
    if (len < 2 || len % 2 != 0) {
        return points;
    }

    jdouble* raw = env->GetDoubleArrayElements(pointsArray, nullptr);
    points.reserve(static_cast<size_t>(len / 2));
    for (jsize i = 0; i < len; i += 2) {
        points.push_back({raw[i], raw[i + 1]});
    }
    env->ReleaseDoubleArrayElements(pointsArray, raw, JNI_ABORT);
    return points;
}

struct XY {
    double x;
    double y;
};

XY toMeters(const LatLon& point, double referenceLatRad) {
    return {
        kEarthRadiusMeters * toRadians(point.lon) * std::cos(referenceLatRad),
        kEarthRadiusMeters * toRadians(point.lat)
    };
}

double perpendicularDistance(const XY& p, const XY& a, const XY& b) {
    const double dx = b.x - a.x;
    const double dy = b.y - a.y;
    if (dx == 0.0 && dy == 0.0) {
        const double px = p.x - a.x;
        const double py = p.y - a.y;
        return std::sqrt(px * px + py * py);
    }
    const double t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / (dx * dx + dy * dy);
    const double projX = a.x + t * dx;
    const double projY = a.y + t * dy;
    const double distX = p.x - projX;
    const double distY = p.y - projY;
    return std::sqrt(distX * distX + distY * distY);
}

void rdpSimplify(const std::vector<XY>& points,
                 int start,
                 int end,
                 double toleranceMeters,
                 std::vector<bool>& keep) {
    if (end <= start + 1) {
        return;
    }

    double maxDistance = 0.0;
    int index = -1;

    for (int i = start + 1; i < end; ++i) {
        const double distance = perpendicularDistance(points[i], points[start], points[end]);
        if (distance > maxDistance) {
            maxDistance = distance;
            index = i;
        }
    }

    if (index >= 0 && maxDistance > toleranceMeters) {
        keep[index] = true;
        rdpSimplify(points, start, index, toleranceMeters, keep);
        rdpSimplify(points, index, end, toleranceMeters, keep);
    }
}
}

extern "C" JNIEXPORT jdouble JNICALL
Java_ua_kyiv_putivnyk_domain_geo_NativeGeoEngine_nativeDistanceMeters(
        JNIEnv*, jobject,
        jdouble lat1,
        jdouble lon1,
        jdouble lat2,
        jdouble lon2) {
    return haversineMeters({lat1, lon1}, {lat2, lon2});
}

extern "C" JNIEXPORT jdouble JNICALL
Java_ua_kyiv_putivnyk_domain_geo_NativeGeoEngine_nativePolylineDistanceMeters(
        JNIEnv* env,
        jobject,
        jdoubleArray pointsArray) {
    const auto points = decodePoints(env, pointsArray);
    if (points.size() < 2) {
        return 0.0;
    }

    double total = 0.0;
    for (size_t i = 1; i < points.size(); ++i) {
        total += haversineMeters(points[i - 1], points[i]);
    }
    return total;
}

extern "C" JNIEXPORT jint JNICALL
Java_ua_kyiv_putivnyk_domain_geo_NativeGeoEngine_nativeNearestPointIndex(
        JNIEnv* env,
        jobject,
        jdoubleArray pointsArray,
        jdouble lat,
        jdouble lon) {
    const auto points = decodePoints(env, pointsArray);
    if (points.empty()) {
        return -1;
    }

    const LatLon target{lat, lon};
    int bestIndex = 0;
    double bestDistance = haversineMeters(points[0], target);

    for (size_t i = 1; i < points.size(); ++i) {
        const double distance = haversineMeters(points[i], target);
        if (distance < bestDistance) {
            bestDistance = distance;
            bestIndex = static_cast<int>(i);
        }
    }

    return bestIndex;
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_ua_kyiv_putivnyk_domain_geo_NativeGeoEngine_nativeSimplifyPolyline(
        JNIEnv* env,
        jobject,
        jdoubleArray pointsArray,
        jdouble toleranceMeters) {
    const auto points = decodePoints(env, pointsArray);
    if (points.size() < 3 || toleranceMeters <= 0.0) {
        return pointsArray;
    }

    double refLat = 0.0;
    for (const auto& p : points) {
        refLat += p.lat;
    }
    refLat /= static_cast<double>(points.size());
    const double refLatRad = toRadians(refLat);

    std::vector<XY> metric(points.size());
    for (size_t i = 0; i < points.size(); ++i) {
        metric[i] = toMeters(points[i], refLatRad);
    }

    std::vector<bool> keep(points.size(), false);
    keep.front() = true;
    keep.back() = true;

    rdpSimplify(metric, 0, static_cast<int>(metric.size() - 1), toleranceMeters, keep);

    std::vector<jdouble> simplified;
    simplified.reserve(points.size() * 2);
    for (size_t i = 0; i < points.size(); ++i) {
        if (keep[i]) {
            simplified.push_back(points[i].lat);
            simplified.push_back(points[i].lon);
        }
    }

    jdoubleArray out = env->NewDoubleArray(static_cast<jsize>(simplified.size()));
    if (out == nullptr) {
        return nullptr;
    }
    env->SetDoubleArrayRegion(out, 0, static_cast<jsize>(simplified.size()), simplified.data());
    return out;
}
