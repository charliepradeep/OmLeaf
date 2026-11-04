import SwiftUI

@main
struct OmLeafApp: App {
    @StateObject private var store = AppStore()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(store)
                .tint(.omTeal)
        }
    }
}
