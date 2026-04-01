import SwiftUI
import PutivnykShared

@main
struct PutivnykIOSApp: App {
    @StateObject private var localization = UiLocalizationViewModel()
    @StateObject private var appExperience = AppExperienceViewModel()

    init() {
        IosSyncService.shared.registerBackgroundTasks()
    }

    var body: some Scene {
        WindowGroup {
            Group {
                if appExperience.showOnboarding {
                    OnboardingView {
                        appExperience.completeOnboarding()
                    }
                    .environmentObject(localization)
                } else {
                    MainTabView()
                        .environmentObject(localization)
                }
            }
            .task {
                await IosSyncService.shared.syncAll()
                IosSyncService.shared.scheduleEventsRefresh()
                IosSyncService.shared.scheduleOfflineCacheRefresh()
            }
        }
    }
}

// MARK: - Onboarding

struct OnboardingView: View {
    @EnvironmentObject var localization: UiLocalizationViewModel
    var onComplete: () -> Void

    @State private var currentPage = 0
    private var texts: [String: String] { localization.uiTexts }

    private var pages: [(icon: String, title: String, description: String)] {
        [
            ("map.fill",
             tr("onboarding.welcome_title", fallback: "Ласкаво просимо до Путівника!", texts: texts),
             tr("onboarding.welcome_desc", fallback: "Ваш персональний гід по Києву", texts: texts)),
            ("star.fill",
             tr("onboarding.places_title", fallback: "Відкрийте цікаві місця", texts: texts),
             tr("onboarding.places_desc", fallback: "Музеї, театри, парки та ресторани — все в одному додатку", texts: texts)),
            ("point.topleft.down.to.point.bottomright.curvepath",
             tr("onboarding.routes_title", fallback: "Плануйте маршрути", texts: texts),
             tr("onboarding.routes_desc", fallback: "Створюйте оптимальні пішохідні маршрути з навігацією", texts: texts)),
            ("globe",
             tr("onboarding.lang_title", fallback: "Мова інтерфейсу", texts: texts),
             tr("onboarding.lang_desc", fallback: "Підтримка 35 мов з автоматичним визначенням", texts: texts))
        ]
    }

    var body: some View {
        VStack(spacing: 40) {
            Spacer()

            TabView(selection: $currentPage) {
                ForEach(Array(pages.enumerated()), id: \.offset) { index, page in
                    VStack(spacing: 20) {
                        Image(systemName: page.icon)
                            .font(.system(size: 80))
                            .foregroundStyle(.blue)
                        Text(page.title)
                            .font(.title)
                            .fontWeight(.bold)
                            .multilineTextAlignment(.center)
                        Text(page.description)
                            .font(.body)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 40)
                    }
                    .tag(index)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .always))

            Button {
                if currentPage < pages.count - 1 {
                    withAnimation { currentPage += 1 }
                } else {
                    onComplete()
                }
            } label: {
                Text(currentPage < pages.count - 1
                     ? tr("onboarding.next", fallback: "Далі", texts: texts)
                     : tr("onboarding.start", fallback: "Розпочати", texts: texts))
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(.blue)
                    .foregroundStyle(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 16))
            }
            .padding(.horizontal, 40)

            if currentPage < pages.count - 1 {
                Button(tr("onboarding.skip", fallback: "Пропустити", texts: texts)) {
                    onComplete()
                }
                .foregroundStyle(.secondary)
            }

            Spacer()
                .frame(height: 40)
        }
    }
}