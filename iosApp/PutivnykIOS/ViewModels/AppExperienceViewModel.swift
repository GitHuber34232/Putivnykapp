import SwiftUI
import PutivnykShared

@MainActor
final class AppExperienceViewModel: ObservableObject {
    @Published var showOnboarding = false

    private let services = AppServices.shared
    private static let keyOnboardingCompleted = "onboarding.completed"

    init() {
        Task {
            let completed = (try? await services.userPreferenceRepository.getString(
                key: Self.keyOnboardingCompleted, defaultValue: "false")) == "true"
            showOnboarding = !completed
        }
    }

    func completeOnboarding() {
        Task {
            try? await services.userPreferenceRepository.upsert(key: Self.keyOnboardingCompleted, value_: "true")
            showOnboarding = false
        }
    }

    func resetOnboarding() {
        Task {
            try? await services.userPreferenceRepository.upsert(key: Self.keyOnboardingCompleted, value_: "false")
            showOnboarding = true
        }
    }
}
