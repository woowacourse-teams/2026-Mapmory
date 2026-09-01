import FirebaseCore
import Shared
import SwiftUI

private let lightSystemBarColor = UIColor(
    red: 250.0 / 255.0,
    green: 252.0 / 255.0,
    blue: 251.0 / 255.0,
    alpha: 1
)
private let darkSystemBarColor = UIColor(
    red: 17.0 / 255.0,
    green: 21.0 / 255.0,
    blue: 24.0 / 255.0,
    alpha: 1
)

@main
struct MapmoryApp: App {
    @State private var isDarkTheme = false
    private let analyticsLogger: FirebaseAnalyticsLogger

    init() {
        FirebaseApp.configure()
        analyticsLogger = FirebaseAnalyticsLogger()
    }

    var body: some Scene {
        WindowGroup {
            ZStack {
                Color(uiColor: isDarkTheme ? darkSystemBarColor : lightSystemBarColor)
                ComposeView(
                    isDarkTheme: $isDarkTheme,
                    analyticsLogger: analyticsLogger,
                )
            }
            .ignoresSafeArea()
            .preferredColorScheme(isDarkTheme ? .dark : .light)
        }
    }
}

private struct ComposeView: UIViewControllerRepresentable {
    @Binding var isDarkTheme: Bool
    let analyticsLogger: FirebaseAnalyticsLogger

    func makeCoordinator() -> Coordinator {
        Coordinator(isDarkTheme: $isDarkTheme)
    }

    func makeUIViewController(context: Context) -> UIViewController {
        let coordinator = context.coordinator
        let viewController = MainViewControllerKt.MainViewController(
            onThemeChanged: { isDark in
                coordinator.updateTheme(isDark.boolValue)
            },
            analytics: analyticsLogger,
        )
        applyTheme(to: viewController)
        return viewController
    }

    func updateUIViewController(
        _ uiViewController: UIViewController,
        context: Context
    ) {
        context.coordinator.isDarkTheme = $isDarkTheme
        applyTheme(to: uiViewController)
    }

    private func applyTheme(to viewController: UIViewController) {
        viewController.overrideUserInterfaceStyle = isDarkTheme ? .dark : .light
        viewController.view.backgroundColor = isDarkTheme ? darkSystemBarColor : lightSystemBarColor
        viewController.setNeedsStatusBarAppearanceUpdate()
    }

    final class Coordinator {
        var isDarkTheme: Binding<Bool>

        init(isDarkTheme: Binding<Bool>) {
            self.isDarkTheme = isDarkTheme
        }

        func updateTheme(_ isDark: Bool) {
            DispatchQueue.main.async { [weak self] in
                self?.isDarkTheme.wrappedValue = isDark
            }
        }
    }
}
