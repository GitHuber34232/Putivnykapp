import CoreLocation
import Combine

/// Manages GPS location tracking for the app.
final class LocationService: NSObject, ObservableObject, CLLocationManagerDelegate {
    static let shared = LocationService()

    @Published var userLocation: CLLocationCoordinate2D?
    @Published var userBearing: Double = -1
    @Published var userSpeedMetersPerSecond: Double = 0
    @Published var userAccuracy: Double = .greatestFiniteMagnitude
    @Published var hasLocation: Bool = false
    @Published var authorizationStatus: CLAuthorizationStatus = .notDetermined

    private let manager = CLLocationManager()

    private override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.distanceFilter = 5
    }

    func requestPermission() {
        manager.requestWhenInUseAuthorization()
    }

    func startTracking() {
        manager.startUpdatingLocation()
        manager.startUpdatingHeading()
    }

    func stopTracking() {
        manager.stopUpdatingLocation()
        manager.stopUpdatingHeading()
    }

    // MARK: - CLLocationManagerDelegate

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        userLocation = location.coordinate
        userSpeedMetersPerSecond = max(0, location.speed)
        userAccuracy = location.horizontalAccuracy
        hasLocation = true
    }

    func locationManager(_ manager: CLLocationManager, didUpdateHeading newHeading: CLHeading) {
        if newHeading.trueHeading >= 0 {
            userBearing = newHeading.trueHeading
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        authorizationStatus = manager.authorizationStatus
        if manager.authorizationStatus == .authorizedWhenInUse ||
           manager.authorizationStatus == .authorizedAlways {
            startTracking()
        }
    }
}
