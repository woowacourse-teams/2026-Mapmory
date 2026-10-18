import FirebaseCore
import FirebaseRemoteConfig
import Foundation
import Shared
import SwiftUI
import UIKit
import UserNotifications

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
    @UIApplicationDelegateAdaptor(UploadAppDelegate.self) private var appDelegate
    @State private var isDarkTheme = false
    @Environment(\.scenePhase) private var scenePhase
    private let forceUpdate: IosForceUpdateController
    private let analyticsLogger: FirebaseAnalyticsLogger

    init() {
#if INTERNAL
        analyticsLogger = FirebaseAnalyticsLogger(isEnabled: false)
#else
        FirebaseApp.configure()
        analyticsLogger = FirebaseAnalyticsLogger()
#endif
        forceUpdate = IosForceUpdateController()
    }

    var body: some Scene {
        WindowGroup {
            ZStack {
                Color(uiColor: isDarkTheme ? darkSystemBarColor : lightSystemBarColor)
                ComposeView(
                    isDarkTheme: $isDarkTheme,
                    analyticsLogger: analyticsLogger,
                    forceUpdate: forceUpdate,
                )
            }
            .onAppear { forceUpdate.refresh() }
            .onChange(of: scenePhase) { phase in
                if phase == .active { forceUpdate.refresh() }
            }
            .ignoresSafeArea()
            .preferredColorScheme(isDarkTheme ? .dark : .light)
        }
    }
}

private struct ComposeView: UIViewControllerRepresentable {
    @Binding var isDarkTheme: Bool
    let analyticsLogger: FirebaseAnalyticsLogger
    let forceUpdate: IosForceUpdateController

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
            updateState: forceUpdate.state,
            onRetryUpdateCheck: { forceUpdate.refresh() },
        )
        let viewController = composeViewController
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

final class UploadAppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification, withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .sound])
    }

    func application(_ application: UIApplication, handleEventsForBackgroundURLSession identifier: String, completionHandler: @escaping () -> Void) {
        guard identifier == "com.mapmory.record-uploads" else {
            completionHandler()
            return
        }
        MainViewControllerKt.resumeBackgroundUploads(
            apiBaseUrl: AppConfiguration.apiBaseUrl,
            tokenStore: KeychainAuthTokenStore(apiBaseUrl: AppConfiguration.apiBaseUrl)
        )
        PhotoUploadTransferKt.handleBackgroundUploadEvents(completion: completionHandler)
    }
}

private final class IosForceUpdateController {
    let state = ForceUpdateState(
        currentVersion: Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "",
        storeUrl: "https://apps.apple.com/app/id6807056166"
    )
#if DEBUG || INTERNAL
    private let key = "ios_dev_min_supported_version"
#else
    private let key = "ios_min_supported_version"
#endif
    private var fetching = false
    private var generation = 0
    private var didCheckForStoreUpdate = false

    init() {
        _ = state.applyMinimumVersion(value: UserDefaults.standard.string(forKey: key) ?? "")
    }

    func refresh() {
        checkForStoreUpdate()
        guard !fetching else { return }
        guard FirebaseApp.app() != nil else { state.finishCheck(); return }
        fetching = true
        generation += 1
        let request = generation
        let remote = RemoteConfig.remoteConfig()
        let settings = RemoteConfigSettings()
#if DEBUG
        settings.minimumFetchInterval = 0
#else
        settings.minimumFetchInterval = 3600
#endif
        settings.fetchTimeout = 10
        remote.configSettings = settings
        DispatchQueue.main.asyncAfter(deadline: .now() + 12) { [weak self] in
            guard let self, self.generation == request else { return }
            self.generation += 1
            self.fetching = false
            self.state.finishCheck()
        }
        remote.ensureInitialized { [weak self] _ in
            DispatchQueue.main.async {
                guard let self, self.generation == request else { return }
                self.accept(remote.configValue(forKey: self.key).stringValue)
                remote.fetchAndActivate { [weak self] _, error in
                    DispatchQueue.main.async {
                        guard let self, self.generation == request else { return }
                        if error == nil { self.accept(remote.configValue(forKey: self.key).stringValue) }
                        self.generation += 1
                        self.fetching = false
                        self.state.finishCheck()
                    }
                }
            }
        }
    }

    private func accept(_ value: String) {
        if state.applyMinimumVersion(value: value) {
            UserDefaults.standard.set(value, forKey: key)
        }
    }

    private func checkForStoreUpdate() {
#if DEBUG || INTERNAL
        return
#else
        guard !didCheckForStoreUpdate,
              Bundle.main.bundleIdentifier == "com.mapmory.ios",
              let installedVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String,
              var components = URLComponents(string: "https://itunes.apple.com/lookup") else {
            return
        }
        didCheckForStoreUpdate = true
        components.queryItems = [
            URLQueryItem(name: "id", value: "6807056166"),
            URLQueryItem(name: "country", value: "kr"),
        ]
        guard let lookupURL = components.url else { return }

        URLSession.shared.dataTask(with: lookupURL) { data, response, error in
            guard error == nil,
                  let response = response as? HTTPURLResponse,
                  (200..<300).contains(response.statusCode),
                  let data,
                  let results = try? JSONDecoder().decode(AppStoreLookupResponse.self, from: data).results,
                  let app = results.first,
                  app.bundleId == "com.mapmory.ios" else {
                return
            }
            let updateAvailable = app.version.compare(installedVersion, options: .numeric) == .orderedDescending
            DispatchQueue.main.async {
                self.state.reportStoreUpdateAvailability(isAvailable: updateAvailable)
            }
        }.resume()
#endif
    }
}

private struct AppStoreLookupResponse: Decodable {
    let results: [AppStoreApplication]
}

private struct AppStoreApplication: Decodable {
    let bundleId: String
    let version: String
}
