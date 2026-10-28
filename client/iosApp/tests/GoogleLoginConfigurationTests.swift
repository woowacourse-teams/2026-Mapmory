import Foundation

@main
struct GoogleLoginConfigurationTests {
    static func main() throws {
        let ios = "123-ios.apps.googleusercontent.com"
        let web = "123-web.apps.googleusercontent.com"
        let scheme = "com.googleusercontent.apps.123-ios"
        let valid: [String: Any] = [
            "MAPMORY_GOOGLE_IOS_CLIENT_ID": ios,
            "MAPMORY_GOOGLE_WEB_CLIENT_ID": web,
            "CFBundleURLTypes": [["CFBundleURLSchemes": ["kakao-test", scheme]]],
        ]
        var count = 0
        func check(_ name: String, _ changes: [String: Any], failure: GoogleLoginConfiguration.Failure? = nil) throws {
            let info = valid.merging(changes) { _, new in new }
            do {
                let config = try GoogleLoginConfiguration(info: info)
                guard failure == nil, config.clientID == info["MAPMORY_GOOGLE_IOS_CLIENT_ID"] as? String,
                      config.serverClientID == web else { fatalError("Failed: \(name)") }
            } catch let error as GoogleLoginConfiguration.Failure {
                guard error == failure else { fatalError("Failed: \(name): \(error)") }
            }
            count += 1
        }

        try check("valid iOS client and backend audience", [:])
        for missing in ["", "$(MAPMORY_GOOGLE_IOS_CLIENT_ID)", ".apps.googleusercontent.com", "123-ios", " \(ios)"] {
            try check("missing or invalid iOS client", ["MAPMORY_GOOGLE_IOS_CLIENT_ID": missing], failure: .invalidIOSClient)
        }
        try check("absent iOS client", ["MAPMORY_GOOGLE_IOS_CLIENT_ID": NSNull()], failure: .invalidIOSClient)
        try check("absent web client", ["MAPMORY_GOOGLE_WEB_CLIENT_ID": NSNull()], failure: .invalidServerClient)
        try check("unexpanded web client", ["MAPMORY_GOOGLE_WEB_CLIENT_ID": "$(WEB_ID)"], failure: .invalidServerClient)
        try check("web client must not replace iOS client", ["MAPMORY_GOOGLE_IOS_CLIENT_ID": web], failure: .sameClient)
        try check("absent URL types", ["CFBundleURLTypes": NSNull()], failure: .missingCallbackScheme)
        try check("wrong callback scheme", ["CFBundleURLTypes": [["CFBundleURLSchemes": ["com.googleusercontent.apps.123-web"]]]], failure: .missingCallbackScheme)
        try check("unexpanded callback scheme", ["CFBundleURLTypes": [["CFBundleURLSchemes": ["$(REVERSED_CLIENT_ID)"]]]], failure: .missingCallbackScheme)
        try check("URL scheme case is ignored", ["CFBundleURLTypes": [["CFBundleURLSchemes": [scheme.uppercased()]]]])
        try check("Internal cannot reuse main callback", ["MAPMORY_GOOGLE_IOS_CLIENT_ID": "123-internal.apps.googleusercontent.com"], failure: .missingCallbackScheme)
        try check("Internal has independent client and callback", [
            "MAPMORY_GOOGLE_IOS_CLIENT_ID": "123-internal.apps.googleusercontent.com",
            "CFBundleURLTypes": [["CFBundleURLSchemes": ["com.googleusercontent.apps.123-internal"]]],
        ])
        print("Google login configuration: \(count) checks passed")
    }
}
