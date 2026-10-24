import SwiftUI

struct GoalSettingsSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Bindable var profile: ProgramProfile

    @State private var selectedDays: Set<Int> = []
    @State private var reminderTime = Calendar.current.date(bySettingHour: 19, minute: 0, second: 0, of: Date()) ?? Date()
    @State private var reminderEnabled = true

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
                                        .foregroundStyle(isSelected ? .white : Color.white.opacity(0.65))
                                        .frame(maxWidth: .infinity)
                                        .frame(height: 48)
                                        .background(
                                            Circle()
                                                .fill(isSelected ? Theme.accent : Color(white: 0.16))
                                        )
                                }
                                .buttonStyle(.pressable)
                            }
                        }

                        Text(isReady
                             ? "\(need) \(RuPlural.word(need, "тренировка", "тренировки", "тренировок")) в неделю — столько же дней в программе."
                             : "Выбери \(need) \(RuPlural.word(need, "день", "дня", "дней")) — столько тренировок в неделе программы. Сейчас \(selectedDays.count).")
                            .font(.system(size: 12))
                            .foregroundStyle(isReady ? Color.white.opacity(0.45) : Theme.warning)
                            .fixedSize(horizontal: false, vertical: true)
                            .animation(.easeOut(duration: 0.15), value: isReady)
                    }

                    // Напоминание
                    VStack(alignment: .leading, spacing: 10) {
                        sectionTitle("НАПОМИНАНИЕ")

                        VStack(spacing: 0) {
                            Toggle("Напоминать в дни тренировок", isOn: $reminderEnabled.animation(Motion.snappy))
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundStyle(.white)
                                .tint(Theme.accent)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 14)

                            if reminderEnabled {
                                Divider().overlay(Color.white.opacity(0.06)).padding(.horizontal, 16)

                                HStack {
                                    Text("Время")
                                        .font(.system(size: 16, weight: .semibold))
                                        .foregroundStyle(.white)
                                    Spacer()
                                    DatePicker("", selection: $reminderTime, displayedComponents: .hourAndMinute)
                                        .labelsHidden()
                                        .colorScheme(.dark)
                                }
                                .padding(.horizontal, 16)
                                .padding(.vertical, 12)
                            }
                        }
                        .background(Color(white: 0.12), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                    }

                    Spacer(minLength: 20)

                    // Кнопка «СОХРАНИТЬ ЦЕЛЬ»
                    Button {
                        saveGoal()
                    } label: {
                        Text("СОХРАНИТЬ ЦЕЛЬ")
                            .font(.system(size: 16, weight: .bold, design: Theme.fontDesign))
                            .tracking(1.0)
                            .foregroundStyle(isReady ? .black : Color.white.opacity(0.4))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 18)
                            .background(isReady ? Color.white : Color(white: 0.16), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                    }
                    .buttonStyle(.pressable)
                    .disabled(!isReady)
                    .padding(.bottom, 20)
                }
                .padding(.horizontal, 18)
                .padding(.top, 14)
            }
            .background(Color.black.ignoresSafeArea())
            .navigationTitle("Цель")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(.white)
                            .padding(8)
                            .background(Color.white.opacity(0.12), in: Circle())
                    }
                    .accessibilityLabel("Назад")
                }
            }
        }
        .preferredColorScheme(.dark)
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
        }
    }

    private func sectionTitle(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 12, weight: .bold))
            .tracking(1.2)
            .foregroundStyle(Color.white.opacity(0.45))
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

        ReminderScheduler.reschedule(for: profile)
        WidgetBridge.publish(profile)
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        dismiss()
    }
}
