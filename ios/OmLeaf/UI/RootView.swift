import SwiftUI

struct RootView: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
        TabView {
            NavigationStack { PracticesView() }
                .tabItem { Label("Practise", systemImage: "leaf.fill") }
            NavigationStack { HistoryView() }
                .tabItem { Label("Progress", systemImage: "chart.bar.fill") }
            NavigationStack { SettingsView() }
                .tabItem { Label("Settings", systemImage: "gearshape.fill") }
        }
        .alert("Practise gently", isPresented: noteBinding) {
            Button("I understand") { store.acknowledgeNote() }
        } message: {
            Text(
                "OmLeaf is a timer and guide, not medical advice. Never strain. If you are pregnant or have a heart condition, " +
                "high blood pressure, epilepsy or another medical condition, ask your doctor first. Stop if you feel dizzy."
            )
        }
    }

    private var noteBinding: Binding<Bool> {
        Binding(get: { !store.settings.acknowledgedNote }, set: { _ in })
    }
}
