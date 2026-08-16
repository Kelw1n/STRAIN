import SwiftUI
import SwiftData
import UniformTypeIdentifiers

// MARK: - Прогресс

struct ProgressScreen: View {
    @Bindable var profile: ProgramProfile
    private var plan: WorkoutPlan { profile.workoutPlan }
    private var completed: Int { profile.completedDayCount }

    private var ratio: Double {
        guard profile.totalDays > 0 else { return 0 }
        return Double(completed) / Double(profile.totalDays)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 16) {
                    hero.appearIn(0)
                    streak.appearIn(0)
                    ProgressChartView(records: profile.completionLog).appearIn(1).softScroll()
                    TonnageChartView(weeks: profile.weeklyTonnage).appearIn(1).softScroll()
                    maxes.appearIn(1)
                    if profile.programKind.hasBenchWave { benchCard.appearIn(2) }
                    heatmap.appearIn(3).softScroll()
                    HistoryCard(records: profile.completionLog).appearIn(4).softScroll()
                }
                .padding(.horizontal)
                .padding(.bottom, 28)
            }
            .scrollIndicators(.hidden)
            .screenBackground()
            .navigationTitle("Прогресс")
        }
    }

    private var hero: some View {
        HighlightCard {
            HStack(spacing: 20) {
                RingProgress(value: ratio, lineWidth: 12) {
                    VStack(spacing: 0) {
                        Text("\(Int((ratio * 100).rounded()))")
                            .font(.system(size: 24, weight: .bold, design: .rounded))
                            .contentTransition(.numericText())
                        Text("%").font(.caption2).foregroundStyle(.secondary)
                    }
                }
                .frame(width: 96, height: 96)

                VStack(alignment: .leading, spacing: 6) {
                    Text("ЦИКЛ")
                        .font(.caption2.weight(.bold)).tracking(1.3)
                        .foregroundStyle(Theme.accentGradient)
                    HStack(alignment: .firstTextBaseline, spacing: 5) {
                        Text("\(completed)")
                            .font(.system(size: 40, weight: .bold, design: .rounded))
                            .contentTransition(.numericText())
                        Text("из \(profile.totalDays)").foregroundStyle(.secondary)
                    }
                    Text("тренировочных дней отмечено")
                        .font(.caption).foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
            }
        }
    }

    /// Серия закрытых недель. Показываем только когда есть что показать:
    /// нули на пустом профиле выглядят как упрёк.
    @ViewBuilder
    private var streak: some View {
        let streakValue = profile.weekStreak
        if streakValue.best > 0 {
            CardView {
                HStack(spacing: 18) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("СЕРИЯ")
                            .font(.caption2.weight(.bold)).tracking(1.3)
                            .foregroundStyle(Theme.accentGradient)
                        HStack(alignment: .firstTextBaseline, spacing: 5) {
                            Text("\(streakValue.current)")
                                .font(.system(size: 34, weight: .bold, design: .rounded))
                                .contentTransition(.numericText())
                            Text(weekWord(streakValue.current)).foregroundStyle(.secondary)
                        }
                        Text("подряд без пропусков")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer(minLength: 0)
                    VStack(alignment: .trailing, spacing: 4) {
                        Text("ЛУЧШАЯ").font(.caption2.weight(.bold)).tracking(1.3)
                            .foregroundStyle(.secondary)
                        Text("\(streakValue.best)")
                            .font(.system(size: 22, weight: .bold, design: .rounded))
                            .foregroundStyle(streakValue.current >= streakValue.best ? AnyShapeStyle(Theme.success) : AnyShapeStyle(.secondary))
                    }
                }
            }
        }
    }

    /// «1 неделя», «2 недели», «5 недель» — без этого счётчик читается коряво.
    private func weekWord(_ count: Int) -> String {
        let tens = count % 100
        if (11...14).contains(tens) { return "недель" }
        switch count % 10 {
        case 1: return "неделя"
        case 2, 3, 4: return "недели"
        default: return "недель"
        }
    }

    private var maxes: some View {
        CardView {
            Text("Текущие \(profile.maximumLabel)").font(.title3.weight(.bold))
            PRRow(title: "Приседания", value: profile.squat5RM, symbol: "figure.strengthtraining.functional")
            PRRow(title: "Жим лёжа", value: profile.bench5RM, symbol: "figure.strengthtraining.traditional")
            PRRow(title: "Становая тяга", value: profile.deadlift5RM, symbol: "figure.stand")
        }
    }

    private var benchCard: some View {
        let total = UpperLowerCalculator.benchSessionCount
        let done = profile.completedBenchCount
        return CardView {
            HStack {
                Label("Волна «Жим 14»", systemImage: "waveform.path.ecg")
                    .font(.headline)
                Spacer()
                Text("\(done)/\(total)")
                    .font(.system(size: 17, weight: .bold, design: .rounded))
                    .foregroundStyle(Theme.recordGradient)
                    .contentTransition(.numericText())
            }
            LineMeter(value: total > 0 ? Double(done) / Double(total) : 0, gradient: Theme.recordGradient)
            if let next = profile.nextBenchSession {
                Text("Следующая жимовая тренировка — №\(next)")
                    .font(.caption).foregroundStyle(.secondary)
            } else {
                Text("Волна пройдена. Пора тестировать новый 1ПМ.")
                    .font(.caption).foregroundStyle(Theme.success)
            }
        }
    }

    private var heatmap: some View {
        CardView {
            Text("Недели").font(.title3.weight(.bold))
            VStack(spacing: 6) {
                ForEach(Array(plan.weeks.enumerated()), id: \.element.id) { weekIndex, week in
                    let done = week.days.filter { profile.isCompleted(week: week.number, day: $0.number) }.count
                    HStack(spacing: 6) {
                        Text("\(week.number)")
                            .font(.system(size: 11, weight: .bold, design: .rounded).monospacedDigit())
                            .foregroundStyle(.secondary)
                            .frame(width: 18, alignment: .trailing)
                        ForEach(week.days) { day in
                            HeatCell(
                                isDone: profile.isCompleted(week: week.number, day: day.number),
                                delay: Double(weekIndex) * 0.03
                            )
                        }
                        Spacer(minLength: 6)
                        Text("\(done)/\(week.days.count)")
                            .font(.caption2.monospacedDigit())
                            .foregroundStyle(done == week.days.count ? Theme.success : .secondary)
                    }
                }
            }
        }
    }
}

struct HeatCell: View {
    let isDone: Bool
    let delay: Double
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var shown = false

    var body: some View {
        RoundedRectangle(cornerRadius: 6, style: .continuous)
            .fill(isDone ? AnyShapeStyle(Theme.successGradient) : AnyShapeStyle(Color.primary.opacity(0.08)))
            .frame(height: 20)
            .frame(maxWidth: 46)
            .scaleEffect(shown ? 1 : 0.6)
            .opacity(shown ? 1 : 0)
            .animation(Motion.maybe(Motion.bouncy, reduce: reduceMotion), value: isDone)
            .onAppear {
                guard !shown else { return }
                if reduceMotion { shown = true } else {
                    withAnimation(.spring(response: 0.45, dampingFraction: 0.75).delay(delay)) { shown = true }
                }
            }
    }
}

struct PRRow: View {
    let title: String
    let value: Double
    var symbol: String = "dumbbell.fill"

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: symbol)
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(Theme.accent)
                .frame(width: 32, height: 32)
                .background(Theme.accent.opacity(0.12), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            Text(title).font(.subheadline)
            Spacer()
            Text(WeightFormat.kilogramsPrecise(value))
                .font(.system(size: 17, weight: .bold, design: .rounded))
                .monospacedDigit()
                .contentTransition(.numericText())
        }
    }
}

// MARK: - Инструкция

private struct GuideItem: Identifiable {
    let id: String
    let icon: String
    let title: String
    let text: String
}

struct InstructionsView: View {
    let programKind: TrainingProgramKind
    @State private var opened: String?

    private var items: [GuideItem] {
        var result: [GuideItem] = [
            GuideItem(
                id: "start",
                icon: "flag.checkered",
                title: "Как начать",
                text: programKind == .texas
                    ? "Протестируй настоящий пятиповторный максимум в приседаниях, жиме лёжа и становой тяге. Введи именно проверенные значения — программа рассчитает рабочие веса и постепенное увеличение нагрузки."
                    : "Протестируй 1ПМ в приседаниях, жиме лёжа и становой тяге. Программа рассчитает базовые веса с округлением к 5 кг. Для остальных упражнений подбирай вес по указанному RPE."
            )
        ]
        if programKind == .texas {
            result.append(GuideItem(
                id: "extra",
                icon: "plus.circle",
                title: "Дополнительные упражнения",
                text: "Дополнительные упражнения необязательны, но выбранное упражнение следует выполнять на протяжении всего цикла. Растянутый суперсет рук означает чередование бицепса и трицепса с полноценным отдыхом после каждого подхода."
            ))
        }
        result.append(GuideItem(
            id: "rest",
            icon: "hourglass",
            title: "Отдых",
            text: "Отдыхай до полного восстановления. Минимум — 3 минуты; при необходимости отдых может занимать 10 минут и больше."
        ))
        if programKind == .texas {
            result.append(GuideItem(
                id: "peak",
                icon: "mountain.2.fill",
                title: "Пикирование",
                text: "Пикирование — необязательная часть после основной 12-недельной программы. Используй его только если хочешь проверить 1ПМ или подготовиться к соревнованиям. Перед запуском внеси свежие 5ПМ."
            ))
        }
        if programKind == .upperLower {
            result.append(GuideItem(
                id: "bench14",
                icon: "waveform.path.ecg",
                title: "Жим 14",
                text: "Волновая прогрессия из 14 жимовых тренировок вынесена в отдельный раздел: там весь цикл по подходам, проценты от 1ПМ и своя отметка выполнения. Негативные повторы выполняй только со страхующим и заранее оговорённой подачей штанги."
            ))
        }
        return result
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 14) {
                    ForEach(Array(items.enumerated()), id: \.element.id) { index, item in
                        GuideCard(item: item, isOpen: opened == item.id) {
                            withAnimation(Motion.card) { opened = opened == item.id ? nil : item.id }
                        }
                        .appearIn(index)
                        .softScroll()
                    }
                    rpeCard.appearIn(items.count).softScroll()
                }
                .padding(.horizontal)
                .padding(.bottom, 28)
            }
            .scrollIndicators(.hidden)
            .screenBackground()
            .navigationTitle("Инструкция")
        }
    }

    private var rpeCard: some View {
        CardView {
            Label("RPE", systemImage: "gauge.with.dots.needle.50percent")
                .font(.title3.weight(.bold))
            Text("RPE — субъективная оценка оставшихся повторов в запасе. Записывай ощущения и регулируй нагрузку, если восстановление отличается от обычного.")
                .font(.subheadline).foregroundStyle(.secondary)
            VStack(spacing: 8) {
                rpeRow("RPE 7", "3 повтора в запасе", 0.7)
                rpeRow("RPE 8", "2 повтора в запасе", 0.8)
                rpeRow("RPE 9", "1 повтор в запасе", 0.9)
                rpeRow("RPE 10", "предел", 1.0)
            }
        }
    }

    private func rpeRow(_ label: String, _ detail: String, _ value: Double) -> some View {
        HStack(spacing: 12) {
            Text(label)
                .font(.caption.weight(.bold))
                .frame(width: 58, alignment: .leading)
            LineMeter(value: value, gradient: value >= 0.95 ? Theme.recordGradient : Theme.accentGradient, height: 6)
            Text(detail)
                .font(.caption)
                .foregroundStyle(.secondary)
                .frame(width: 130, alignment: .trailing)
        }
    }
}

private struct GuideCard: View {
    let item: GuideItem
    let isOpen: Bool
    let onTap: () -> Void

    var body: some View {
        CardView(padding: 16) {
            Button(action: onTap) {
                HStack(spacing: 13) {
                    Image(systemName: item.icon)
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(.white)
                        .frame(width: 36, height: 36)
                        .background(Theme.accentGradient, in: RoundedRectangle(cornerRadius: 11, style: .continuous))
                    Text(item.title).font(.headline).foregroundStyle(.primary)
                    Spacer(minLength: 4)
                    Image(systemName: "chevron.down")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(.secondary)
                        .rotationEffect(.degrees(isOpen ? 180 : 0))
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.pressable)

            if isOpen {
                Text(item.text)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
                    .transition(.opacity.combined(with: .offset(y: -8)))
            }
        }
    }
}

// MARK: - Настройки

struct SettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @Query(sort: [SortDescriptor(\ProgramProfile.createdAt)]) private var profiles: [ProgramProfile]
    @AppStorage("activeProfileID") private var activeProfileID = ""
    @Bindable var profile: ProgramProfile
    let onAddProfile: () -> Void
    let onDeleteProfile: () -> Void
    @Environment(\.modelContext) private var modelContext
    @State private var showPeaking = false
    @State private var showNewCycle = false
    @State private var showReset = false
    @State private var showImporter = false
    @State private var shareItem: ShareItem?
    @State private var backupMessage: String?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Имя профиля", text: $profile.name)
                    ForEach(profiles) { item in
                        Button {
                            guard item.profileID != profile.profileID else { return }
                            closeThen { activeProfileID = item.profileID.uuidString }
                        } label: {
                            HStack(spacing: 12) {
                                Image(systemName: item.profileID == profile.profileID ? "person.fill" : "person")
                                    .font(.footnote.weight(.bold))
                                    .foregroundStyle(.white)
                                    .frame(width: 30, height: 30)
                                    .background(item.profileID == profile.profileID ? AnyShapeStyle(Theme.accentGradient) : AnyShapeStyle(Color.gray.opacity(0.4)),
                                                in: RoundedRectangle(cornerRadius: 9, style: .continuous))
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(item.name).foregroundStyle(.primary)
                                    Text(item.programKind.rawValue).font(.caption).foregroundStyle(.secondary)
                                }
                                Spacer()
                                if item.profileID == profile.profileID {
                                    Image(systemName: "checkmark").font(.footnote.weight(.bold)).foregroundStyle(Theme.accent)
                                }
                            }
                        }
                    }
                    Button {
                        closeThen(onAddProfile)
                    } label: {
                        Label("Добавить профиль", systemImage: "person.badge.plus")
                    }
                } header: {
                    Text("Профили")
                } footer: {
                    Text("У каждого профиля своя программа, максимумы, расписание и отметки.")
                }

                Section(profile.maximumLabel) {
                    TextField("Приседания", value: $profile.squat5RM, format: .number).keyboardType(.decimalPad)
                    TextField("Жим лёжа", value: $profile.bench5RM, format: .number).keyboardType(.decimalPad)
                    TextField("Становая тяга", value: $profile.deadlift5RM, format: .number).keyboardType(.decimalPad)
                }

                if profile.programKind == .fullBody {
                    Section {
                        Picker("Стаж", selection: $profile.fullBodyLevel) {
                            ForEach(FullBodyLevel.allCases) { Text($0.rawValue).tag($0) }
                        }
                    } header: {
                        Text("Сколько ходишь в зал")
                    } footer: {
                        Text(profile.fullBodyLevel.explanation)
                    }
                    Section {
                        // Слоты зависят от стажа: сменил стаж — список меняется,
                        // а выбранные названия остаются на своих местах.
                        ForEach(profile.fullBodyLevel.accessorySlots) { category in
                            ExercisePicker(category: category, value: binding(for: category))
                        }
                    } header: {
                        Text("Подсобка")
                    } footer: {
                        Text("Эти упражнения идут в каждой тренировке.")
                    }
                }

                if profile.programKind == .texas {
                    Section("Уровень") {
                        Picker("Уровень", selection: $profile.level) {
                            ForEach(TrainingLevel.allCases) { Text($0.rawValue).tag($0) }
                        }
                        .pickerStyle(.segmented)
                    }
                    Section("Дополнительные упражнения") {
                        ForEach(AdditionalExerciseCategory.allCases) { category in
                            ExercisePicker(category: category, value: binding(for: category))
                        }
                    }
                    Section("После 12 недель") {
                        Button {
                            showPeaking = true
                        } label: {
                            Label("Открыть модуль пикирования", systemImage: "mountain.2.fill")
                        }
                        Text(profile.completedDayCount >= 36
                             ? "Основная программа пройдена — можно запускать."
                             : "Рассчитано на тех, кто прошёл основную программу: отмечено \(profile.completedDayCount) из 36. Запустить можно и раньше, отметки основного цикла сохранятся отдельно и вернутся после отмены.")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                }

                Section {
                    Button {
                        showNewCycle = true
                    } label: {
                        Label("Начать новый цикл", systemImage: "arrow.triangle.2.circlepath")
                    }
                } header: {
                    Text("Цикл \(profile.cycleNumber)")
                } footer: {
                    Text("Программа начнётся заново с новыми максимумами. Отметки прошлого цикла спрячутся, история и графики останутся сквозными.")
                }

                if profile.supportsAutoregulation {
                    Section {
                        Toggle("Подстраивать веса", isOn: $profile.autoregulationEnabled)
                    } header: {
                        Text("Авторегуляция")
                    } footer: {
                        Text("После тяжёлого дня приложение спросит, как прошёл рабочий подход по каждому движению, и посчитает следующую неделю от ответа. Каждое движение идёт своим темпом. Пока не отвечаешь, прибавка обычная — 2,5 кг.")
                    }
                }

                if profile.supportsAutoregulation {
                    Section {
                        Toggle("Подстраивать веса", isOn: $profile.autoregulationEnabled)
                    } header: {
                        Text("Авторегуляция")
                    } footer: {
                        Text("После тяжёлого дня приложение спросит, как прошёл рабочий подход по каждому движению, и посчитает следующую неделю от ответа. Каждое движение идёт своим темпом. Пока не отвечаешь, прибавка обычная — 2,5 кг.")
                    }
                }

                Section {
                    Toggle("Не гасить экран", isOn: $profile.keepScreenOn)
                } header: {
                    Text("В зале")
                } footer: {
                    Text("Пока открыта тренировка, экран не уходит в сон. Телефон на лавке между подходами перестанет тухнуть.")
                }

                if !profile.activeSkips.isEmpty {
                    Section {
                        ForEach(profile.activeSkips) { item in
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(item.title)
                                    Text(item.holdsProgression ? "веса задержаны" : "без задержки")
                                        .font(.caption).foregroundStyle(.secondary)
                                }
                                Spacer()
                                Button(role: .destructive) {
                                    profile.removeSkip(item)
                                } label: {
                                    Image(systemName: "arrow.uturn.backward")
                                }
                                .buttonStyle(.borderless)
                            }
                        }
                    } header: {
                        Text("Пропущено")
                    } footer: {
                        Text("Пока пропуск с задержкой не закрыт, веса следующих недель стоят на месте. Отметишь тренировку — задержка снимется сама.")
                    }
                }

                Section {
                    Picker("Порядок", selection: $profile.scheduleMode) {
                        ForEach(ScheduleMode.allCases) { Text($0.rawValue).tag($0) }
                    }
                    .pickerStyle(.segmented)
                } header: {
                    Text("Порядок тренировок")
                } footer: {
                    Text(profile.scheduleMode.explanation)
                }

                Section {
                    ForEach(1...profile.trainingDayCount, id: \.self) { day in
                        Picker(dayLabel(day), selection: weekdayBinding(for: day)) {
                            ForEach(RuDate.weekOrder, id: \.self) { weekday in
                                Text(RuDate.full(weekday: weekday).capitalized).tag(weekday)
                            }
                        }
                    }
                } header: {
                    Text("Дни недели")
                } footer: {
                    Text(profile.scheduleMode == .queue
                         ? "Дни, в которые ты ходишь в зал. Очередь раздаёт тренировки именно по ним."
                         : "День недели задаёт тип тренировки. Поменяешь здесь — поменяется и то, что приложение предложит в этот день.")
                }

                if profile.programKind.hasBenchWave {
                    Section("Жим 14") {
                        HStack {
                            Label("Отмечено тренировок", systemImage: "waveform.path.ecg")
                            Spacer()
                            Text("\(profile.completedBenchCount) / \(UpperLowerCalculator.benchSessionCount)")
                                .font(.system(size: 15, weight: .bold, design: .rounded))
                                .foregroundStyle(Theme.recordGradient)
                        }
                        Button("Сбросить отметки жима", role: .destructive) {
                            withAnimation(Motion.card) { profile.completedBenchSessions = [] }
                        }
                        .disabled(profile.completedBenchCount == 0)
                    }
                }

                Section {
                    Button {
                        exportBackup()
                    } label: {
                        Label("Сохранить копию", systemImage: "square.and.arrow.up")
                    }
                    Button {
                        showImporter = true
                    } label: {
                        Label("Загрузить копию", systemImage: "square.and.arrow.down")
                    }
                    if let backupMessage {
                        Text(backupMessage).font(.caption).foregroundStyle(Theme.success)
                    }
                } header: {
                    Text("Резервная копия")
                } footer: {
                    Text("Файл со всеми профилями, максимумами, расписанием и историей. Тот же формат читает версия для Android.")
                }

                Section {
                    Button(profiles.count > 1 ? "Удалить этот профиль" : "Сбросить профиль", role: .destructive) { showReset = true }
                    Text(profiles.count > 1
                         ? "Удалит максимумы, программу и отметки этого профиля. Остальные профили останутся."
                         : "Удалит максимумы, выбранную программу и все отметки, затем вернёт на стартовый выбор.")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            .scrollContentBackground(.hidden)
            .screenBackground()
            .navigationTitle("Настройки")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Готово") { dismiss() }.font(.body.weight(.semibold))
                }
            }
        }
        .sheet(isPresented: $showPeaking) { PeakingView(profile: profile) }
        .sheet(isPresented: $showNewCycle) { NewCycleView(profile: profile) }
        .sheet(item: $shareItem) { item in
            ShareSheet(url: item.url).presentationDetents([.medium])
        }
        .fileImporter(isPresented: $showImporter, allowedContentTypes: [.json]) { result in
            importBackup(result)
        }
        .confirmationDialog(profiles.count > 1 ? "Удалить профиль «\(profile.name)»?" : "Полностью сбросить профиль?",
                            isPresented: $showReset, titleVisibility: .visible) {
            Button(profiles.count > 1 ? "Удалить профиль" : "Сбросить", role: .destructive) { closeThen(onDeleteProfile) }
            Button("Отмена", role: .cancel) {}
        }
    }

    // MARK: - Резервная копия

    private func exportBackup() {
        do {
            shareItem = ShareItem(url: try BackupService.writeTemporaryFile(profiles))
        } catch {
            backupMessage = "Не удалось собрать копию"
        }
    }

    private func importBackup(_ result: Result<URL, Error>) {
        guard case .success(let url) = result else {
            backupMessage = "Файл не выбран"
            return
        }
        // Файл лежит вне песочницы приложения, доступ надо запросить явно.
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }

        do {
            let archive = try BackupService.archive(from: try Data(contentsOf: url))
            let count = BackupService.restore(archive, into: modelContext, existing: profiles)
            backupMessage = "Восстановлено профилей: \(count)"
        } catch {
            backupMessage = "Файл не читается как копия STRAIN"
        }
    }

    /// Сначала закрыть лист, потом менять профиль: иначе экран под ним пересобирается
    /// прямо во время анимации закрытия.
    private func closeThen(_ action: @escaping () -> Void) {
        dismiss()
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35, execute: action)
    }

    /// Название тренировочного дня в расписании: «День 1 · Верх тяжёлый».
    private func dayLabel(_ day: Int) -> String {
        let title = profile.workoutPlan.weeks.first?.days.first { $0.number == day }?.title
        guard let title, profile.programKind.hasBenchWave else { return "День \(day)" }
        return "День \(day) · " + title.prefix(1) + title.dropFirst().lowercased()
    }

    private func weekdayBinding(for day: Int) -> Binding<Int> {
        Binding(
            get: { profile.weekday(forDay: day) },
            set: { profile.setWeekday($0, forDay: day) }
        )
    }

    private func binding(for category: AdditionalExerciseCategory) -> Binding<String?> {
        Binding(get: { get(category) }, set: { set(category, $0) })
    }

    private func get(_ category: AdditionalExerciseCategory) -> String? {
        switch category {
        case .pull: return profile.pull
        case .arms: return profile.arms
        case .core: return profile.core
        case .back: return profile.back
        case .press: return profile.press
        }
    }

    private func set(_ category: AdditionalExerciseCategory, _ value: String?) {
        switch category {
        case .pull: profile.pull = value
        case .arms: profile.arms = value
        case .core: profile.core = value
        case .back: profile.back = value
        case .press: profile.press = value
        }
    }
}

// MARK: - Пикирование

struct PeakingView: View {
    @Environment(\.dismiss) private var dismiss
    @Bindable var profile: ProgramProfile

    private var plan: WorkoutPlan { ProgramCalculator.generatePeaking(input: profile.peakingInput) }

    private func peakBinding(_ keyPath: ReferenceWritableKeyPath<ProgramProfile, Double?>, fallback: Double) -> Binding<Double> {
        Binding(
            get: { profile[keyPath: keyPath] ?? fallback },
            set: { profile[keyPath: keyPath] = $0 }
        )
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 16) {
                    ScreenHeader(
                        eyebrow: "после 12 недель",
                        title: "Пикирование",
                        subtitle: "Подготовка к тесту 1ПМ. Подтверди свежие 5ПМ: они сохранятся отдельно и не изменят основную программу."
                    )
                    .padding(.top, 8)
                    .appearIn(0)

                    CardView {
                        Text("Свежие 5ПМ").font(.headline)
                        peakField("Приседания", peakBinding(\.peakSquat5RM, fallback: profile.squat5RM))
                        peakField("Жим лёжа", peakBinding(\.peakBench5RM, fallback: profile.bench5RM))
                        peakField("Становая тяга", peakBinding(\.peakDeadlift5RM, fallback: profile.deadlift5RM))
                        Button {
                            withAnimation(Motion.card) {
                                profile.peakSquat5RM = profile.peakSquat5RM ?? profile.squat5RM
                                profile.peakBench5RM = profile.peakBench5RM ?? profile.bench5RM
                                profile.peakDeadlift5RM = profile.peakDeadlift5RM ?? profile.deadlift5RM
                                profile.peakingActive = true
                            }
                        } label: {
                            Label(profile.peakingActive ? "Пикирование активно" : "Запустить пикирование",
                                  systemImage: profile.peakingActive ? "checkmark.circle.fill" : "mountain.2.fill")
                        }
                        .buttonStyle(GradientButtonStyle(
                            gradient: profile.peakingActive ? Theme.successGradient : Theme.accentGradient,
                            glow: profile.peakingActive ? Theme.success : Theme.accent
                        ))
                        .disabled(profile.peakingActive)
                        Text(profile.peakingActive
                             ? "«Сегодня» и «План» показывают пиковый цикл. Отметки основной программы сохранены отдельно и вернутся после отмены."
                             : "После запуска «Сегодня» и «План» переключатся на пиковый цикл.")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    .appearIn(1)

                    ForEach(Array(plan.weeks.enumerated()), id: \.element.id) { index, week in
                        CardView {
                            Text("Неделя \(week.number)").font(.headline)
                            ForEach(week.days) { day in
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(day.title)
                                        .font(.caption.weight(.bold))
                                        .foregroundStyle(Theme.accentGradient)
                                    Text(day.exercises.map { "\($0.name) · \($0.load.displayText)" }.joined(separator: "\n"))
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                            }
                        }
                        .appearIn(index + 2)
                        .softScroll()
                    }

                    if profile.peakingActive {
                        Button("Отменить пикирование", role: .destructive) {
                            withAnimation(Motion.card) {
                                profile.peakingActive = false
                                profile.peakSquat5RM = nil
                                profile.peakBench5RM = nil
                                profile.peakDeadlift5RM = nil
                                // Отметки пикового цикла уходят вместе с ним,
                                // основная программа остаётся нетронутой.
                                profile.completedDayKeys.removeAll { profile.isOwnKey($0) }
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.top, 4)
                    }
                }
                .padding(.horizontal)
                .padding(.bottom, 28)
            }
            .scrollIndicators(.hidden)
            .screenBackground()
            .navigationTitle("Пикирование")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Готово") { dismiss() }.font(.body.weight(.semibold))
                }
            }
        }
    }

    private func peakField(_ title: String, _ value: Binding<Double>) -> some View {
        HStack {
            Text(title).font(.subheadline)
            Spacer()
            TextField("кг", value: value, format: .number)
                .keyboardType(.decimalPad)
                .multilineTextAlignment(.trailing)
                .font(.system(size: 17, weight: .bold, design: .rounded))
                .frame(width: 80)
            Text("кг").font(.caption).foregroundStyle(.secondary)
        }
        .padding(.vertical, 8)
        .padding(.horizontal, 12)
        .background(Color.primary.opacity(0.05), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}
