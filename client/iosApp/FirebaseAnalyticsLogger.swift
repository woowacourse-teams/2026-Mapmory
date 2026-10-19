import FirebaseAnalytics
import Shared

final class FirebaseAnalyticsLogger: NSObject, MapmoryAnalytics {
    private let isEnabled: Bool

    init(isEnabled: Bool = true) {
        self.isEnabled = isEnabled
    }

    func logEvent(name: String, parameters: [String: String]) {
        guard isEnabled else { return }

        var firebaseParameters = parameters.reduce(into: [String: Any]()) { result, entry in
            if ["duration_ms", "result_count", "http_status"].contains(entry.key), let number = Int64(entry.value) {
                result[entry.key] = NSNumber(value: number)
            } else {
                result[entry.key] = entry.value
            }
        }
        firebaseParameters["event_schema_version"] = "2"
        Analytics.logEvent(name, parameters: firebaseParameters.isEmpty ? nil : firebaseParameters)
    }
}
