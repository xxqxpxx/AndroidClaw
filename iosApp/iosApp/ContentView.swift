import SwiftUI
// import Shared  // Uncomment when shared framework is built

struct ContentView: View {
    var body: some View {
        NavigationView {
            VStack {
                Text("AndroidClaw")
                    .font(.largeTitle)
                    .padding()
                Text("Voice AI Assistant")
                    .font(.subheadline)
                    .foregroundColor(.gray)
                // Compose Multiplatform view will be hosted here
                // ComposeView()
                //     .ignoresSafeArea()
            }
            .navigationTitle("AndroidClaw")
        }
    }
}

// UIViewControllerRepresentable bridge for Compose Multiplatform
// struct ComposeView: UIViewControllerRepresentable {
//     func makeUIViewController(context: Context) -> UIViewController {
//         return SharedKt.MainViewController()
//     }
//     func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
// }
