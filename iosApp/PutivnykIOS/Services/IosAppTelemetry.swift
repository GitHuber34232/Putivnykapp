import Foundation
import os.log

/// Lightweight telemetry implementation using Apple's unified logging (`os_log`).
/// In production, this can be swapped for Firebase Analytics / Sentry / etc.
final class IosAppTelemetry {
    static let shared = IosAppTelemetry()

    private let logger = Logger(subsystem: "ua.kyiv.putivnyk.ios", category: "telemetry")

    private init() {}

    func trackEvent(_ name: String, attributes: [String: String] = [:]) {
        if attributes.isEmpty {
            logger.info("event=\(name, privacy: .public)")
        } else {
            logger.info("event=\(name, privacy: .public) attrs=\(attributes.description, privacy: .public)")
        }
    }

    func trackWarning(_ name: String, error: Error? = nil, attributes: [String: String] = [:]) {
        if let error {
            logger.warning("warn=\(name, privacy: .public) error=\(error.localizedDescription, privacy: .public)")
        } else {
            logger.warning("warn=\(name, privacy: .public)")
        }
    }

    func trackError(_ name: String, error: Error? = nil, attributes: [String: String] = [:]) {
        if let error {
            logger.error("error=\(name, privacy: .public) error=\(error.localizedDescription, privacy: .public)")
        } else {
            logger.error("error=\(name, privacy: .public)")
        }
    }
}
