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

    func load() -> AuthTokens? { try? loadChecked() }

    func read_() throws -> AuthTokenRead { AuthTokenRead(tokens: try loadChecked()) }

    private func loadChecked() throws -> AuthTokens? {
        // An old app version wrote newer rotated tokens here when a Keychain update failed.
        if let payload = UserDefaults.standard.string(forKey: legacyKey) {
            guard let tokens = decode(payload) else {
                throw NSError(domain: "Mapmory.AuthStorage", code: 3,
                              userInfo: [NSLocalizedDescriptionKey: "저장된 로그인 정보를 읽지 못했습니다."])
            }
            if saveToKeychain(tokens) { UserDefaults.standard.removeObject(forKey: legacyKey) }
            return tokens
        }
        return try loadFromKeychain()
    }

    func save(tokens: AuthTokens) throws {
        if saveToKeychain(tokens) {
            UserDefaults.standard.removeObject(forKey: legacyKey)
        } else {
            throw NSError(domain: "Mapmory.AuthStorage", code: 2,
                          userInfo: [NSLocalizedDescriptionKey: "로그인 정보를 안전하게 저장하지 못했습니다. 다시 시도해 주세요."])
        }
    }

    func pendingProvider() throws -> AuthPendingRead {
        AuthPendingRead(provider: UserDefaults.standard.string(forKey: legacyKey + "_pending_provider"))
    }

    func markPendingProvider(provider: String?) throws {
        UserDefaults.standard.set(provider, forKey: legacyKey + "_pending_provider")
    }

    func recentLoginProvider() throws -> AuthPendingRead {
        AuthPendingRead(provider: UserDefaults.standard.string(forKey: legacyKey + "_recent_login_provider"))
    }

    func saveRecentLoginProvider(provider: String) throws {
        UserDefaults.standard.set(provider, forKey: legacyKey + "_recent_login_provider")
    }

    func clear() throws {
        let status = SecItemDelete(baseQuery() as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw NSError(domain: "Mapmory.AuthStorage", code: Int(status),
                          userInfo: [NSLocalizedDescriptionKey: "로그인 정보를 삭제하지 못했습니다. 다시 시도해 주세요."])
        }
        UserDefaults.standard.removeObject(forKey: legacyKey)
    }

    private func loadFromKeychain() throws -> AuthTokens? {
        var query = baseQuery()
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess,
              let data = result as? Data,
              let payload = String(data: data, encoding: .utf8),
              let tokens = decode(payload) else {
            // Locked/unavailable Keychain and corrupt payloads are not an absent identity.
            throw NSError(domain: "Mapmory.AuthStorage", code: Int(status == errSecSuccess ? errSecDecode : status),
                          userInfo: [NSLocalizedDescriptionKey: "로그인 정보를 읽지 못했습니다. 잠금을 해제하고 다시 시도해 주세요."])
        }
        return tokens
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
