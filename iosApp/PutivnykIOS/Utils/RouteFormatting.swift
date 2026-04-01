import Foundation

func formatRouteDistance(_ distanceMeters: Double) -> String {
    switch distanceMeters {
    case 1000...:
        return String(format: "%.1f км", distanceMeters / 1000.0)
    case 100...:
        return "\(Int(distanceMeters / 10.0) * 10) м"
    default:
        return "\(Int(distanceMeters)) м"
    }
}

func formatRouteDuration(_ totalMinutes: Int) -> String {
    guard totalMinutes > 0 else { return "0 хв" }
    let hours = totalMinutes / 60
    let minutes = totalMinutes % 60
    switch (hours, minutes) {
    case let (h, m) where h > 0 && m > 0:
        return "\(h) год \(m) хв"
    case let (h, _) where h > 0:
        return "\(h) год"
    default:
        return "\(minutes) хв"
    }
}