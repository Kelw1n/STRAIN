import SwiftUI

struct GoalSettingsSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Bindable var profile: ProgramProfile

    @State private var selectedDays: Set<Int> = []
    @State private var durationMonths = 3
    @State private var isPerDayTime = false
    @State private var uniformTime = Calendar.current.date(bySettingHour: 19, minute: 0, second: 0, of: Date()) ?? Date()
    @State private var reminderCount = 2
    @State private var reminderEnabled = true

    // Дни недели: ПН(2), ВТ(3), СР(4), ЧТ(5), ПТ(6), СБ(7), ВС(1)
    private let weekdaysList: [(label: String, index: Int)] = [
        ("ПН", 2), ("ВТ", 3), ("СР", 4), ("ЧТ", 5), ("ПТ", 6), ("СБ", 7), ("ВС", 1)
    ]

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    // Дни тренировок
                    VStack(alignment: .leading, spacing: 12) {
                        Text("ДНИ ТРЕНИРОВОК")
                            .font(.system(size: 12, weight: .bold))
                            .tracking(1.2)
                            .foregroundStyle(Color.white.opacity(0.45))

                        HStack(spacing: 8) {
                            ForEach(weekdaysList, id: \.index) { item in
                                let isSelected = selectedDays.contains(item.index)
                                Button {
                                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                    if isSelected {
                                        if selectedDays.count > 1 { selectedDays.remove(item.index) }
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
                                .buttonStyle(.plain)
                            }
                        }
                    }

                    // Срок
                    VStack(alignment: .leading, spacing: 10) {
                        Text("СРОК")
                            .font(.system(size: 12, weight: .bold))
                            .tracking(1.2)
                            .foregroundStyle(Color.white.opacity(0.45))

                        VStack(spacing: 0) {
                            HStack {
                                Text("Начало")
                                    .font(.system(size: 16, weight: .semibold))
                                    .foregroundStyle(.white)
                                Spacer()
                                Text("Сегодня")
                                    .font(.system(size: 15, weight: .medium))
                                    .foregroundStyle(Color.white.opacity(0.85))
                                Image(systemName: "chevron.right")
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundStyle(Color.white.opacity(0.35))
                            }
                            .padding(.horizontal, 16)
                            .padding(.vertical, 14)

                            Divider().overlay(Color.white.opacity(0.06)).padding(.horizontal, 16)

                            HStack {
                                Text("Длительность")
                                    .font(.system(size: 16, weight: .semibold))
                                    .foregroundStyle(.white)
                                Spacer()
                                Menu {
                                    Button("1 месяц") { durationMonths = 1 }
                                    Button("3 месяца") { durationMonths = 3 }
                                    Button("6 месяцев") { durationMonths = 6 }
                                    Button("1 год") { durationMonths = 12 }
                                } label: {
                                    HStack(spacing: 4) {
                                        Text("\(durationMonths) месяца")
                                            .font(.system(size: 15, weight: .medium))
                                            .foregroundStyle(Color.white.opacity(0.85))
                                        Image(systemName: "chevron.up.chevron.down")
                                            .font(.system(size: 12, weight: .semibold))
                                            .foregroundStyle(Color.white.opacity(0.35))
                                    }
                                }
                            }
                            .padding(.horizontal, 16)
                            .padding(.vertical, 14)
                        }
                        .background(Color(white: 0.12), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                    }

                    // Время тренировок
                    VStack(alignment: .leading, spacing: 10) {
                        Text("ВРЕМЯ ТРЕНИРОВОК")
                            .font(.system(size: 12, weight: .bold))
                            .tracking(1.2)
                            .foregroundStyle(Color.white.opacity(0.45))

                        VStack(spacing: 0) {
                            HStack {
                                Text("Время")
                                    .font(.system(size: 16, weight: .semibold))
                                    .foregroundStyle(.white)
                                Spacer()
                                DatePicker("", selection: $uniformTime, displayedComponents: .hourAndMinute)
                                    .labelsHidden()
                                    .colorScheme(.dark)
                            }
                            .padding(.horizontal, 16)
                            .padding(.vertical, 12)
                        }
                        .background(Color(white: 0.12), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                    }

                    // Напоминания
                    VStack(alignment: .leading, spacing: 10) {
                        Text("НАПОМИНАНИЯ")
                            .font(.system(size: 12, weight: .bold))
                            .tracking(1.2)
                            .foregroundStyle(Color.white.opacity(0.45))

                        VStack(spacing: 0) {
                            Toggle("Включить напоминания", isOn: $reminderEnabled)
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundStyle(.white)
                                .tint(Theme.accent)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 14)

                            if reminderEnabled {
                                Divider().overlay(Color.white.opacity(0.06)).padding(.horizontal, 16)

                                HStack {
                                    Text("Напомнить")
                                        .font(.system(size: 16, weight: .semibold))
                                        .foregroundStyle(.white)
                                    Spacer()
                                    Text("За 1 час и вовремя")
                                        .font(.system(size: 15, weight: .medium))
                                        .foregroundStyle(Color.white.opacity(0.85))
                                }
                                .padding(.horizontal, 16)
                                .padding(.vertical, 14)
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
                            .foregroundStyle(.black)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 18)
                            .background(Color.white, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                    }
                    .buttonStyle(.plain)
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
                }
            }
        }
        .preferredColorScheme(.dark)
        .onAppear {
            let initialDays = profile.scheduleWeekdays.isEmpty ? [2, 4, 6] : profile.scheduleWeekdays
            selectedDays = Set(initialDays)
            reminderEnabled = profile.reminderEnabled

            var comps = Calendar.current.dateComponents([.year, .month, .day], from: Date())
            comps.hour = profile.reminderHour
            comps.minute = profile.reminderMinute
            if let date = Calendar.current.date(from: comps) {
                uniformTime = date
            }
        }
    }

    private func saveGoal() {
        profile.scheduleWeekdays = Array(selectedDays).sorted()
        profile.reminderEnabled = reminderEnabled

        let comps = Calendar.current.dateComponents([.hour, .minute], from: uniformTime)
        profile.reminderHour = comps.hour ?? 19
        profile.reminderMinute = comps.minute ?? 0

        ReminderScheduler.reschedule(for: profile)
        WidgetBridge.publish(profile)
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        dismiss()
    }
}
