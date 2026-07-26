import SwiftUI
import UIKit

struct AlternateIconPicker: View {
    @State private var selectedIcon = UIApplication.shared.alternateIconName ?? "AppIconDark"
    @State private var errorMessage: String?

    var body: some View {
        Picker("Вариант", selection: $selectedIcon) {
            Label("Тёмная", systemImage: "moon.fill").tag("AppIconDark")
            Label("Светлая", systemImage: "sun.max.fill").tag("AppIconLight")
        }
        .pickerStyle(.segmented)
        .onChange(of: selectedIcon) { _, newValue in
            guard UIApplication.shared.supportsAlternateIcons else {
                errorMessage = "Эта версия iOS не поддерживает смену иконки."
                return
            }
            let target: String? = newValue == "AppIconDark" ? nil : newValue
            UIApplication.shared.setAlternateIconName(target) { error in
                errorMessage = error?.localizedDescription
            }
        }
        if let errorMessage { Text(errorMessage).font(.caption).foregroundStyle(.red) }
    }
}
