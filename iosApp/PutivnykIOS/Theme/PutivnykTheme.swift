import SwiftUI

// MARK: - Putivnyk Color Palette (mirrors Android Color.kt)

extension Color {
    // Primary — Ukrainian blue
    static let putivnykPrimary = Color(red: 0.0, green: 0.47, blue: 0.84)      // #0078D7
    static let putivnykOnPrimary = Color.white
    static let putivnykPrimaryContainer = Color(red: 0.82, green: 0.91, blue: 1.0)
    static let putivnykOnPrimaryContainer = Color(red: 0.0, green: 0.18, blue: 0.38)

    // Secondary — gold/yellow
    static let putivnykSecondary = Color(red: 0.96, green: 0.76, blue: 0.07)    // #F5C211
    static let putivnykOnSecondary = Color(red: 0.2, green: 0.16, blue: 0.0)
    static let putivnykSecondaryContainer = Color(red: 1.0, green: 0.94, blue: 0.76)
    static let putivnykOnSecondaryContainer = Color(red: 0.24, green: 0.19, blue: 0.0)

    // Tertiary — green
    static let putivnykTertiary = Color(red: 0.0, green: 0.58, blue: 0.35)
    static let putivnykOnTertiary = Color.white
    static let putivnykTertiaryContainer = Color(red: 0.7, green: 0.96, blue: 0.82)

    // Surface
    static let putivnykSurface = Color(red: 0.98, green: 0.98, blue: 0.98)
    static let putivnykSurfaceDark = Color(red: 0.07, green: 0.07, blue: 0.07)
    static let putivnykOnSurface = Color(red: 0.1, green: 0.1, blue: 0.1)
    static let putivnykOnSurfaceVariant = Color(red: 0.44, green: 0.44, blue: 0.47)

    // Error
    static let putivnykError = Color(red: 0.73, green: 0.15, blue: 0.15)

    // Background
    static let putivnykBackground = Color(red: 0.98, green: 0.98, blue: 1.0)
    static let putivnykBackgroundDark = Color(red: 0.06, green: 0.06, blue: 0.09)
}

// MARK: - Liquid Glass Helpers

struct LiquidGlassModifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .background(.ultraThinMaterial)
            .clipShape(RoundedRectangle(cornerRadius: 20))
    }
}

struct LiquidGlassCardModifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .background(.regularMaterial)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .shadow(color: .black.opacity(0.08), radius: 4, y: 2)
    }
}

extension View {
    func liquidGlass() -> some View {
        modifier(LiquidGlassModifier())
    }

    func liquidGlassCard() -> some View {
        modifier(LiquidGlassCardModifier())
    }
}
