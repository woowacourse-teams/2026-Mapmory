import FirebaseCore
import Shared
import SwiftUI
import UIKit

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

private enum AppConfiguration {
    static let apiBaseUrl: String = {
        guard let value = Bundle.main.object(forInfoDictionaryKey: "MAPMORY_API_BASE_URL") as? String,
              !value.isEmpty else {
            fatalError("MAPMORY_API_BASE_URL is missing from the app configuration")
        }
        return value
    }()
}

@main
struct MapmoryApp: App {
    @State private var isDarkTheme = false
    private let analyticsLogger: FirebaseAnalyticsLogger

    init() {
#if INTERNAL
        analyticsLogger = FirebaseAnalyticsLogger(isEnabled: false)
#else
        FirebaseApp.configure()
        analyticsLogger = FirebaseAnalyticsLogger()
#endif
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
        let navigation = MapmoryNavigation()
        let composeViewController = MainViewControllerKt.MainViewController(
            apiBaseUrl: AppConfiguration.apiBaseUrl,
            onThemeChanged: { isDark in
                coordinator.updateTheme(isDark.boolValue)
            },
            analytics: analyticsLogger,
            tokenStore: KeychainAuthTokenStore(apiBaseUrl: AppConfiguration.apiBaseUrl),
            navigation: navigation,
        )
        let viewController = SystemBackHandlingViewController(
            contentViewController: composeViewController,
            navigation: navigation,
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

/// iOS에는 Android처럼 시스템 BackHandler가 없기 때문에 가장자리 스와이프를
/// 공통 navigation 객체로 전달해 작성 중 이탈 확인을 동일하게 처리한다.
private final class SystemBackHandlingViewController: UIViewController,
    UIGestureRecognizerDelegate {
    private let contentViewController: UIViewController
    private let navigation: MapmoryNavigation

    init(
        contentViewController: UIViewController,
        navigation: MapmoryNavigation,
    ) {
        self.contentViewController = contentViewController
        self.navigation = navigation
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        addChild(contentViewController)
        view.addSubview(contentViewController.view)
        contentViewController.view.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            contentViewController.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            contentViewController.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            contentViewController.view.topAnchor.constraint(equalTo: view.topAnchor),
            contentViewController.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        contentViewController.didMove(toParent: self)

        let edgePan = UIScreenEdgePanGestureRecognizer(
            target: self,
            action: #selector(handleBackGesture(_:)),
        )
        edgePan.edges = .left
        edgePan.delegate = self
        edgePan.cancelsTouchesInView = false
        view.addGestureRecognizer(edgePan)
    }

    @objc private func handleBackGesture(_ gesture: UIScreenEdgePanGestureRecognizer) {
        guard gesture.state == .ended else { return }
        _ = navigation.popBackStack()
    }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer,
    ) -> Bool {
        true
    }
}
