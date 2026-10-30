import PhotosUI
import SwiftUI
import UIKit

/// Вкладка «Питание»: дневник еды и метод Лимара — неделя наблюдения,
/// поддержка, дальше +30 г углеводов на наборе или −13 г жиров на сушке.
struct NutritionView: View {
    @Bindable var profile: ProgramProfile

    @State private var day = Calendar.current.startOfDay(for: Date())
    @State private var showingCamera = false
    @State private var showingLibrary = false
    @State private var captured: UIImage?
    @State private var libraryPick: PhotosPickerItem?
    @State private var photo: PickedPhoto?
    @State private var editing: FoodEntry?
    @State private var addingManually = false
    @State private var showingGuide = false
    @State private var showingKey = false
    @State private var weightInput = ""
    @FocusState private var typingWeight: Bool

    private var store: NutritionStore { .shared }
    private var calendar: Calendar { .current }

    private struct PickedPhoto: Identifiable {
        let id = UUID()
        let image: UIImage
    }

    private var dayEntries: [FoodEntry] { store.entries(on: day) }
    private var eaten: Nutrients { LimarMethod.total(dayEntries) }
    private var target: Nutrients? { store.data.plan?.target }
    private var dayWeight: BodyWeightEntry? { profile.bodyWeightLog.first { calendar.isDate($0.date, inSameDayAs: day) } }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    stageCard
                    dayPicker
                    dayCard
                    weightCard
                    addButtons
                    if !dayEntries.isEmpty { entriesCard }
                    guideLink
                    Spacer(minLength: 90)
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .tabScrollAnchor(.nutrition)
                .animation(Motion.card, value: store.data.plan)
                .animation(Motion.card, value: dayEntries)
            }
            .scrollIndicators(.hidden)
            .scrollDismissesKeyboard(.interactively)
            .background(Color.canvas.ignoresSafeArea())
            .navigationTitle("Питание")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { showingKey = true } label: {
                        Image(systemName: GeminiKeychain.load() == nil ? "key" : "key.fill")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(Color.ink.opacity(0.9))
                            .frame(width: 36, height: 36)
                            .background(Color.tone(0.14), in: Circle())
                    }
                    .accessibilityLabel("Ключ Google для фото еды")
                }
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("Готово") { typingWeight = false }
                }
            }
        }
        .onAppear {
            store.open(profile.profileID)
            let today = calendar.startOfDay(for: Date())
            if day > today { day = today }
        }
        .onChange(of: profile.profileID) { _, id in store.open(id) }
        // Повторное нажатие на «Питание»: открыт прошлый день — назад к сегодня,
        // сегодня — наверх.
        .onTabReselect(.nutrition) {
            let today = calendar.startOfDay(for: Date())
            if day != today {
                withAnimation(Motion.snappy) { day = today }
                weightInput = ""
            }
            TabReselection.shared.scrollToTop(.nutrition)
        }
        // Лист с результатом открываем, только когда камера уже закрылась:
        // во время её ухода новый лист не показывается.
        .fullScreenCover(isPresented: $showingCamera, onDismiss: {
            if let image = captured {
                captured = nil
                photo = PickedPhoto(image: image)
            }
        }) {
            CameraPicker(isPresented: $showingCamera) { captured = $0 }
                .ignoresSafeArea()
                .background(Color.canvas.ignoresSafeArea())
        }
        .photosPicker(isPresented: $showingLibrary, selection: $libraryPick, matching: .images)
        .onChange(of: libraryPick) { _, item in loadLibrary(item) }
        .sheet(item: $photo) { picked in
            FoodPhotoSheet(image: picked.image) { foods in addRecognized(foods) }
        }
        .sheet(item: $editing) { entry in
            FoodEntrySheet(original: entry, day: day, onSave: { store.update($0) }, onDelete: { store.remove(entry) })
        }
        .sheet(isPresented: $addingManually) {
            FoodEntrySheet(original: nil, day: day, recent: store.recentFoods(), onSave: { store.add([$0]) })
        }
        .sheet(isPresented: $showingGuide) { NutritionGuideView() }
        .sheet(isPresented: $showingKey) { GeminiKeySheet() }
    }

    // MARK: - Этап метода

    @ViewBuilder
    private var stageCard: some View {
        if let plan = store.data.plan {
            if plan.goal != nil {
                goalCard(plan)
            } else {
                observationCard(LimarMethod.observation(plan, entries: store.data.entries, weights: profile.bodyWeightLog))
            }
        } else {
            introCard
        }
    }

    private var introCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            NutritionCaption(text: "Метод Лимара", color: Theme.accent)
            Text("Найди свою норму калорий")
                .font(.system(size: 24, weight: .heavy, design: Theme.fontDesign))
                .foregroundStyle(.ink)
            Text("Без формул. Неделю ешь как обычно и записывай всё, что съел, и вес утром натощак. Средние калории при стоящем весе — твоя поддержка.")
                .font(.system(size: 14))
                .foregroundStyle(Color.ink.opacity(0.7))
                .fixedSize(horizontal: false, vertical: true)
            NutritionButton(title: "Начать неделю наблюдения", systemImage: "play.fill") {
                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                store.startObservation()
            }
        }
        .nutritionCard()
    }

    @ViewBuilder
    private func observationCard(_ state: LimarMethod.Observation) -> some View {
        switch state {
        case let .collecting(dayNumber, week):
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    NutritionCaption(text: "Неделя наблюдения · день \(min(dayNumber, 7)) из 7", color: Theme.accent)
                    Spacer()
                    restartMenu
                }
                weekDots(week.start)
                Text("Ничего не меняй в питании — просто записывай еду и вес натощак каждое утро.")
                    .font(.system(size: 14))
                    .foregroundStyle(Color.ink.opacity(0.7))
                    .fixedSize(horizontal: false, vertical: true)
                Text("Записано: еда — \(week.foodDays) дн., вес — \(week.weightDays) дн.")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Color.ink.opacity(0.45))
            }
            .nutritionCard()
        case let .notEnough(week):
            VStack(alignment: .leading, spacing: 12) {
                NutritionCaption(text: "Неделя прошла", color: Theme.warning)
                Text("Записано мало: еда — \(week.foodDays) из 7 дней, вес — \(week.weightDays) из 7. Чтобы среднее не врало, нужно хотя бы \(LimarMethod.minimumDays) дней того и другого.")
                    .font(.system(size: 14))
                    .foregroundStyle(Color.ink.opacity(0.75))
                    .fixedSize(horizontal: false, vertical: true)
                NutritionButton(title: "Начать неделю заново", systemImage: "arrow.clockwise") {
                    store.startObservation()
                }
            }
            .nutritionCard()
        case let .done(week, maintenance, shift):
            maintenanceCard(week: week, maintenance: maintenance, shift: shift)
        }
    }

    private func maintenanceCard(week: LimarMethod.Week, maintenance: Nutrients, shift: Double?) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            NutritionCaption(text: "Твоя поддержка", color: Theme.accent)
            kcalHeadline(maintenance.kcal)
            Text(NutritionFormat.macros(maintenance))
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Color.ink.opacity(0.75))
            if let weight = week.weight {
                Text("Средний вес \(WeightFormat.decimal((weight * 10).rounded() / 10)) кг · записано \(week.foodDays) дн.")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Color.ink.opacity(0.45))
            }
            if let shift {
                Text("За неделю вес сдвинулся на \(NutritionFormat.change(shift)) — значит, это ещё не совсем поддержка. Можно понаблюдать ещё неделю или начать так: недельные проверки поправят норму.")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Theme.warning)
                    .fixedSize(horizontal: false, vertical: true)
            }
            NutritionCaption(text: "Что дальше")
                .padding(.top, 4)
            ForEach(NutritionGoal.allCases) { goal in
                Button {
                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                    store.choose(goal, maintenance: maintenance)
                } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(goal.title)
                                .font(.system(size: 16, weight: .bold))
                                .foregroundStyle(.ink)
                            Text(goal.explanation)
                                .font(.system(size: 12))
                                .foregroundStyle(Color.ink.opacity(0.5))
                        }
                        Spacer()
                        Image(systemName: "chevron.right")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(Color.ink.opacity(0.3))
                    }
                    .padding(14)
                    .background(Color.tone(0.12), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
                .buttonStyle(.pressable)
            }
            NutritionButton(title: "Понаблюдать ещё неделю", primary: false) {
                store.startObservation()
            }
        }
        .nutritionCard()
    }

    private func goalCard(_ plan: NutritionPlan) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                NutritionCaption(text: "\(plan.goal?.title ?? "") · норма на день", color: Theme.accent)
                Spacer()
                goalMenu(plan)
            }
            if let target = plan.target {
                kcalHeadline(target.kcal)
                Text(NutritionFormat.macros(target))
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Color.ink.opacity(0.75))
            }
            if let check = LimarMethod.check(plan, weights: profile.bodyWeightLog) {
                checkBlock(check, goal: plan.goal ?? .hold)
            } else {
                Text(nextCheckLine(plan))
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Color.ink.opacity(0.45))
            }
        }
        .nutritionCard()
    }

    private func nextCheckLine(_ plan: NutritionPlan) -> String {
        var parts: [String] = []
        if let maintenance = plan.maintenance { parts.append("Поддержка \(NutritionFormat.kcal(maintenance.kcal)) ккал") }
        if let start = plan.stepStart,
           let due = calendar.date(byAdding: .day, value: LimarMethod.weekLength, to: start) {
            let left = max(calendar.dateComponents([.day], from: calendar.startOfDay(for: Date()), to: due).day ?? 0, 1)
            parts.append("проверка веса через \(left) дн.")
        }
        return parts.joined(separator: " · ")
    }

    private func checkBlock(_ check: LimarMethod.Check, goal: NutritionGoal) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            NutritionCaption(text: "Неделя прошла")
            Text(changeLine(check.change))
                .font(.system(size: 15, weight: .bold))
                .foregroundStyle(.ink)
            Text(adviceLine(check, goal: goal))
                .font(.system(size: 14))
                .foregroundStyle(Color.ink.opacity(0.75))
                .fixedSize(horizontal: false, vertical: true)
            if check.lowFat {
                Text("Жиров станет \(NutritionFormat.grams(check.proposed.fat)) г — меньше \(WeightFormat.decimal(LimarMethod.fatFloorPerKg)) г на кило веса. Ниже лучше не опускаться.")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Theme.warning)
                    .fixedSize(horizontal: false, vertical: true)
            }
            if check.advice == .keep {
                NutritionButton(title: "Ещё неделю на этой норме", systemImage: "checkmark") {
                    store.apply(check.proposed, note: "Без изменений")
                }
            } else {
                NutritionButton(title: "Применить: \(NutritionFormat.kcal(check.proposed.kcal)) ккал", systemImage: "checkmark") {
                    UINotificationFeedbackGenerator().notificationOccurred(.success)
                    store.apply(check.proposed, note: stepNote(check.advice))
                }
                NutritionButton(title: "Оставить как есть", primary: false) {
                    if let target = store.data.plan?.target { store.apply(target, note: "Без изменений") }
                }
            }
        }
        .padding(14)
        .background(Color.tone(0.12), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private func changeLine(_ change: Double?) -> String {
        guard let change else { return "Мало замеров веса — нужно хотя бы \(LimarMethod.minimumDays) за неделю" }
        if abs(change) < 0.05 { return "Средний вес не изменился за неделю" }
        return "Средний вес \(NutritionFormat.change(change)) к прошлой неделе"
    }

    private func adviceLine(_ check: LimarMethod.Check, goal: NutritionGoal) -> String {
        let next = "\(NutritionFormat.kcal(check.proposed.kcal)) ккал"
        switch check.advice {
        case .addCarbs where goal == .hold: return "Вес уходит вниз — добавь 30 г углеводов, норма станет \(next)."
        case .addCarbs: return "Вес почти не растёт — добавь ещё 30 г углеводов, норма станет \(next)."
        case .removeCarbs: return "Вес растёт — убери 30 г углеводов, норма станет \(next)."
        case .cutFat: return "Вес почти не уходит — убери ещё 13 г жиров, норма станет \(next)."
        case .keep:
            if check.change == nil { return "Записывай вес натощак каждое утро — без него не видно, работает ли норма." }
            switch goal {
            case .bulk: return "Вес растёт — норма работает, держи её ещё неделю."
            case .cut: return "Вес уходит — норма работает, держи её ещё неделю."
            case .hold: return "Вес на месте — так и держи."
            }
        }
    }

    private func stepNote(_ advice: LimarMethod.Advice) -> String {
        switch advice {
        case .addCarbs: return "+30 г углеводов"
        case .removeCarbs: return "−30 г углеводов"
        case .cutFat: return "−13 г жиров"
        case .keep: return "Без изменений"
        }
    }

    private var restartMenu: some View {
        Menu {
            Button {
                store.startObservation()
            } label: {
                Label("Начать неделю заново", systemImage: "arrow.clockwise")
            }
            Button(role: .destructive) {
                store.resetPlan()
            } label: {
                Label("Выйти из метода", systemImage: "xmark")
            }
        } label: {
            menuLabel
        }
    }

    private func goalMenu(_ plan: NutritionPlan) -> some View {
        Menu {
            if let maintenance = plan.maintenance {
                ForEach(NutritionGoal.allCases.filter { $0 != plan.goal }) { goal in
                    Button {
                        store.choose(goal, maintenance: maintenance)
                    } label: {
                        Label("Цель: \(goal.title.lowercased())", systemImage: "target")
                    }
                }
            }
            Button {
                store.startObservation()
            } label: {
                Label("Заново найти поддержку", systemImage: "arrow.clockwise")
            }
            Button(role: .destructive) {
                store.resetPlan()
            } label: {
                Label("Выйти из метода", systemImage: "xmark")
            }
        } label: {
            menuLabel
        }
    }

    private var menuLabel: some View {
        Image(systemName: "ellipsis")
            .font(.system(size: 14, weight: .bold))
            .foregroundStyle(Color.ink.opacity(0.5))
            .frame(width: 32, height: 28)
            .contentShape(Rectangle())
    }

    private func kcalHeadline(_ kcal: Double) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 6) {
            Text(NutritionFormat.kcal(kcal))
                .font(.system(size: 44, weight: .heavy, design: Theme.fontDesign))
                .monospacedDigit()
                .foregroundStyle(.ink)
                .contentTransition(.numericText())
            Text("ккал")
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(Color.ink.opacity(0.5))
        }
    }

    /// Дни с замером веса — один проход по замерам, а не по проходу на каждый день.
    private var weighedDays: Set<Date> {
        Set(profile.bodyWeightLog.map { calendar.startOfDay(for: $0.date) })
    }

    /// Семь дней недели наблюдения: кружок — еда записана, полоска — вес.
    private func weekDots(_ start: Date) -> some View {
        let weighedDays = self.weighedDays
        return HStack(spacing: 0) {
            ForEach(0..<LimarMethod.weekLength, id: \.self) { offset in
                let date = calendar.date(byAdding: .day, value: offset, to: start) ?? start
                let food = !store.entries(on: date).isEmpty
                let weight = weighedDays.contains(calendar.startOfDay(for: date))
                let isToday = calendar.isDateInToday(date)
                VStack(spacing: 6) {
                    Text(RuDate.short(weekday: calendar.component(.weekday, from: date)))
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(Color.ink.opacity(isToday ? 0.9 : 0.4))
                    ZStack {
                        Circle()
                            .fill(food ? Theme.accent : Color.tone(0.14))
                        if food {
                            Image(systemName: "checkmark")
                                .font(.system(size: 11, weight: .black))
                                .foregroundStyle(.canvas)
                        }
                    }
                    .frame(width: 30, height: 30)
                    .overlay(Circle().strokeBorder(Color.ink.opacity(isToday ? 0.8 : 0), lineWidth: 1.5))
                    Capsule()
                        .fill(weight ? Theme.success : Color.tone(0.16))
                        .frame(width: 16, height: 4)
                }
                .frame(maxWidth: .infinity)
            }
        }
    }

    // MARK: - День

    private var dayPicker: some View {
        HStack {
            Button { shiftDay(-1) } label: { chevron("chevron.left") }
                .accessibilityLabel("Предыдущий день")
            Spacer()
            Text(NutritionFormat.dayTitle(day))
                .font(.system(size: 17, weight: .bold))
                .foregroundStyle(.ink)
                .contentTransition(.opacity)
            Spacer()
            Button { shiftDay(1) } label: { chevron("chevron.right") }
                .disabled(calendar.isDateInToday(day))
                .opacity(calendar.isDateInToday(day) ? 0.3 : 1)
                .accessibilityLabel("Следующий день")
        }
        .padding(.horizontal, 4)
    }

    private func chevron(_ name: String) -> some View {
        Image(systemName: name)
            .font(.system(size: 14, weight: .bold))
            .foregroundStyle(.ink)
            .frame(width: 40, height: 40)
            .background(Color.tone(0.14), in: Circle())
    }

    private func shiftDay(_ delta: Int) {
        guard let next = calendar.date(byAdding: .day, value: delta, to: day),
              next <= calendar.startOfDay(for: Date()) else { return }
        UISelectionFeedbackGenerator().selectionChanged()
        withAnimation(Motion.snappy) { day = next }
        weightInput = ""
    }

    private var dayCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            NutritionCaption(text: "Съедено")
            HStack(alignment: .firstTextBaseline) {
                kcalHeadline(eaten.kcal)
                Spacer()
                if let target {
                    VStack(alignment: .trailing, spacing: 2) {
                        Text("из \(NutritionFormat.kcal(target.kcal))")
                            .font(.system(size: 15, weight: .bold, design: Theme.fontDesign))
                            .foregroundStyle(Color.ink.opacity(0.75))
                        Text(leftLine(target))
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(eaten.kcal > target.kcal ? Theme.warning : Color.ink.opacity(0.45))
                    }
                }
            }
            if let target, target.kcal > 0 {
                LineMeter(value: eaten.kcal / target.kcal)
            }
            HStack(spacing: 10) {
                macroTile("Белки", eaten.protein, target?.protein)
                macroTile("Жиры", eaten.fat, target?.fat)
                macroTile("Углеводы", eaten.carbs, target?.carbs)
            }
        }
        .nutritionCard()
    }

    private func leftLine(_ target: Nutrients) -> String {
        let left = target.kcal - eaten.kcal
        return left >= 0 ? "осталось \(NutritionFormat.kcal(left))" : "сверху \(NutritionFormat.kcal(-left))"
    }

    private func macroTile(_ title: String, _ value: Double, _ goal: Double?) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Color.ink.opacity(0.5))
            // Норма — строкой ниже: на узком iPhone рядом с числом она не влезала,
            // и «100» переносилось на две строки.
            Text(NutritionFormat.grams(value) + (goal == nil ? " г" : ""))
                .font(.system(size: 20, weight: .heavy, design: Theme.fontDesign))
                .monospacedDigit()
                .foregroundStyle(.ink)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            if let goal {
                Text("из \(NutritionFormat.grams(goal)) г")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(Color.ink.opacity(0.4))
                    .lineLimit(1)
            }
            if let goal, goal > 0 {
                LineMeter(value: value / goal, height: 4)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(Color.tone(0.12), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    // MARK: - Вес натощак

    private var weightCard: some View {
        let dayWeight = self.dayWeight
        return VStack(alignment: .leading, spacing: 12) {
            HStack {
                NutritionCaption(text: "Вес натощак")
                Spacer()
                if let dayWeight {
                    Text(WeightFormat.kilogramsPrecise(dayWeight.weight))
                        .font(.system(size: 15, weight: .bold, design: Theme.fontDesign))
                        .foregroundStyle(Theme.success)
                }
            }
            HStack(spacing: 10) {
                TextField(dayWeight == nil ? "Утром, кг" : "Поправить, кг", text: $weightInput)
                    .keyboardType(.decimalPad)
                    .focused($typingWeight)
                    .font(.system(size: 18, weight: .bold, design: Theme.fontDesign))
                    .foregroundStyle(.ink)
                    .padding(.horizontal, 14)
                    .frame(height: 50)
                    .background(Color(white: typingWeight ? 0.17 : 0.13), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(Theme.accent.opacity(typingWeight ? 1 : 0), lineWidth: 1.5))
                    .animation(.easeOut(duration: 0.15), value: typingWeight)
                Button(action: recordWeight) {
                    Text("Записать")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(parsedWeight == nil ? Color.ink.opacity(0.4) : .canvas)
                        .padding(.horizontal, 18)
                        .frame(height: 50)
                        .background(parsedWeight == nil ? Color.tone(0.16) : Theme.accent, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                .buttonStyle(.pressable)
                .disabled(parsedWeight == nil)
            }
            Text("Попадает в «Замеры веса». Взвешивайся каждое утро натощак — так средний вес недели честный.")
                .font(.system(size: 12))
                .foregroundStyle(Color.ink.opacity(0.4))
                .fixedSize(horizontal: false, vertical: true)
        }
        .nutritionCard()
    }

    private var parsedWeight: Double? {
        let text = weightInput.replacingOccurrences(of: ",", with: ".").trimmingCharacters(in: .whitespaces)
        guard let value = Double(text), value > 20, value < 400 else { return nil }
        return value
    }

    private func recordWeight() {
        guard let parsedWeight else { return }
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        withAnimation(Motion.card) {
            profile.recordBodyWeight(parsedWeight, on: day)
            weightInput = ""
            typingWeight = false
        }
    }

    // MARK: - Добавить еду

    private var addButtons: some View {
        HStack(spacing: 10) {
            Menu {
                if CameraPicker.isAvailable {
                    Button { startPhoto(camera: true) } label: { Label("Снять", systemImage: "camera") }
                }
                Button { startPhoto(camera: false) } label: { Label("Из галереи", systemImage: "photo.on.rectangle") }
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: "camera.fill").font(.system(size: 15, weight: .bold))
                    Text("Сфоткать еду").font(.system(size: 15, weight: .bold))
                }
                .foregroundStyle(.canvas)
                .frame(maxWidth: .infinity)
                .frame(height: 52)
                .background(Theme.accent, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            }
            Button {
                addingManually = true
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: "square.and.pencil").font(.system(size: 15, weight: .bold))
                    Text("Вручную").font(.system(size: 15, weight: .bold))
                }
                .foregroundStyle(.ink)
                .frame(maxWidth: .infinity)
                .frame(height: 52)
                .background(Color.tone(0.16), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            }
            .buttonStyle(.pressable)
        }
    }

    /// Без ключа фото разобрать нечем — сначала просим ключ.
    private func startPhoto(camera: Bool) {
        guard GeminiKeychain.load() != nil else {
            showingKey = true
            return
        }
        if camera { showingCamera = true } else { showingLibrary = true }
    }

    private func loadLibrary(_ item: PhotosPickerItem?) {
        guard let item else { return }
        Task {
            let data = try? await item.loadTransferable(type: Data.self)
            await MainActor.run {
                libraryPick = nil
                guard let data, let image = UIImage(data: data) else { return }
                photo = PickedPhoto(image: image)
            }
        }
    }

    private func addRecognized(_ foods: [RecognizedFood]) {
        let moment = NutritionFormat.moment(on: day)
        store.add(foods.map {
            FoodEntry(date: moment, name: $0.name, grams: $0.grams, nutrients: $0.nutrients, fromPhoto: true)
        })
    }

    // MARK: - Записи дня

    private var entriesCard: some View {
        VStack(alignment: .leading, spacing: 4) {
            NutritionCaption(text: "Записи · \(dayEntries.count)")
                .padding(.bottom, 6)
            ForEach(Array(dayEntries.enumerated()), id: \.element.id) { index, entry in
                Button { editing = entry } label: { entryRow(entry) }
                    .buttonStyle(.pressable)
                    .contextMenu {
                        Button(role: .destructive) {
                            withAnimation(Motion.snappy) { store.remove(entry) }
                        } label: {
                            Label("Удалить", systemImage: "trash")
                        }
                    }
                if index < dayEntries.count - 1 {
                    Divider().overlay(Color.ink.opacity(0.06))
                }
            }
        }
        .nutritionCard()
    }

    private func entryRow(_ entry: FoodEntry) -> some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 3) {
                Text(entry.name)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(.ink)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
                Text(entryDetail(entry))
                    .font(.system(size: 12))
                    .foregroundStyle(Color.ink.opacity(0.45))
            }
            Spacer(minLength: 8)
            VStack(alignment: .trailing, spacing: 3) {
                Text("\(NutritionFormat.kcal(entry.nutrients.kcal)) ккал")
                    .font(.system(size: 15, weight: .bold, design: Theme.fontDesign))
                    .monospacedDigit()
                    .foregroundStyle(.ink)
                Text(NutritionFormat.macros(entry.nutrients))
                    .font(.system(size: 11))
                    .foregroundStyle(Color.ink.opacity(0.4))
            }
        }
        .padding(.vertical, 8)
        .contentShape(Rectangle())
    }

    private func entryDetail(_ entry: FoodEntry) -> String {
        var parts: [String] = []
        if let grams = entry.grams { parts.append("\(NutritionFormat.grams(grams)) г") }
        if entry.fromPhoto { parts.append("по фото") }
        parts.append(NutritionFormat.time(entry.date))
        return parts.joined(separator: " · ")
    }

    private var guideLink: some View {
        Button { showingGuide = true } label: {
            HStack(spacing: 12) {
                Image(systemName: "questionmark.circle.fill")
                    .font(.system(size: 20))
                    .foregroundStyle(Theme.accent)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Как работает метод")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(.ink)
                    Text("Почему не формулы и как найти свою норму")
                        .font(.system(size: 12))
                        .foregroundStyle(Color.ink.opacity(0.45))
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(Color.ink.opacity(0.3))
            }
            .nutritionCard(padding: 16)
        }
        .buttonStyle(.pressable)
    }
}
