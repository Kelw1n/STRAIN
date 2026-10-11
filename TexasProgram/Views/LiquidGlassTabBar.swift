import SwiftUI

enum MainTabItem: String, CaseIterable, Identifiable {
    case today = "Главное"
    case workout = "Тренировка"
    case progress = "Активность"
    case community = "Друзья"
    case profile = "Профиль"

    var id: String { rawValue }

    /// Контурная иконка — так вкладка выглядит вне линзы.
    var icon: String {
        switch self {
        case .today: return "house"
        case .workout: return "dumbbell"
        case .progress: return "chart.bar"
        case .community: return "person.3"
        case .profile: return "person.crop.circle"
        }
    }

    /// Залитая — то, что видно сквозь линзу.
    var selectedIcon: String { icon + ".fill" }

    var index: Int { Self.allCases.firstIndex(of: self) ?? 0 }
}

/// Плавающая панель вкладок с линзой из жидкого стекла.
///
/// Палец можно поставить и вести: линза поднимается над панелью, едет за ним,
/// тянется по скорости и увеличивает иконки под собой. Отпустил — линза
/// с пружиной садится на ближайшую вкладку, и та открывается.
///
/// Иконки нарисованы дважды. Нижний слой — контурный, в линзе в нём дырка.
/// Верхний — залитый, виден только сквозь линзу и увеличен вокруг её центра.
/// Поэтому иконка, наполовину заехавшая под линзу, наполовину и меняется,
/// как у системной панели iOS 26.
struct LiquidGlassTabBar: View {
    @Binding var selectedTab: MainTabItem
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// Где палец и с какой скоростью едет. `GestureState` сбрасывается сам,
    /// даже если жест отменила система, — линза не залипнет поднятой.
    @GestureState(resetTransaction: Transaction(animation: LiquidGlassTabBar.settle))
    private var touch: Touch? = nil

    private struct Touch: Equatable {
        var x: CGFloat
        var velocity: CGFloat
        var index: Int
    }

    /// Посадка на вкладку: чуть недодемпфирована, чтобы линза качнулась, как капля.
    static let settle = Animation.spring(response: 0.42, dampingFraction: 0.66)
    /// Подъём линзы в момент касания.
    private static let lift = Animation.spring(response: 0.26, dampingFraction: 0.72)
    /// Слежение за пальцем: почти без задержки, но не рывками.
    private static let follow = Animation.interactiveSpring(response: 0.18, dampingFraction: 0.86)

    private let tabs = MainTabItem.allCases
    private let barHeight: CGFloat = 62
    private let restLensHeight: CGFloat = 48
    /// На сколько поднятая линза выходит за верх и низ панели.
    private let overhang: CGFloat = 7
    private let magnification: CGFloat = 1.28

    var body: some View {
        GeometryReader { proxy in
            bar(Layout(width: proxy.size.width, count: tabs.count))
        }
        .frame(height: barHeight)
        .padding(.horizontal, 20)
        .padding(.bottom, 8)
        // Щелчок на каждой пересечённой вкладке, лёгкий удар при касании.
        .sensoryFeedback(trigger: touch?.index) { old, new in
            switch (old, new) {
            case (nil, .some): return .impact(weight: .light)
            case let (.some(a), .some(b)) where a != b: return .selection
            default: return nil
            }
        }
        .sensoryFeedback(.impact(weight: .medium), trigger: selectedTab)
    }

    // MARK: - Раскладка

    private struct Layout {
        let width: CGFloat
        let count: Int

        var item: CGFloat { width / CGFloat(max(count, 1)) }

        func center(of index: Int) -> CGFloat { item * (CGFloat(index) + 0.5) }

        func index(at x: CGFloat) -> Int {
            guard item > 0 else { return 0 }
            return min(max(Int(x / item), 0), count - 1)
        }
    }

    private struct Lens {
        let midX: CGFloat
        let width: CGFloat
        let height: CGFloat
        let lifted: Bool
    }

    private func lens(in layout: Layout) -> Lens {
        guard let touch else {
            return Lens(
                midX: layout.center(of: selectedTab.index),
                width: max(layout.item - 8, 0),
                height: restLensHeight,
                lifted: false
            )
        }
        // Растяжение по скорости: быстро ведёшь — капля вытягивается и сплющивается.
        let stretch = reduceMotion ? 0 : min(abs(touch.velocity) / 2600, 0.24)
        // Поднятая линза шире вкладки, как у REBRN: под её краем видно соседей.
        let width = (layout.item * 1.24) * (1 + stretch)
        let height = (barHeight + overhang * 2) * (1 - stretch * 0.4)
        let half = width / 2
        let midX = min(max(touch.x, half + 2), layout.width - half - 2)
        return Lens(midX: midX, width: width, height: height, lifted: true)
    }

    // MARK: - Панель

    /// Собрано Xcode 26 и запущено на iOS 26 — линза из системного стекла.
    private var usesSystemGlass: Bool {
        #if compiler(>=6.2)
        if #available(iOS 26.0, *) { return true }
        #endif
        return false
    }

    private func bar(_ layout: Layout) -> some View {
        let lens = lens(in: layout)
        return ZStack {
            if usesSystemGlass {
                systemGlassLayers(lens)
            } else {
                drawnLayers(lens, layout: layout)
            }
        }
        .frame(width: layout.width, height: barHeight)
        .contentShape(Capsule(style: .continuous))
        .gesture(drag(layout))
        .accessibilityRepresentation {
            HStack {
                ForEach(tabs) { tab in
                    Button(tab.rawValue) { selectedTab = tab }
                        .accessibilityAddTraits(tab == selectedTab ? .isSelected : [])
                }
            }
        }
    }

    private func drag(_ layout: Layout) -> some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .local)
            .updating($touch) { value, state, transaction in
                transaction.animation = state == nil ? Self.lift : Self.follow
                state = Touch(
                    x: value.location.x,
                    velocity: value.velocity.width,
                    index: layout.index(at: value.location.x)
                )
            }
            .onEnded { value in
                let tab = tabs[layout.index(at: value.location.x)]
                withAnimation(reduceMotion ? nil : Self.settle) { selectedTab = tab }
            }
    }

    // MARK: - Слои

    /// iOS 26: линза — прозрачное системное стекло поверх иконок. Оно само
    /// преломляет то, что под ним, с радужной каймой на краю — как у REBRN.
    /// Подложка при этом матовая: стекло не умеет смотреть сквозь другое стекло.
    @ViewBuilder
    private func systemGlassLayers(_ lens: Lens) -> some View {
        let active = touch?.index ?? selectedTab.index
        materialDock

        // В покое под выбранной вкладкой тёмная таблетка; поднятая линза её заменяет.
        Capsule(style: .continuous)
            .fill(Color.black.opacity(0.55))
            .overlay(Capsule(style: .continuous).strokeBorder(Color.white.opacity(0.08), lineWidth: 1))
            .frame(width: lens.width, height: lens.height)
            .position(x: lens.midX, y: barHeight / 2)
            .opacity(lens.lifted ? 0 : 1)

        HStack(spacing: 0) {
            ForEach(tabs) { tab in
                let isActive = tab.index == active
                Image(systemName: isActive ? tab.selectedIcon : tab.icon)
                    .font(.system(size: 21, weight: isActive ? .semibold : .regular))
                    .foregroundStyle(Color.white.opacity(isActive ? 1 : 0.55))
                    .contentTransition(.symbolEffect(.replace))
                    .frame(maxWidth: .infinity)
                    .frame(height: barHeight)
            }
        }

        glassLens(lens)
    }

    @ViewBuilder
    private func glassLens(_ lens: Lens) -> some View {
        #if compiler(>=6.2)
        if #available(iOS 26.0, *) {
            Color.clear
                .frame(width: lens.width, height: lens.height)
                .glassEffect(.clear, in: Capsule(style: .continuous))
                .opacity(lens.lifted ? 1 : 0)
                .position(x: lens.midX, y: barHeight / 2)
                .allowsHitTesting(false)
        }
        #endif
    }

    /// До iOS 26 линза нарисована: матовое стекло с бликом и увеличенные иконки под ней.
    @ViewBuilder
    private func drawnLayers(_ lens: Lens, layout: Layout) -> some View {
        materialDock

        iconRow(filled: false)
            .mask { hole(for: lens) }

        lensBody(lens)

        iconRow(filled: true)
            .scaleEffect(
                lens.lifted ? magnification : 1,
                anchor: UnitPoint(x: layout.width > 0 ? lens.midX / layout.width : 0.5, y: 0.5)
            )
            .mask { lensShape(lens) }
    }

    private func iconRow(filled: Bool) -> some View {
        HStack(spacing: 0) {
            ForEach(tabs) { tab in
                Image(systemName: filled ? tab.selectedIcon : tab.icon)
                    .font(.system(size: 21, weight: filled ? .semibold : .regular))
                    .foregroundStyle(filled ? Color.white : Color.white.opacity(0.5))
                    .frame(maxWidth: .infinity)
                    .frame(height: barHeight)
            }
        }
    }

    private func lensShape(_ lens: Lens) -> some View {
        Capsule(style: .continuous)
            .frame(width: lens.width, height: lens.height)
            .position(x: lens.midX, y: barHeight / 2)
    }

    /// Всё, кроме линзы: под ней нижний слой иконок не рисуется.
    private func hole(for lens: Lens) -> some View {
        Rectangle()
            .overlay { lensShape(lens).blendMode(.destinationOut) }
            .compositingGroup()
    }

    /// Тело линзы. В покое это тёмная таблетка, как у REBRN; поднятая —
    /// прозрачное стекло с бликом сверху и отсветом снизу.
    private func lensBody(_ lens: Lens) -> some View {
        let shape = Capsule(style: .continuous)
        return ZStack {
            shape.fill(Color.black.opacity(0.55))
                .opacity(lens.lifted ? 0 : 1)

            shape.fill(.ultraThinMaterial)
                .overlay { shape.fill(Color.white.opacity(0.06)) }
                .overlay {
                    shape
                        .fill(LinearGradient(
                            colors: [Color.white.opacity(0.24), .clear],
                            startPoint: .top,
                            endPoint: .center
                        ))
                        .padding(2)
                }
                .opacity(lens.lifted ? 1 : 0)

            shape.strokeBorder(
                LinearGradient(
                    stops: [
                        .init(color: Color.white.opacity(lens.lifted ? 0.75 : 0.12), location: 0),
                        .init(color: Color.white.opacity(lens.lifted ? 0.12 : 0.04), location: 0.45),
                        .init(color: Color.white.opacity(lens.lifted ? 0.35 : 0.06), location: 1)
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                ),
                lineWidth: lens.lifted ? 1.2 : 1
            )
        }
        .shadow(color: Color.black.opacity(lens.lifted ? 0.45 : 0), radius: 14, y: 8)
        .frame(width: lens.width, height: lens.height)
        .position(x: lens.midX, y: barHeight / 2)
    }

    private var materialDock: some View {
        Capsule(style: .continuous)
            .fill(.ultraThinMaterial)
            .overlay {
                Capsule(style: .continuous)
                    .fill(Color(red: 0.08, green: 0.08, blue: 0.09).opacity(0.72))
            }
            .overlay {
                Capsule(style: .continuous)
                    .strokeBorder(
                        LinearGradient(
                            stops: [
                                .init(color: Color.white.opacity(0.28), location: 0.0),
                                .init(color: Color.white.opacity(0.06), location: 0.35),
                                .init(color: Color.clear, location: 0.65),
                                .init(color: Color.white.opacity(0.14), location: 1.0)
                            ],
                            startPoint: .top,
                            endPoint: .bottom
                        ),
                        lineWidth: 1
                    )
            }
            .shadow(color: Color.black.opacity(0.6), radius: 24, x: 0, y: 12)
    }
}
