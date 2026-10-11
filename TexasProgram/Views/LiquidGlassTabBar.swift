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

    @State private var isInteracting: Bool = false
    @State private var hoveredTab: MainTabItem? = nil

    private let barHeight: CGFloat = 62
    private let lensHeight: CGFloat = 48
    private let tabs = MainTabItem.allCases

    private var currentActiveTab: MainTabItem {
        hoveredTab ?? selectedTab
    }

    private var currentActiveIndex: Int {
        tabs.firstIndex(of: currentActiveTab) ?? 0
    }

    var body: some View {
        GeometryReader { geometry in
            let totalWidth = geometry.size.width
            let itemWidth = max(1, totalWidth / CGFloat(tabs.count))
            let lensWidth = itemWidth - 8
            let lensOffset = CGFloat(currentActiveIndex) * itemWidth + 4

            ZStack(alignment: .leading) {
                // Фоновая подложка Liquid Glass капсулы
                dockBackground

                // Скользящая физическая линза (одна плавная форма с физикой пружины)
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
                    .shadow(color: Theme.accent.opacity(isInteracting ? 0.55 : 0.35), radius: isInteracting ? 12 : 8, y: 2)
                    .frame(width: lensWidth, height: lensHeight)
                    .offset(x: lensOffset)
                    .animation(.spring(response: 0.30, dampingFraction: 0.72), value: currentActiveIndex)
                    .scaleEffect(isInteracting ? 1.05 : 1.0)
                    .animation(.spring(response: 0.25, dampingFraction: 0.7), value: isInteracting)

                // Иконки вкладок
                HStack(spacing: 0) {
                    ForEach(tabs) { tab in
                        let isActive = currentActiveTab == tab

                        Button {
                            selectTab(tab)
                        } label: {
                            Image(systemName: tab.icon)
                                .font(.system(size: 20, weight: isActive ? .bold : .medium))
                                .foregroundStyle(isActive ? AnyShapeStyle(Color.white) : AnyShapeStyle(Color.white.opacity(0.42)))
                                .scaleEffect(isActive ? (isInteracting ? 1.20 : 1.10) : 1.0)
                                .animation(.spring(response: 0.28, dampingFraction: 0.7), value: isActive)
                                .animation(.spring(response: 0.28, dampingFraction: 0.7), value: isInteracting)
                                .frame(maxWidth: .infinity)
                                .frame(height: lensHeight)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .frame(width: totalWidth, height: barHeight)
            .scaleEffect(isInteracting ? 0.985 : 1.0)
            .animation(.spring(response: 0.30, dampingFraction: 0.75), value: isInteracting)
            .overlay(alignment: .topLeading) {
                // Всплывающая стеклянная плашка с названием меню во время свайпа/зажатия
                if isInteracting {
                    Text(currentActiveTab.rawValue)
                        .font(.system(size: 12, weight: .bold, design: .rounded))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background {
                            Capsule()
                                .fill(.ultraThinMaterial)
                                .overlay {
                                    Capsule().fill(Color(red: 0.10, green: 0.10, blue: 0.12).opacity(0.92))
                                }
                                .overlay {
                                    Capsule().strokeBorder(Theme.accent.opacity(0.5), lineWidth: 1)
                                }
                                .shadow(color: Color.black.opacity(0.6), radius: 10, y: 5)
                        }
                        .offset(x: max(10, min(totalWidth - 90, lensOffset + (lensWidth / 2) - 40)), y: -42)
                        .animation(.spring(response: 0.28, dampingFraction: 0.72), value: currentActiveIndex)
                        .transition(.scale(scale: 0.8).combined(with: .opacity))
                }
            }
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        handleDrag(at: value.location.x, totalWidth: totalWidth, itemWidth: itemWidth)
                    }
                    .onEnded { _ in
                        handleDragEnd()
                    }
            )
        }
        .frame(height: barHeight)
        .padding(.horizontal, 20)
        .padding(.bottom, 8)
    }

    private var dockBackground: some View {
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

    private func handleDrag(at x: CGFloat, totalWidth: CGFloat, itemWidth: CGFloat) {
        if !isInteracting {
            withAnimation(.spring(response: 0.25, dampingFraction: 0.7)) {
                isInteracting = true
            }
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        }
        let clampedX = min(max(x, 0), totalWidth - 1)
        let index = Int(clampedX / itemWidth)
        let targetTab = tabs[min(max(index, 0), tabs.count - 1)]
        if targetTab != currentActiveTab {
            UISelectionFeedbackGenerator().selectionChanged()
            withAnimation(.spring(response: 0.28, dampingFraction: 0.72)) {
                hoveredTab = targetTab
            }
        }
    }

    private func handleDragEnd() {
        if let target = hoveredTab, target != selectedTab {
            selectedTab = target
        }
        withAnimation(.spring(response: 0.30, dampingFraction: 0.75)) {
            isInteracting = false
            hoveredTab = nil
        }
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
    }

    private func selectTab(_ tab: MainTabItem) {
        guard selectedTab != tab else { return }
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        withAnimation(.spring(response: 0.32, dampingFraction: 0.74)) {
            selectedTab = tab
            hoveredTab = nil
        }
    }
}
