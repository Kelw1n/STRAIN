import SwiftUI
import SwiftData
import UniformTypeIdentifiers

// MARK: - Инструкция

private struct GuideItem: Identifiable {
    let id: String
    let icon: String
    let title: String
    let text: String
}

struct InstructionsView: View {
    let programKind: TrainingProgramKind
    /// Открыта из настроек поверх них — сверху кнопка «Готово».
    var onClose: (() -> Void)?
    @State private var opened: String?

    private var items: [GuideItem] {
        var result: [GuideItem] = [
            GuideItem(
                id: "start",
                icon: "flag.checkered",
                title: "Как начать",
                text: programKind.usesFiveRepMax
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
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    if let onClose {
                        Button("Готово", action: onClose).font(.body.weight(.semibold))
                    }
                }
            }
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
    @State private var showGuide = false
    @State private var showNewCycle = false
    @State private var showReset = false
    @State private var showImporter = false
    @State private var shareItem: ShareItem?
    @State private var backupMessage: String?
    @AppStorage("rest_timer_sound_enabled") private var restTimerSoundEnabled = true
    @AppStorage(GlassPreference.storageKey) private var clearGlass = true

    var body: some View {
        NavigationStack {
            Form {
                Section {
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

                Section("Имя профиля") {
                    TextField("Твоё имя или псевдоним", text: $profile.name)
                        .autocorrectionDisabled()
                }

                // Инструкция была отдельной вкладкой и пропала вместе со старой панелью.
                Section {
                    Button {
                        showGuide = true
                    } label: {
                        Label("Как устроена программа", systemImage: "book.closed.fill")
                    }
                } footer: {
                    Text("С чего начать, сколько отдыхать, что такое RPE и особенности твоей программы.")
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

                Section {
                    Toggle("Вести вес подсобки", isOn: $profile.accessoryProgressionEnabled)
                } header: {
                    Text("Подсобка")
                } footer: {
                    Text("У подсобки в плане веса нет — только повторы. Приложение возьмёт вес из прошлой тренировки и подскажет, когда прибавить: сначала добираешь верх диапазона во всех подходах, потом растёт вес на шаг прибавки. Три тренировки подряд без роста — предложит сбросить вес и набрать заново. Выключишь — вернутся просто РПЕ и повторы.")
                }

                Section {
                    Picker("Шаг прибавки", selection: $profile.defaultWeightStep) {
                        ForEach(ProgramProfile.weightStepOptions, id: \.self) { step in
                            Text(ProgramProfile.weightStepTitle(step)).tag(step)
                        }
                    }
                } header: {
                    Text("Блины в зале")
                } footer: {
                    Text(ProgramProfile.weightStepHint(profile.defaultWeightStep) + " Для отдельного упражнения шаг можно поменять в его меню на тренировке или в подсказке под упражнением — например, 2,5 кг на блоке и 5 кг на жиме, если мелких блинов нет.")
                }

                Section {
                    Toggle("Напоминать о тренировке", isOn: $profile.reminderEnabled)
                    if profile.reminderEnabled {
                        DatePicker("Время", selection: reminderTime, displayedComponents: .hourAndMinute)
                    }
                } header: {
                    Text("Напоминания")
                } footer: {
                    Text(profile.reminderEnabled
                         ? "Придёт в дни расписания: \(profile.weekdays.map { RuDate.short(weekday: $0) }.joined(separator: ", ")). Меняешь дни ниже — напоминания переезжают за ними."
                         : "Уведомление в дни, которые стоят в расписании. Веса в нём не пишутся: они меняются каждую неделю, а уведомление система повторяет одно и то же.")
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
                                .font(.system(size: 15, weight: .bold, design: Theme.fontDesign))
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
                    Button {
                        exportCSV()
                    } label: {
                        Label("Выгрузить таблицу", systemImage: "tablecells")
                    }
                    if let backupMessage {
                        Text(backupMessage).font(.caption).foregroundStyle(Theme.success)
                    }
                } header: {
                    Text("Резервная копия")
                } footer: {
                    Text("Копия — файл со всеми профилями, максимумами, расписанием и историей; тот же формат читает версия для Android. Таблица — записанные подходы в CSV, открывается в Excel.")
                }

                Section {
                    Toggle("Звуковой сигнал отдыха", isOn: $restTimerSoundEnabled)
                } header: {
                    Text("Таймер отдыха")
                } footer: {
                    Text("Короткий звуковой отсчёт за 3, 2, 1 секунды до окончания отдыха и сигнал завершения.")
                }

                Section {
                    AppVersionRow()
                } header: {
                    Text("Приложение")
                }

                // Только там, где стекло настоящее: до iOS 26 оно нарисовано и не меняется.
                if GlassPreference.isSupported {
                    Section {
                        Toggle("Прозрачное стекло, как в iOS 26", isOn: $clearGlass)
                    } header: {
                        Text("Оформление")
                    } footer: {
                        Text("Панель вкладок, поле ввода в переписке и кнопки поверх фото — из прозрачного жидкого стекла: сквозь него видно, что под ним, а края преломляют свет. Выключишь — стекло станет матовым, как в iOS 27.")
                    }
                }

                if HealthKitService.shared.deviceSupportsHealth && !HealthKitService.shared.isAvailable {
                    Section {
                        Label("Недоступно в этой сборке", systemImage: "heart.slash")
                            .foregroundStyle(.secondary)
                    } header: {
                        Text("Apple Здоровье")
                    } footer: {
                        Text("Приложение установлено с бесплатным Apple ID, а «Здоровье» Apple открывает только приложениям с платным аккаунтом разработчика. Тренировки, рекорды и вес тела всё равно хранятся в STRAIN.")
                    }
                }

                if HealthKitService.shared.isAvailable {
                    Section {
                        Toggle("Синхронизация с HealthKit", isOn: Binding(
                            get: { HealthKitService.shared.isEnabled },
                            set: { enabled in
                                HealthKitService.shared.isEnabled = enabled
                                if enabled {
                                    Task {
                                        _ = await HealthKitService.shared.requestAuthorization()
                                    }
                                }
                            }
                        ))
                    } header: {
                        Text("Apple Здоровье")
                    } footer: {
                        Text("Автоматически сохраняет тренировки в приложении «Здоровье» и позволяет подтягивать вес тела.")
                    }
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
        .sheet(isPresented: $showGuide) {
            InstructionsView(programKind: profile.programKind, onClose: { showGuide = false })
        }
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

    private func exportCSV() {
        do {
            shareItem = ShareItem(url: try CSVExport.writeTemporaryFile(profiles))
        } catch {
            backupMessage = "Не удалось собрать таблицу"
        }
    }

    /// Часы и минуты хранятся числами: `Date` в модели тянул бы за собой дату,
    /// которая напоминанию не нужна и только путала бы при переносе копии.
    private var reminderTime: Binding<Date> {
        Binding(
            get: {
                Calendar.current.date(from: DateComponents(hour: profile.reminderHour, minute: profile.reminderMinute))
                    ?? Date()
            },
            set: { value in
                let parts = Calendar.current.dateComponents([.hour, .minute], from: value)
                profile.reminderHour = parts.hour ?? 18
                profile.reminderMinute = parts.minute ?? 0
            }
        )
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
            guard let first = archive.profiles.first else {
                backupMessage = "В файле нет профилей"
                return
            }
            let count = BackupService.restore(archive, into: modelContext, existing: profiles)
            NutritionStore.shared.reload()
            SocialService.shared.adoptBackupToken(archive.socialToken)
            // Сразу открываем восстановленный профиль: иначе после переустановки
            // на экране остаётся новый пустой, и кажется, что копия не применилась.
            backupMessage = "Восстановлено профилей: \(count). Открыт «\(first.name)»."
            if first.id != profile.profileID.uuidString {
                closeThen { activeProfileID = first.id }
            }
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
                .font(.system(size: 17, weight: .bold, design: Theme.fontDesign))
                .frame(width: 80)
            Text("кг").font(.caption).foregroundStyle(.secondary)
        }
        .padding(.vertical, 8)
        .padding(.horizontal, 12)
        .background(Color.primary.opacity(0.05), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}
