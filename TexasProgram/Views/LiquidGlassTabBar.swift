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

    @State private var isInteracting: Bool = false

    private let barHeight: CGFloat = 62
    private let lensHeight: CGFloat = 48
    private let tabs = MainTabItem.allCases

    var body: some View {
        GeometryReader { geometry in
            let totalWidth = geometry.size.width
            let itemWidth = max(1, totalWidth / CGFloat(tabs.count))

            HStack(spacing: 0) {
                ForEach(tabs) { tab in
                    let isSelected = selectedTab == tab

                    Button {
                        selectTab(tab)
                    } label: {
                        ZStack {
                            // Скользящая линза под активной вкладкой
                            if isSelected {
                                Capsule(style: .continuous)
                                    .fill(Color.white.opacity(isInteracting ? 0.22 : 0.16))
                                    .overlay(
                                        Capsule(style: .continuous)
                                            .strokeBorder(
                                                LinearGradient(
                                                    colors: [
                                                        Color.white.opacity(isInteracting ? 0.55 : 0.40),
                                                        Color.white.opacity(0.08)
                                                    ],
                                                    startPoint: .topLeading,
                                                    endPoint: .bottomTrailing
                                                ),
                                                lineWidth: 1
                                            )
                                    )
                                    .matchedGeometryEffect(id: "active_lens", in: tabLensNamespace)
                                    .shadow(color: Theme.accent.opacity(isInteracting ? 0.55 : 0.35), radius: isInteracting ? 12 : 8, y: 2)
                                    .frame(height: lensHeight)
                                    .padding(.horizontal, 4)
                                    .scaleEffect(isInteracting ? 1.05 : 1.0)
                            }

                            // Иконка вкладки
                            Image(systemName: tab.icon)
                                .font(.system(size: 20, weight: isSelected ? .bold : .medium))
                                .foregroundStyle(isSelected ? AnyShapeStyle(Color.white) : AnyShapeStyle(Color.white.opacity(0.45)))
                                .scaleEffect(isSelected ? (isInteracting ? 1.15 : 1.08) : 1.0)
                                .frame(maxWidth: .infinity)
                                .frame(height: lensHeight)
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: lensHeight)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            .frame(width: totalWidth, height: barHeight)
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
            .scaleEffect(isInteracting ? 0.985 : 1.0)
            .animation(.spring(response: 0.30, dampingFraction: 0.75), value: isInteracting)
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        if !isInteracting {
                            isInteracting = true
                            UIImpactFeedbackGenerator(style: .light).impactOccurred()
                        }
                        let clampedX = min(max(value.location.x, 0), totalWidth - 1)
                        let index = Int(clampedX / itemWidth)
                        let targetTab = tabs[min(max(index, 0), tabs.count - 1)]
                        if targetTab != selectedTab {
                            UIImpactFeedbackGenerator(style: .selection).selectionChanged()
                            withAnimation(.spring(response: 0.28, dampingFraction: 0.72)) {
                                selectedTab = targetTab
                            }
                        }
                    }
                    .onEnded { _ in
                        withAnimation(.spring(response: 0.30, dampingFraction: 0.75)) {
                            isInteracting = false
                        }
                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                    }
            )
        }
        .frame(height: barHeight)
        .padding(.horizontal, 20)
        .padding(.bottom, 8)
    }

    private func selectTab(_ tab: MainTabItem) {
        guard selectedTab != tab else { return }
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        withAnimation(.spring(response: 0.34, dampingFraction: 0.74)) {
            selectedTab = tab
        }
    }
}
