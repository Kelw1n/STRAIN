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

    private let barHeight: CGFloat = 62
    private let lensHeight: CGFloat = 48

    var body: some View {
        HStack(spacing: 4) {
            ForEach(MainTabItem.allCases) { tab in
                let isSelected = selectedTab == tab

                Button {
                    guard selectedTab != tab else { return }
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    withAnimation(.spring(response: 0.34, dampingFraction: 0.74)) {
                        selectedTab = tab
                    }
                } label: {
                    ZStack {
                        // Скользящая линза под активной вкладкой
                        if isSelected {
                            Capsule(style: .continuous)
                                .fill(Color.white.opacity(0.16))
                                .overlay(
                                    Capsule(style: .continuous)
                                        .strokeBorder(
                                            LinearGradient(
                                                colors: [
                                                    Color.white.opacity(0.40),
                                                    Color.white.opacity(0.08)
                                                ],
                                                startPoint: .topLeading,
                                                endPoint: .bottomTrailing
                                            ),
                                            lineWidth: 1
                                        )
                                )
                                .matchedGeometryEffect(id: "active_lens", in: tabLensNamespace)
                                .shadow(color: Theme.accent.opacity(0.35), radius: 8, y: 2)
                                .frame(height: lensHeight)
                                .padding(.horizontal, 2)
                        }

                        // Иконка вкладки
                        Image(systemName: tab.icon)
                            .font(.system(size: 20, weight: isSelected ? .bold : .medium))
                            .foregroundStyle(isSelected ? AnyShapeStyle(Color.white) : AnyShapeStyle(Color.white.opacity(0.45)))
                            .scaleEffect(isSelected ? 1.08 : 1.0)
                            .frame(maxWidth: .infinity)
                            .frame(height: lensHeight)
                    }
                    .frame(height: lensHeight)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 8)
        .frame(height: barHeight)
        .background {
            Capsule(style: .continuous)
                .fill(.ultraThinMaterial)
                .overlay {
                    Capsule(style: .continuous)
                        .fill(Color(red: 0.08, green: 0.08, blue: 0.09).opacity(0.88))
                }
                .overlay {
                    Capsule(style: .continuous)
                        .strokeBorder(
                            LinearGradient(
                                stops: [
                                    .init(color: Color.white.opacity(0.32), location: 0.0),
                                    .init(color: Color.white.opacity(0.08), location: 0.3),
                                    .init(color: Color.clear, location: 0.65),
                                    .init(color: Color.white.opacity(0.18), location: 1.0)
                                ],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            ),
                            lineWidth: 1
                        )
                }
                .shadow(color: Color.black.opacity(0.60), radius: 24, x: 0, y: 12)
        }
        .frame(height: barHeight)
        .padding(.horizontal, 20)
        .padding(.bottom, 8)
    }
}
