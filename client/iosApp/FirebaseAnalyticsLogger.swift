import FirebaseAnalytics
import Shared

final class FirebaseAnalyticsLogger: NSObject, MapmoryAnalytics {
    func logEvent(name: String, parameters: [String: String]) {
        let firebaseParameters = parameters.reduce(into: [String: Any]()) { result, entry in
            result[entry.key] = entry.value
        }
        Analytics.logEvent(name, parameters: firebaseParameters.isEmpty ? nil : firebaseParameters)
    }
}
