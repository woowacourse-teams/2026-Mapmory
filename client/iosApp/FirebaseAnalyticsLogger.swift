import FirebaseAnalytics
import Shared

final class FirebaseAnalyticsLogger: NSObject, MapmoryAnalytics {
    private let isEnabled: Bool

    init(isEnabled: Bool = true) {
        self.isEnabled = isEnabled
    }

    func logEvent(name: String, parameters: [String: String]) {
        guard isEnabled else { return }

        let firebaseParameters = parameters.reduce(into: [String: Any]()) { result, entry in
            result[entry.key] = entry.value
        }
        Analytics.logEvent(name, parameters: firebaseParameters.isEmpty ? nil : firebaseParameters)
    }
}
