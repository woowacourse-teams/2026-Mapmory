import Shared
import SwiftUI

private let systemBarColor = Color(red: 0.027, green: 0.090, blue: 0.106)

@main
struct MapmoryApp: App {
    var body: some Scene {
        WindowGroup {
            ZStack {
                systemBarColor
                    .ignoresSafeArea()
                ComposeView()
            }
        }
    }
}

private struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let viewController = MainViewControllerKt.MainViewController()
        viewController.view.backgroundColor = UIColor(
            red: 0.027,
            green: 0.090,
            blue: 0.106,
            alpha: 1,
        )
        return viewController
    }

    func updateUIViewController(
        _ uiViewController: UIViewController,
        context: Context
    ) {}
}
