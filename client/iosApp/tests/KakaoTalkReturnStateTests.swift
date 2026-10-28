import Foundation

@main
struct KakaoTalkReturnStateTests {
    static func main() {
        var state = KakaoTalkReturnState()
        let first = state.begin(usesTalk: true)
        precondition(state.returnedWithoutRedirect == nil, "Initial activation is not a cancellation")
        state.didLeaveApp()
        precondition(state.shouldCancelReturn(first), "Direct app return must release login loading")
        state.finish()
        precondition(!state.isCurrent(first), "Late SDK result after cancellation must be ignored")
        precondition(!state.shouldCancelReturn(first), "Duplicate activation must not complete twice")

        let second = state.begin(usesTalk: true)
        state.didLeaveApp()
        precondition(!state.isCurrent(first), "Previous SDK callback cannot complete a new login")
        precondition(!state.shouldCancelReturn(first), "Previous delayed check cannot cancel a new login")
        precondition(state.shouldCancelReturn(second), "A new attempt tracks its own app return")
        state.didReceiveRedirect()
        precondition(!state.shouldCancelReturn(second), "Redirect after activation protects slow token exchange")
        precondition(state.isCurrent(second), "Await the SDK result after a valid redirect")
        state.finish()

        let third = state.begin(usesTalk: true)
        state.didReceiveRedirect()
        state.didLeaveApp()
        precondition(!state.shouldCancelReturn(third), "Redirect before lifecycle events is also safe")
        state.finish()

        let web = state.begin(usesTalk: false)
        state.didLeaveApp()
        precondition(!state.shouldCancelReturn(web), "Do not cancel a web authentication session on activation")
        precondition(state.isCurrent(web), "Web SDK still owns success/cancel/failure")
        state.finish()
        precondition(!state.isCurrent(web), "Completed web result cannot be delivered twice")
        print("KakaoTalk return: 13 lifecycle and stale-callback checks passed")
    }
}
