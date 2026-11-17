import Foundation

#if canImport(Shared)
import FirebaseAnalytics
import FirebaseCrashlytics
import Shared

final class FirebaseAnalyticsLogger: NSObject, MapmoryAnalytics {
    private let isEnabled: Bool

    init(isEnabled: Bool = true) {
        self.isEnabled = isEnabled
    }

    func logEvent(name: String, parameters: [String: String]) {
        guard isEnabled else { return }

        if let crashlytics = collectingCrashlytics,
           let context = CrashlyticsEventContext.make(name: name, parameters: parameters) {
            crashlytics.setCustomKeysAndValues(context.keys)
            crashlytics.log(context.log)
        }

        var firebaseParameters = parameters.reduce(into: [String: Any]()) { result, entry in
            if ["duration_ms", "elapsed_ms", "result_count", "http_status"].contains(entry.key), let number = Int64(entry.value) {
                result[entry.key] = NSNumber(value: number)
            } else {
                result[entry.key] = entry.value
            }
        }
        firebaseParameters["event_schema_version"] = "2"
        Analytics.logEvent(name, parameters: firebaseParameters.isEmpty ? nil : firebaseParameters)
    }

    func setUserProperty(name: String, value: String?) {
        guard isEnabled else { return }

        Analytics.setUserProperty(value, forName: name)
        if name == "is_team" {
            collectingCrashlytics?.setCustomValue(CrashlyticsEventContext.teamValue(value), forKey: name)
        }
    }

    private var collectingCrashlytics: Crashlytics? {
        let crashlytics = Crashlytics.crashlytics()
        return crashlytics.isCrashlyticsCollectionEnabled() ? crashlytics : nil
    }
}
#endif

/// Kept independent of Firebase so the exported context can be tested without sending telemetry.
struct CrashlyticsEventContext {
    let keys: [String: String]
    let log: String

    private static let dimensions = [
        "action", "error_category", "error_type", "feature", "media_type", "mode",
        "operation", "origin", "outcome", "owner_hint", "share_type", "source",
        "stage", "status", "step_name",
    ]
    private static let measurements = [
        "count", "duration_ms", "elapsed_ms", "http_status", "photo_count",
        "platform_code", "platform_extra_code", "result_count", "selected_count", "selection_index",
    ]

    static func make(name: String, parameters: [String: String]) -> CrashlyticsEventContext? {
        guard let event = identifier(name, limit: 64) else { return nil }
        var details: [String: String] = [:]
        for key in dimensions {
            if let value = identifier(parameters[key]) { details[key] = value }
        }
        for key in measurements {
            if let raw = parameters[key], let value = Int64(raw),
               value >= 0 || key == "platform_code" || key == "platform_extra_code" {
                details[key] = String(value)
            }
        }

        var keys = [
            "event_schema_version": "2", "crash_context_schema_version": "1", "last_event": event,
        ]
        // These describe one event. Clear absent values instead of retaining another event's error/state.
        for key in dimensions + measurements {
            keys["last_" + key] = details[key] ?? "unset"
        }
        if event == "app_screen_view" {
            let screen = identifier(parameters["screen_name"]) ?? "unknown"
            keys["screen_name"] = screen
            details["screen_name"] = screen
        }

        let fields = details.sorted { $0.key < $1.key }.map { "\($0.key)=\($0.value)" }
        let message = ([event] + fields).joined(separator: " ")
        return CrashlyticsEventContext(keys: keys, log: String(message.prefix(1024)))
    }

    static func teamValue(_ value: String?) -> String {
        switch value {
        case "true": return "true"
        case "false": return "false"
        default: return "unknown"
        }
    }

    private static func identifier(_ value: String?, limit: Int = 80) -> String? {
        guard let value = value, !value.isEmpty, value.utf8.count <= limit,
              value.range(of: "^[A-Za-z_][A-Za-z0-9_.-]*\\z", options: .regularExpression) != nil else {
            return nil
        }
        return value
    }
}
