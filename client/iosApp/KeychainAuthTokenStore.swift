import Foundation
import Security
import Shared

final class KeychainAuthTokenStore: NSObject, AuthTokenStore {
    private let service: String
    private let account = "guest_tokens"
    private let legacyKey: String

    init(apiBaseUrl: String) {
        let key = AuthEnvironmentKt.authTokenStorageKey(apiBaseUrl: apiBaseUrl)
        service = key == "production" ? "com.mapmory.ios.auth" : "com.mapmory.ios.auth.\(key)"
        legacyKey = key == "production" ? "mapmory_auth_tokens" : "mapmory_auth_tokens_\(key)"
        super.init()
    }

    func load() -> AuthTokens? {
        if let tokens = loadFromKeychain() {
            return tokens
        }

        guard let payload = UserDefaults.standard.string(forKey: legacyKey),
              let tokens = decode(payload) else {
            return nil
        }
        if saveToKeychain(tokens) {
            UserDefaults.standard.removeObject(forKey: legacyKey)
        }
        return tokens
    }

    func save(tokens: AuthTokens) {
        if saveToKeychain(tokens) {
            UserDefaults.standard.removeObject(forKey: legacyKey)
        } else {
            UserDefaults.standard.set(encode(tokens), forKey: legacyKey)
        }
    }

    func clear() {
        SecItemDelete(baseQuery() as CFDictionary)
        UserDefaults.standard.removeObject(forKey: legacyKey)
    }

    private func loadFromKeychain() -> AuthTokens? {
        var query = baseQuery()
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data,
              let payload = String(data: data, encoding: .utf8) else {
            return nil
        }
        return decode(payload)
    }

    private func saveToKeychain(_ tokens: AuthTokens) -> Bool {
        let data = Data(encode(tokens).utf8)
        let update = [kSecValueData as String: data]
        let status = SecItemUpdate(baseQuery() as CFDictionary, update as CFDictionary)
        if status == errSecSuccess {
            return true
        }
        guard status == errSecItemNotFound else {
            return false
        }

        var item = baseQuery()
        item[kSecValueData as String] = data
        item[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        return SecItemAdd(item as CFDictionary, nil) == errSecSuccess
    }

    private func baseQuery() -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
    }

    private func encode(_ tokens: AuthTokens) -> String {
        "\(tokens.accessToken)\n\(tokens.refreshToken)"
    }

    private func decode(_ payload: String) -> AuthTokens? {
        let parts = payload.split(separator: "\n", maxSplits: 1, omittingEmptySubsequences: false)
        guard parts.count == 2, !parts[0].isEmpty, !parts[1].isEmpty else {
            return nil
        }
        return AuthTokens(accessToken: String(parts[0]), refreshToken: String(parts[1]))
    }
}
