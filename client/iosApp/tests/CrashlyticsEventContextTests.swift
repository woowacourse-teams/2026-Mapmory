import Foundation

@main
struct CrashlyticsEventContextTests {
    static func main() {
        var checks = 0
        func check(_ condition: @autoclosure () -> Bool, _ message: String) {
            precondition(condition(), message)
            checks += 1
        }
        func context(_ name: String, _ parameters: [String: String] = [:]) -> CrashlyticsEventContext {
            guard let result = CrashlyticsEventContext.make(name: name, parameters: parameters) else {
                fatalError("Valid event rejected: \(name)")
            }
            return result
        }

        let screen = context("app_screen_view", ["screen_name": "record_detail", "record_id": "1234"])
        check(screen.keys["screen_name"] == "record_detail", "Current screen is attached")
        check(!screen.log.contains("1234"), "Record identifiers are excluded from custom logs")
        check(context("app_screen_view").keys["screen_name"] == "unknown", "Missing screen clears stale screen")
        check(context("record_viewed").keys["screen_name"] == nil, "Other events preserve current screen")

        let failure = context("app_error", [
            "operation": "photo_display", "error_category": "client_logic", "error_type": "IllegalStateException",
            "http_status": "503", "duration_ms": "00012", "platform_code": "-11800",
        ])
        check(failure.keys["last_operation"] == "photo_display", "Operation is available as a key")
        check(failure.keys["last_error_type"] == "IllegalStateException", "Exception type is available")
        check(failure.log.contains("http_status=503"), "HTTP status is logged")
        check(failure.log.contains("duration_ms=12"), "Measurements are normalized")
        check(failure.log.contains("platform_code=-11800"), "Native negative platform codes are retained")
        check(failure.keys["event_schema_version"] == "2", "Analytics schema is preserved")

        let success = context("app_operation_completed", ["operation": "record_create", "outcome": "success"])
        var state = failure.keys
        state.merge(success.keys) { _, new in new }
        check(state["last_error_type"] == "unset", "Successful events clear the previous exception type")
        check(state["last_http_status"] == "unset", "Successful events clear the previous HTTP status")
        check(state["last_operation"] == "record_create", "The keys describe the latest event")
        check(context("photos_added", ["count": "8"]).log.contains("count=8"), "Existing photo event count is retained")

        let privateValues = [
            "record_id": "private_record", "user_id": "private_user", "token": "private_token",
            "title": "private_title", "message": "private_message", "url": "https://private.example/file",
            "latitude": "37.5", "longitude": "127.1", "path": "/private/photo.jpg",
            "error_message": "private_exception", "event_schema_version": "private_schema",
        ]
        let redacted = context("app_error", privateValues)
        for value in privateValues.values {
            check(!redacted.log.contains(value) && !redacted.keys.values.contains(value), "Unlisted values are excluded")
        }
        for value in ["https://private.example/file", "/private/photo.jpg", "user@example.com", "private message", "bad\nvalue", "bad\n", String(repeating: "a", count: 81)] {
            let result = context("app_error", ["operation": value, "http_status": value])
            check(result.keys["last_operation"] == "unset", "Invalid dimension is excluded")
            check(result.keys["last_http_status"] == "unset", "Invalid measurement is excluded")
        }
        for value in ["-1", "1.5", "9223372036854775808"] {
            check(context("photos_added", ["count": value]).keys["last_count"] == "unset", "Invalid counts are excluded")
        }
        for name in ["", "private event", "user@example.com", String(repeating: "a", count: 65)] {
            check(CrashlyticsEventContext.make(name: name, parameters: [:]) == nil, "Invalid event names are rejected")
        }

        check(CrashlyticsEventContext.teamValue("true") == "true", "Team devices are explicit")
        check(CrashlyticsEventContext.teamValue("false") == "false", "Non-team devices are explicit")
        check(CrashlyticsEventContext.teamValue(nil) == "unknown", "Removed team property clears the previous value")
        check(CrashlyticsEventContext.teamValue("invalid") == "unknown", "Unknown is never silently false")

        let full = context("app_error", Dictionary(uniqueKeysWithValues: [
            "action", "error_category", "error_type", "feature", "media_type", "mode", "operation",
            "origin", "outcome", "owner_hint", "share_type", "source", "stage", "status", "step_name",
        ].map { ($0, String(repeating: "a", count: 80)) }))
        check(full.log.utf8.count <= 1024, "Each custom log message is bounded")
        check(full.keys.count + 2 < 64, "Current screen and team still fit the Crashlytics key budget")
        check(context("app_error", ["outcome": "failure", "operation": "photo_display"]).log ==
              context("app_error", ["operation": "photo_display", "outcome": "failure"]).log,
              "Log output is deterministic")
        print("Crashlytics event context: \(checks) checks passed")
    }
}
