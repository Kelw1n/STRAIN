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
    @State private var dragX: CGFloat? = nil

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

            // Координата линзы: при перетаскивании плавно следует за пальцем,
            // при отпускании точно центрируется на выбранной вкладке.
            let targetOffset = CGFloat(currentActiveIndex) * itemWidth + 4
            let lensOffset: CGFloat = {
                if let drag = dragX {
                    return min(max(4, drag - (lensWidth / 2)), totalWidth - lensWidth - 4)
                }
                return targetOffset
            }()

            ZStack(alignment: .leading) {
                // Фоновая подложка Liquid Glass капсулы
                dockBackground

                // Скользящая физическая стеклянная линза
                Capsule(style: .continuous)
                    .fill(Color.white.opacity(isInteracting ? 0.24 : 0.16))
                    .overlay(
                        Capsule(style: .continuous)
                            .strokeBorder(
                                LinearGradient(
                                    colors: [
                                        Color.white.opacity(isInteracting ? 0.60 : 0.40),
                                        Color.white.opacity(0.08)
                                    ],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                ),
                                lineWidth: 1.2
                            )
                    )
                    .shadow(
                        color: Theme.accent.opacity(isInteracting ? 0.60 : 0.35),
                        radius: isInteracting ? 14 : 8,
                        y: 2
                    )
                    .frame(width: lensWidth, height: lensHeight)
                    .offset(x: lensOffset)
                    .scaleEffect(isInteracting ? 1.06 : 1.0)
                    .animation(
                        isInteracting ? .interactiveSpring(response: 0.16, dampingFraction: 0.82) : .spring(response: 0.32, dampingFraction: 0.72),
                        value: lensOffset
                    )
                    .animation(.spring(response: 0.25, dampingFraction: 0.7), value: isInteracting)

                // Иконки вкладок (без захватывающих события Button, всё управляется док-жестом)
                HStack(spacing: 0) {
                    ForEach(tabs) { tab in
                        let isActive = currentActiveTab == tab

                        ZStack {
                            Image(systemName: tab.icon)
                                .font(.system(size: 20, weight: isActive ? .bold : .medium))
                                .foregroundStyle(isActive ? AnyShapeStyle(Color.white) : AnyShapeStyle(Color.white.opacity(0.42)))
                                .scaleEffect(isActive ? (isInteracting ? 1.22 : 1.10) : 1.0)
                                .animation(.spring(response: 0.25, dampingFraction: 0.7), value: isActive)
                                .animation(.spring(response: 0.25, dampingFraction: 0.7), value: isInteracting)
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: lensHeight)
                        .contentShape(Rectangle())
                    }
                }
            }
            .frame(width: totalWidth, height: barHeight)
            .scaleEffect(isInteracting ? 0.985 : 1.0)
            .animation(.spring(response: 0.30, dampingFraction: 0.75), value: isInteracting)
            .overlay(alignment: .topLeading) {
                // Всплывающий стеклянный HUD-пузырёк с названием вкладки во время свайпа/зажатия
                if isInteracting {
                    Text(currentActiveTab.rawValue)
                        .font(.system(size: 12, weight: .bold, design: .rounded))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 7)
                        .background {
                            Capsule()
                                .fill(.ultraThinMaterial)
                                .overlay {
                                    Capsule().fill(Color(red: 0.10, green: 0.10, blue: 0.12).opacity(0.92))
                                }
                                .overlay {
                                    Capsule().strokeBorder(Theme.accent.opacity(0.6), lineWidth: 1.2)
                                }
                                .shadow(color: Color.black.opacity(0.65), radius: 12, y: 6)
                        }
                        .offset(x: max(10, min(totalWidth - 96, lensOffset + (lensWidth / 2) - 44)), y: -46)
                        .animation(.interactiveSpring(response: 0.16, dampingFraction: 0.82), value: lensOffset)
                        .transition(.scale(scale: 0.8).combined(with: .opacity))
                }
            }
            .contentShape(Capsule(style: .continuous))
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        handleDrag(at: value.location.x, totalWidth: totalWidth, itemWidth: itemWidth)
                    }
                    .onEnded { value in
                        handleDragEnd(at: value.location.x, totalWidth: totalWidth, itemWidth: itemWidth)
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
            withAnimation(.spring(response: 0.22, dampingFraction: 0.7)) {
                isInteracting = true
            }
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        }

        dragX = x

        let clampedX = min(max(x, 0), totalWidth - 1)
        let index = Int(clampedX / itemWidth)
        let targetTab = tabs[min(max(index, 0), tabs.count - 1)]

        if targetTab != currentActiveTab {
            UISelectionFeedbackGenerator().selectionChanged()
            withAnimation(.spring(response: 0.25, dampingFraction: 0.72)) {
                hoveredTab = targetTab
            }
        }
    }

    private func handleDragEnd(at x: CGFloat, totalWidth: CGFloat, itemWidth: CGFloat) {
        let clampedX = min(max(x, 0), totalWidth - 1)
        let index = Int(clampedX / itemWidth)
        let finalTab = hoveredTab ?? tabs[min(max(index, 0), tabs.count - 1)]

        if selectedTab != finalTab {
            selectedTab = finalTab
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        } else {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        }

        withAnimation(.spring(response: 0.32, dampingFraction: 0.72)) {
            isInteracting = false
            dragX = nil
            hoveredTab = nil
        }
    }
}
