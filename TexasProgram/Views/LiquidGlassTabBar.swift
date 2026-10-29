import SwiftUI

enum MainTabItem: String, CaseIterable, Identifiable {
    case today = "Главное"
    case workout = "Тренировка"
    case progress = "Активность"
    case nutrition = "Питание"
    case community = "Друзья"
    case profile = "Профиль"

    var id: String { rawValue }

    /// Контурная иконка — так вкладка выглядит вне линзы.
    var icon: String {
        switch self {
        case .today: return "house"
        case .workout: return "dumbbell"
        case .progress: return "chart.bar"
        case .nutrition: return "fork.knife"
        case .community: return "person.3"
        case .profile: return "person.crop.circle"
        }
    }

    /// Залитая — то, что видно сквозь линзу.
    /// У вилки с ножом залитого варианта нет — она и так сплошная.
    var selectedIcon: String { self == .nutrition ? icon : icon + ".fill" }

    var index: Int { Self.allCases.firstIndex(of: self) ?? 0 }
}

// MARK: - Повторное нажатие на вкладку

/// Повторное нажатие на открытую вкладку, как в приложениях iPhone: открытый
/// внутри экран закрывается, а начальный экран едет наверх. Вкладки слушают
/// свой счётчик и сами решают, что для них «начало».
@Observable
@MainActor
final class TabReselection {
    static let shared = TabReselection()

    private(set) var counts: [MainTabItem: Int] = [:]
    @ObservationIgnored private var scrollViews: [MainTabItem: WeakScrollView] = [:]

    private struct WeakScrollView { weak var view: UIScrollView? }

    func fire(_ tab: MainTabItem) { counts[tab, default: 0] += 1 }

    func count(of tab: MainTabItem) -> Int { counts[tab] ?? 0 }

    func register(_ view: UIScrollView, for tab: MainTabItem) {
        scrollViews[tab] = WeakScrollView(view: view)
    }

    /// Лента начального экрана — к самому верху, как по нажатию на часы:
    /// крупный заголовок раскрывается обратно.
    func scrollToTop(_ tab: MainTabItem) {
        guard let view = scrollViews[tab]?.view else { return }
        let top = CGPoint(x: -view.adjustedContentInset.left, y: -view.adjustedContentInset.top)
        guard abs(view.contentOffset.y - top.y) > 0.5 else { return }
        view.setContentOffset(top, animated: true)
    }
}

extension View {
    /// Метка начального экрана вкладки: по повторному нажатию его лента едет наверх.
    /// Ставится на содержимое прокрутки, а не на саму прокрутку.
    @MainActor
    func tabScrollAnchor(_ tab: MainTabItem) -> some View {
        background(TabScrollFinder(tab: tab).frame(width: 0, height: 0).allowsHitTesting(false))
    }

    /// Повторное нажатие на эту вкладку.
    @MainActor
    func onTabReselect(_ tab: MainTabItem, perform action: @escaping () -> Void) -> some View {
        onChange(of: TabReselection.shared.count(of: tab)) { _, _ in action() }
    }
}

/// Находит прокрутку, внутри которой лежит, и отдаёт её вкладке.
private struct TabScrollFinder: UIViewRepresentable {
    let tab: MainTabItem

    func makeUIView(context: Context) -> FinderView { FinderView(tab: tab) }

    func updateUIView(_ view: FinderView, context: Context) {}

    final class FinderView: UIView {
        let tab: MainTabItem

        init(tab: MainTabItem) {
            self.tab = tab
            super.init(frame: .zero)
            isUserInteractionEnabled = false
        }

        required init?(coder: NSCoder) { return nil }

        override func didMoveToWindow() {
            super.didMoveToWindow()
            guard window != nil else { return }
            var parent = superview
            while let current = parent, !(current is UIScrollView) { parent = current.superview }
            if let scroll = parent as? UIScrollView { TabReselection.shared.register(scroll, for: tab) }
        }
    }
}

/// Плавающая панель вкладок с линзой из жидкого стекла.
///
/// Палец можно поставить и вести: линза поднимается над панелью, едет за ним,
/// тянется по скорости и увеличивает иконки под собой. Вкладка под пальцем
/// открывается сразу, пока ведёшь; отпустил — линза садится на неё.
///
/// Иконки нарисованы дважды. Нижний слой — контурный, в линзе в нём дырка.
/// Верхний — залитый, виден только сквозь линзу и увеличен вокруг её центра.
/// Поэтому иконка, наполовину заехавшая под линзу, наполовину и меняется,
/// как у системной панели iOS 26.
///
/// На iOS 26 подложка, подсветка выбранной вкладки и поднятая линза — одно
/// системное стекло: линза вытекает из панели каплей и стекает обратно.
struct LiquidGlassTabBar: View {
    @Binding var selectedTab: MainTabItem
    /// Вкладки с красной точкой: например, «Друзья» с непрочитанными сообщениями.
    var badges: Set<MainTabItem> = []
    /// Нажали на уже открытую вкладку — вернуть её к началу.
    var onReselect: (MainTabItem) -> Void = { _ in }
    /// Только для снимков экрана: линза поднята в этой точке, будто под пальцем.
    /// В приложении всегда `nil`.
    var liftedAt: CGFloat? = nil
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Прозрачное стекло, как в iOS 26, или матовое системное — по настройке.
    @AppStorage(GlassPreference.storageKey) private var clearGlass = true
    /// Метки подложки и линзы в общем контейнере стекла: по ним система узнаёт
    /// их от кадра к кадру и переливает одно в другое, а не рисует заново.
    @Namespace private var glassSpace

    /// С чего началось касание: на какой вкладке, была ли она уже открыта и
    /// уводил ли палец на соседние. Новое касание узнаём по точке начала.
    @State private var press: Press?

    private struct Press {
        let start: CGPoint
        let tab: MainTabItem
        let wasSelected: Bool
        var wandered = false
    }

    /// Где палец и с какой скоростью едет. `GestureState` сбрасывается сам,
    /// даже если жест отменила система, — линза не залипнет поднятой.
    @GestureState(resetTransaction: Transaction(animation: LiquidGlassTabBar.settle))
    private var touch: Touch? = nil

    private struct Touch: Equatable {
        var x: CGFloat
        var velocity: CGFloat
        var index: Int
    }

    /// Посадка на вкладку: быстро и мягко, с едва заметной каплей в конце.
    static let settle = Animation.spring(response: 0.34, dampingFraction: 0.82)
    /// Подъём линзы в момент касания.
    private static let lift = Animation.spring(response: 0.22, dampingFraction: 0.88)
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
            let layout = Layout(width: proxy.size.width, count: tabs.count)
            bar(layout)
                .overlay(alignment: .topLeading) {
                    ForEach(tabs.filter { badges.contains($0) }) { tab in
                        Circle()
                            .fill(Theme.record)
                            .overlay(Circle().strokeBorder(Color.black, lineWidth: 2))
                            .frame(width: 11, height: 11)
                            .position(x: layout.center(of: tab.index) + 14, y: barHeight / 2 - 12)
                            .transition(.scale.combined(with: .opacity))
                    }
                    .allowsHitTesting(false)
                }
        }
        .frame(height: barHeight)
        .padding(.horizontal, 20)
        .padding(.bottom, 8)
        // Лёгкий удар при касании, щелчок на каждой пересечённой вкладке и мягкая
        // посадка, когда отпускаешь.
        .sensoryFeedback(trigger: touch?.index) { old, new in
            switch (old, new) {
            case (nil, .some): return .impact(weight: .light)
            case let (.some(a), .some(b)) where a != b: return .selection
            case (.some, nil): return .impact(flexibility: .soft, intensity: 0.7)
            default: return nil
            }
        }
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
        guard let x = touch?.x ?? liftedAt else {
            return Lens(
                midX: layout.center(of: selectedTab.index),
                width: max(layout.item - 8, 0),
                height: restLensHeight,
                lifted: false
            )
        }
        let velocity: CGFloat = touch?.velocity ?? 0
        // Растяжение по скорости: быстро ведёшь — капля вытягивается и сплющивается.
        let stretch = reduceMotion ? 0 : min(abs(velocity) / 2600, 0.24)
        // Поднятая линза шире вкладки, как у REBRN: под её краем видно соседей.
        let width = (layout.item * 1.24) * (1 + stretch)
        let height = (barHeight + overhang * 2) * (1 - stretch * 0.4)
        let half = width / 2
        let midX = min(max(x, half + 2), layout.width - half - 2)
        return Lens(midX: midX, width: width, height: height, lifted: true)
    }

    // MARK: - Панель

    /// Собрано Xcode 26 и запущено на iOS 26 — панель из системного стекла.
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
                systemGlassLayers(lens, layout: layout)
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
                    Button(tab.rawValue) {
                        if tab == selectedTab { onReselect(tab) } else { selectedTab = tab }
                    }
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
            // Вкладка под пальцем открывается сразу, пока ведёшь, — не после того,
            // как отпустишь.
            .onChanged { value in
                let tab = tabs[layout.index(at: value.location.x)]
                if press?.start != value.startLocation {
                    let first = tabs[layout.index(at: value.startLocation.x)]
                    press = Press(start: value.startLocation, tab: first, wasSelected: first == selectedTab)
                }
                if tab != press?.tab { press?.wandered = true }
                if tab != selectedTab { selectedTab = tab }
            }
            .onEnded { value in
                let tab = tabs[layout.index(at: value.location.x)]
                // Нажали на открытую вкладку и никуда не уводили палец — это «к началу».
                let reselected = press.map { $0.wasSelected && !$0.wandered && $0.tab == tab } ?? false
                press = nil
                withAnimation(reduceMotion ? nil : Self.settle) { selectedTab = tab }
                if reselected { onReselect(tab) }
            }
    }

    // MARK: - Слои

    /// iOS 26: подложка, подсветка выбранной вкладки и поднятая линза — системное
    /// стекло в одном `GlassEffectContainer`, иконки — поверх него. Иконки в линзе
    /// увеличивает тот же приём, что и до iOS 26: залитый ряд под маской линзы.
    /// Иконки над стеклом, а не под ним: сквозь матовое стекло (настройка
    /// выключена) их было бы не разглядеть.
    @ViewBuilder
    private func systemGlassLayers(_ lens: Lens, layout: Layout) -> some View {
        glassBody(lens, width: layout.width)

        lensRim(lens)

        TabIconRow(filled: false, outlineOpacity: 0.6, height: barHeight)
            .equatable()
            .mask { hole(for: lens) }

        magnifiedIcons(lens, layout: layout)
    }

    /// Подложка и линза в одном контейнере стекла. Стекло не умеет смотреть
    /// сквозь другое стекло: отдельная линза поверх подложки вырезала бы в ней
    /// дырку. В общем контейнере у них один фон, и где линза выходит за панель,
    /// стекло вытягивается из подложки каплей — как у системной панели iOS 26.
    ///
    /// Касаний стекло не ловит, жест — у всей панели. `.interactive()` здесь нет:
    /// такое стекло само ловит касания и могло бы забрать у панели ведение пальцем,
    /// а на касание и так отвечает линза. Контейнер больше панели на `glassMargin`
    /// со всех сторон: поднятая линза выходит за панель и не должна обрезаться.
    @ViewBuilder
    private func glassBody(_ lens: Lens, width: CGFloat) -> some View {
        #if compiler(>=6.2)
        if #available(iOS 26.0, *) {
            GlassEffectContainer(spacing: Self.glassSpacing) {
                ZStack {
                    dockGlass(width: width)
                    lensGlass(lens)
                }
                .frame(width: width, height: barHeight)
                .padding(Self.glassMargin)
            }
            .padding(-Self.glassMargin)
            .allowsHitTesting(false)
        }
        #endif
    }

    #if compiler(>=6.2)
    @available(iOS 26.0, *)
    private func dockGlass(width: CGFloat) -> some View {
        Color.clear
            .frame(width: width, height: barHeight)
            .glassEffect(dockMaterial, in: Capsule(style: .continuous))
            .glassEffectID(Self.dockID, in: glassSpace)
    }

    @available(iOS 26.0, *)
    private func lensGlass(_ lens: Lens) -> some View {
        Color.clear
            .frame(width: lens.width, height: lens.height)
            .glassEffect(lensMaterial(lifted: lens.lifted), in: Capsule(style: .continuous))
            .glassEffectID(Self.lensID, in: glassSpace)
            .position(x: lens.midX, y: barHeight / 2)
    }

    /// Подложка: прозрачное стекло с лёгким затемнением — лента под панелью
    /// видна, а белые иконки читаются и на светлом фото. С выключенной
    /// настройкой — системное матовое, как в iOS 27.
    @available(iOS 26.0, *)
    private var dockMaterial: Glass {
        GlassPreference.glass(clear: clearGlass, dimming: Self.dockDimming)
    }

    /// В покое линза — подсветка выбранной вкладки: то же стекло, что подложка,
    /// но светлее. Поднятая — чистое прозрачное стекло без затемнения при любой
    /// настройке: это лупа над иконками, сквозь матовую лупу смотреть незачем.
    @available(iOS 26.0, *)
    private func lensMaterial(lifted: Bool) -> Glass {
        if lifted { return Glass.clear }
        let base = clearGlass ? Glass.clear : Glass.regular
        return base.tint(Color.white.opacity(0.14))
    }
    #endif

    /// Зазор, на котором стекло линзы и подложки сливается: чем он больше, тем
    /// плавнее перешеек между ними, когда линза выходит за панель.
    private static let glassSpacing: CGFloat = 14
    private static let glassMargin: CGFloat = 16
    private static let dockDimming: Double = 0.34
    private static let dockID = "dock"
    private static let lensID = "lens"

    /// Кайма линзы. В общем контейнере край линзы внутри подложки сливается с
    /// ней — стекло рисует блик только по общему контуру. Поэтому кайма
    /// дорисована: подсветку выбранной вкладки и поднятую линзу видно всегда.
    private func lensRim(_ lens: Lens) -> some View {
        Capsule(style: .continuous)
            .strokeBorder(rimGradient(lifted: lens.lifted), lineWidth: 1)
            .frame(width: lens.width, height: lens.height)
            .position(x: lens.midX, y: barHeight / 2)
            .allowsHitTesting(false)
    }

    private func rimGradient(lifted: Bool) -> LinearGradient {
        let top: Double = lifted ? 0.34 : 0.24
        let middle: Double = lifted ? 0.06 : 0.04
        let bottom: Double = lifted ? 0.18 : 0.1
        return LinearGradient(
            stops: [
                .init(color: Color.white.opacity(top), location: 0),
                .init(color: Color.white.opacity(middle), location: 0.5),
                .init(color: Color.white.opacity(bottom), location: 1)
            ],
            startPoint: .top,
            endPoint: .bottom
        )
    }

    /// До iOS 26 линза нарисована: матовое стекло с бликом и увеличенные иконки под ней.
    @ViewBuilder
    private func drawnLayers(_ lens: Lens, layout: Layout) -> some View {
        materialDock

        TabIconRow(filled: false, outlineOpacity: 0.5, height: barHeight)
            .equatable()
            .mask { hole(for: lens) }

        lensBody(lens)

        magnifiedIcons(lens, layout: layout)
    }

    /// Залитые иконки — только внутри линзы и увеличены вокруг её центра.
    private func magnifiedIcons(_ lens: Lens, layout: Layout) -> some View {
        TabIconRow(filled: true, outlineOpacity: 1, height: barHeight)
            .equatable()
            .scaleEffect(
                lens.lifted ? magnification : 1,
                anchor: UnitPoint(x: layout.width > 0 ? lens.midX / layout.width : 0.5, y: 0.5)
            )
            .mask { lensShape(lens) }
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
        .shadow(color: Color.black.opacity(lens.lifted ? 0.45 : 0), radius: lens.lifted ? 14 : 0, y: lens.lifted ? 8 : 0)
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

/// Ряд иконок панели. Отдельная вью со сравнимыми входами: пока палец ведёт
/// линзу, меняются только маска и увеличение поверх ряда, а сам ряд SwiftUI
/// на каждом кадре не пересобирает.
private struct TabIconRow: View, Equatable {
    let filled: Bool
    /// Насколько видны контурные иконки вне линзы.
    let outlineOpacity: Double
    let height: CGFloat

    var body: some View {
        HStack(spacing: 0) {
            ForEach(MainTabItem.allCases) { tab in
                Image(systemName: filled ? tab.selectedIcon : tab.icon)
                    .font(.system(size: 21, weight: filled ? .semibold : .regular))
                    .foregroundStyle(filled ? Color.white : Color.white.opacity(outlineOpacity))
                    .frame(maxWidth: .infinity)
                    .frame(height: height)
            }
        }
    }
}
