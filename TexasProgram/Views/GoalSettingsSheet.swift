import SwiftUI

struct GoalSettingsSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Bindable var profile: ProgramProfile

    @State private var selectedDays: Set<Int> = []
    @State private var reminderTime = Calendar.current.date(bySettingHour: 19, minute: 0, second: 0, of: Date()) ?? Date()
    @State private var reminderEnabled = true
    @State private var goalStart = Calendar.current.startOfDay(for: Date())
    @State private var goalMonths = 0
    @State private var ownTimes = false
    @State private var dayTimes: [Int: Date] = [:]
    @State private var offsets: Set<Int> = [0]

    // Дни недели с понедельника: ПН(2) … СБ(7), ВС(1).
    private let weekdaysList: [(label: String, index: Int)] = [
        ("ПН", 2), ("ВТ", 3), ("СР", 4), ("ЧТ", 5), ("ПТ", 6), ("СБ", 7), ("ВС", 1)
    ]

    /// Дней должно быть ровно столько, сколько тренировок в неделе программы:
    /// иначе расписание не сойдётся с планом, и модель молча вернёт дни по умолчанию.
    private var need: Int { profile.trainingDayCount }
    private var isReady: Bool { selectedDays.count == need }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    // Дни тренировок
                    VStack(alignment: .leading, spacing: 12) {
                        sectionTitle("ДНИ ТРЕНИРОВОК")

                        HStack(spacing: 8) {
                            ForEach(weekdaysList, id: \.index) { item in
                                let isSelected = selectedDays.contains(item.index)
                                Button {
                                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                    if isSelected {
                                        selectedDays.remove(item.index)
                                    } else {
                                        selectedDays.insert(item.index)
                                    }
                                } label: {
                                    Text(item.label)
                                        .font(.system(size: 15, weight: .bold, design: Theme.fontDesign))
                                        .foregroundStyle(isSelected ? .ink : Color.ink.opacity(0.65))
                                        .frame(maxWidth: .infinity)
                                        .frame(height: 48)
                                        .background(
                                            Circle()
                                                .fill(isSelected ? Theme.accent : Color.tone(0.16))
                                        )
                                }
                                .buttonStyle(.pressable)
                            }
                        }

                        Text(isReady
                             ? "\(need) \(RuPlural.word(need, "тренировка", "тренировки", "тренировок")) в неделю — столько же дней в программе."
                             : "Выбери \(need) \(RuPlural.word(need, "день", "дня", "дней")) — столько тренировок в неделе программы. Сейчас \(selectedDays.count).")
                            .font(.system(size: 12))
                            .foregroundStyle(isReady ? Color.ink.opacity(0.45) : Theme.warning)
                            .fixedSize(horizontal: false, vertical: true)
                            .animation(.easeOut(duration: 0.15), value: isReady)
                    }

                    // Срок
                    VStack(alignment: .leading, spacing: 10) {
                        sectionTitle("СРОК")
                        VStack(spacing: 0) {
                            row("Начало") {
                                DatePicker("", selection: $goalStart, displayedComponents: .date)
                                    .labelsHidden()
                                    .environment(\.locale, Locale(identifier: "ru_RU"))
                            }
                            Divider().overlay(Color.ink.opacity(0.06)).padding(.horizontal, 16)
                            row("Длительность") {
                                Menu {
                                    Picker("", selection: $goalMonths) {
                                        ForEach(TrainingGoal.monthOptions, id: \.self) { Text(TrainingGoal.monthsTitle($0)).tag($0) }
                                    }
                                } label: {
                                    HStack(spacing: 4) {
                                        Text(TrainingGoal.monthsTitle(goalMonths))
                                        Image(systemName: "chevron.up.chevron.down").font(.system(size: 11, weight: .semibold))
                                    }
                                    .font(.system(size: 15, weight: .semibold))
                                    .foregroundStyle(Color.ink.opacity(0.85))
                                }
                            }
                        }
                        .background(Color.tone(0.12), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                        if let term = termLine {
                            Text(term)
                                .font(.system(size: 12))
                                .foregroundStyle(Color.ink.opacity(0.45))
                        }
                    }

                    // Время тренировок
                    VStack(alignment: .leading, spacing: 10) {
                        sectionTitle("ВРЕМЯ ТРЕНИРОВОК")
                        VStack(spacing: 0) {
                            row("Время") {
                                Menu {
                                    Picker("", selection: $ownTimes.animation(Motion.snappy)) {
                                        Text("Одинаковое").tag(false)
                                        Text("Своё у каждого дня").tag(true)
                                    }
                                } label: {
                                    HStack(spacing: 4) {
                                        Text(ownTimes ? "Своё у каждого дня" : "Одинаковое")
                                        Image(systemName: "chevron.up.chevron.down").font(.system(size: 11, weight: .semibold))
                                    }
                                    .font(.system(size: 15, weight: .semibold))
                                    .foregroundStyle(Color.ink.opacity(0.85))
                                }
                            }
                            if ownTimes {
                                ForEach(weekdaysList.filter { selectedDays.contains($0.index) }, id: \.index) { item in
                                    Divider().overlay(Color.ink.opacity(0.06)).padding(.horizontal, 16)
                                    row(RuDate.full(weekday: item.index).capitalized) {
                                        DatePicker("", selection: dayTimeBinding(item.index), displayedComponents: .hourAndMinute)
                                            .labelsHidden()
                                    }
                                }
                            } else {
                                Divider().overlay(Color.ink.opacity(0.06)).padding(.horizontal, 16)
                                row("Все дни") {
                                    DatePicker("", selection: $reminderTime, displayedComponents: .hourAndMinute)
                                        .labelsHidden()
                                }
                            }
                        }
                        .background(Color.tone(0.12), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                    }

                    // Напоминания
                    VStack(alignment: .leading, spacing: 10) {
                        sectionTitle("НАПОМИНАНИЯ")
                        VStack(spacing: 0) {
                            Toggle("Напоминать", isOn: $reminderEnabled.animation(Motion.snappy))
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundStyle(.ink)
                                .tint(Theme.accent)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 14)
                            if reminderEnabled {
                                ForEach(TrainingGoal.offsetOptions, id: \.self) { offset in
                                    Divider().overlay(Color.ink.opacity(0.06)).padding(.horizontal, 16)
                                    let selected = offsets.contains(offset)
                                    Button {
                                        UISelectionFeedbackGenerator().selectionChanged()
                                        if selected {
                                            if offsets.count > 1 { offsets.remove(offset) }
                                        } else if offsets.count < TrainingGoal.maxOffsets {
                                            offsets.insert(offset)
                                        }
                                    } label: {
                                        HStack {
                                            Text(TrainingGoal.offsetTitle(offset))
                                                .font(.system(size: 16, weight: .semibold))
                                                .foregroundStyle(.ink)
                                            Spacer()
                                            Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                                                .font(.system(size: 20))
                                                .foregroundStyle(selected ? Theme.accent : Color.ink.opacity(0.25))
                                        }
                                        .padding(.horizontal, 16)
                                        .padding(.vertical, 12)
                                        .contentShape(Rectangle())
                                    }
                                    .buttonStyle(.plain)
                                    .disabled(!selected && offsets.count >= TrainingGoal.maxOffsets)
                                }
                            }
                        }
                        .background(Color.tone(0.12), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                        if reminderEnabled {
                            Text("\(offsets.count) " + RuPlural.word(offsets.count, "напоминание", "напоминания", "напоминаний") + " на тренировку, до трёх.")
                                .font(.system(size: 12))
                                .foregroundStyle(Color.ink.opacity(0.45))
                        }
                    }

                    Spacer(minLength: 20)

                    // Кнопка «СОХРАНИТЬ ЦЕЛЬ»
                    Button {
                        saveGoal()
                    } label: {
                        Text("СОХРАНИТЬ ЦЕЛЬ")
                            .font(.system(size: 16, weight: .bold, design: Theme.fontDesign))
                            .tracking(1.0)
                            .foregroundStyle(isReady ? .canvas : Color.ink.opacity(0.4))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 18)
                            .background(isReady ? Color.ink : Color.tone(0.16), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                    }
                    .buttonStyle(.pressable)
                    .disabled(!isReady)
                    .padding(.bottom, 20)
                }
                .padding(.horizontal, 18)
                .padding(.top, 14)
            }
            .background(Color.canvas.ignoresSafeArea())
            .navigationTitle("Цель")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(.ink)
                            .padding(8)
                            .background(Color.ink.opacity(0.12), in: Circle())
                    }
                    .accessibilityLabel("Назад")
                }
            }
        }
        .onAppear {
            // Те же дни, что видит расписание: свои, если их ровно столько, сколько
            // тренировок, иначе — по умолчанию для программы.
            selectedDays = Set(profile.weekdays)
            reminderEnabled = profile.reminderEnabled

            var comps = Calendar.current.dateComponents([.year, .month, .day], from: Date())
            comps.hour = profile.reminderHour
            comps.minute = profile.reminderMinute
            if let date = Calendar.current.date(from: comps) {
                reminderTime = date
            }
            goalStart = Calendar.current.startOfDay(for: profile.goalStart ?? Date())
            goalMonths = profile.goalMonths
            ownTimes = !profile.trainingTimes.isEmpty
            for item in weekdaysList {
                let minutes = profile.trainingMinutes(weekday: item.index)
                dayTimes[item.index] = Calendar.current.date(bySettingHour: minutes / 60, minute: minutes % 60, second: 0, of: Date())
            }
            offsets = Set(profile.activeReminderOffsets)
        }
    }

    private func row<Trailing: View>(_ title: String, @ViewBuilder trailing: () -> Trailing) -> some View {
        HStack {
            Text(title)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(.ink)
            Spacer()
            trailing()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .frame(minHeight: 50)
    }

    private func dayTimeBinding(_ weekday: Int) -> Binding<Date> {
        Binding(
            get: { dayTimes[weekday] ?? reminderTime },
            set: { dayTimes[weekday] = $0 }
        )
    }

    /// «Идёт неделя 3 из 13 · до 11 января».
    private var termLine: String? {
        guard goalMonths > 0, let end = Calendar.current.date(byAdding: .month, value: goalMonths, to: goalStart) else {
            return "Без срока — цель держится, пока не поменяешь."
        }
        let calendar = Calendar.current
        let days = calendar.dateComponents([.day], from: goalStart, to: end).day ?? 0
        let total = max(1, Int(ceil(Double(days) / 7)))
        let endText = "до \(calendar.component(.day, from: end)) \(RuDate.monthGenitive[calendar.component(.month, from: end)])"
        let today = calendar.startOfDay(for: Date())
        guard today >= goalStart else { return "\(total) " + RuPlural.word(total, "неделя", "недели", "недель") + " · " + endText }
        let week = min(total, (calendar.dateComponents([.day], from: goalStart, to: today).day ?? 0) / 7 + 1)
        return "Идёт неделя \(week) из \(total) · " + endText
    }

    private func sectionTitle(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 12, weight: .bold))
            .tracking(1.2)
            .foregroundStyle(Color.ink.opacity(0.45))
    }

    private func saveGoal() {
        guard isReady else { return }
        // Порядок с понедельника, а не по номеру дня: у воскресенья номер 1,
        // и простая сортировка сделала бы его первой тренировкой недели.
        profile.scheduleWeekdays = weekdaysList.map(\.index).filter { selectedDays.contains($0) }
        profile.reminderEnabled = reminderEnabled

        let comps = Calendar.current.dateComponents([.hour, .minute], from: reminderTime)
        profile.reminderHour = comps.hour ?? 19
        profile.reminderMinute = comps.minute ?? 0
        // Своё время — только у выбранных дней; одинаковое — словарь пустой.
        var times: [String: Int] = [:]
        if ownTimes {
            for day in selectedDays {
                let parts = Calendar.current.dateComponents([.hour, .minute], from: dayTimes[day] ?? reminderTime)
                times[String(day)] = (parts.hour ?? 19) * 60 + (parts.minute ?? 0)
            }
        }
        profile.trainingTimes = times
        profile.reminderOffsets = offsets.sorted()
        profile.goalStart = goalStart
        profile.goalMonths = goalMonths

        ReminderScheduler.reschedule(for: profile)
        WidgetBridge.publish(profile)
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        dismiss()
    }
}
