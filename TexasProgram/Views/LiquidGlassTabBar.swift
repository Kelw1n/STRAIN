import SwiftUI

enum MainTabItem: String, CaseIterable, Identifiable {
    case today = "Главное"
    case workout = "Тренировка"
    case progress = "Активность"
    case community = "Друзья"
    case profile = "Профиль"

    var id: String { rawValue }

    var icon: String {
        switch self {
        case .today: return "house.fill"
        case .workout: return "dumbbell.fill"
        case .progress: return "chart.bar.xaxis"
        case .community: return "person.2.fill"
        case .profile: return "person.crop.circle.fill"
        }
    }
}

struct LiquidGlassTabBar: View {
    @Binding var selectedTab: MainTabItem
    @Environment(\.colorScheme) private var scheme
    @Namespace private var tabLensNamespace

    var body: some View {
        HStack(spacing: 0) {
            ForEach(MainTabItem.allCases) { tab in
                let isSelected = selectedTab == tab

                Button {
                    guard selectedTab != tab else { return }
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    withAnimation(.spring(response: 0.36, dampingFraction: 0.76)) {
                        selectedTab = tab
                    }
                } label: {
                    ZStack {
                        // Скользящая линза под активной вкладкой
                        if isSelected {
                            Capsule(style: .continuous)
                                .fill(scheme == .dark ? Color.white.opacity(0.18) : Color.black.opacity(0.08))
                                .overlay(
                                    Capsule(style: .continuous)
                                        .strokeBorder(
                                            LinearGradient(
                                                colors: [
                                                    .white.opacity(scheme == .dark ? 0.35 : 0.6),
                                                    .white.opacity(0.05)
                                                ],
                                                startPoint: .topLeading,
                                                endPoint: .bottomTrailing
                                            ),
                                            lineWidth: 1
                                        )
                                )
                                .matchedGeometryEffect(id: "active_lens", in: tabLensNamespace)
                                .shadow(color: Theme.accent.opacity(0.2), radius: 8, y: 2)
                        }

                        // Иконка
                        Image(systemName: tab.icon)
                            .font(.system(size: 20, weight: isSelected ? .bold : .medium))
                            .foregroundStyle(isSelected ? AnyShapeStyle(Color.white) : AnyShapeStyle(Color.secondary))
                            .scaleEffect(isSelected ? 1.08 : 1.0)
                            .frame(maxWidth: .infinity)
                            .frame(height: 48)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 6)
        .background {
            Capsule(style: .continuous)
                .fill(.ultraThinMaterial)
                .overlay {
                    Capsule(style: .continuous)
                        .fill(scheme == .dark ? Color.black.opacity(0.70) : Color.white.opacity(0.40))
                }
                .overlay {
                    Capsule(style: .continuous)
                        .strokeBorder(
                            LinearGradient(
                                stops: [
                                    .init(color: .white.opacity(scheme == .dark ? 0.32 : 0.65), location: 0.0),
                                    .init(color: .white.opacity(0.08), location: 0.3),
                                    .init(color: .clear, location: 0.65),
                                    .init(color: .white.opacity(scheme == .dark ? 0.16 : 0.25), location: 1.0)
                                ],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            ),
                            lineWidth: 1
                        )
                }
                .shadow(color: Color.black.opacity(scheme == .dark ? 0.45 : 0.12), radius: 22, x: 0, y: 10)
        }
        .padding(.horizontal, 22)
        .padding(.bottom, 6)
    }
}
