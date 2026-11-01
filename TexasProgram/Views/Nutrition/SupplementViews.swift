import SwiftUI
import UIKit

// MARK: - Карточка «Добавки»

/// Добавки на выбранный день: нажал — выпил, ещё раз — снял отметку. Сверху
/// серия дней, когда выпито всё, снизу последние семь дней.
struct SupplementsCard: View {
    let day: Date
    /// Дни с тренировкой (полночь): «с тренировкой» ждём только в них.
    let trainedDays: Set<Date>
    var store: NutritionStore = .shared

    @State private var editing: Supplement?
    @State private var adding = false

    private var calendar: Calendar { .current }

    var body: some View {
        let data = store.supplements
        VStack(alignment: .leading, spacing: 12) {
            header(data)
            if data.items.isEmpty {
                empty
            } else {
                VStack(spacing: 0) {
                    let items = SupplementTracker.ordered(data.items)
                    ForEach(Array(items.enumerated()), id: \.element.id) { index, item in
                        row(item, data: data)
                        if index < items.count - 1 {
                            Divider().overlay(Color.ink.opacity(0.06))
                        }
                    }
                }
                week(data)
            }
        }
        .nutritionCard()
        .animation(Motion.card, value: data)
        .sheet(item: $editing) { item in
            SupplementSheet(original: item, onSave: { store.saveSupplement($0) }, onDelete: { store.deleteSupplement(item) })
        }
        .sheet(isPresented: $adding) {
            SupplementSheet(original: nil, onSave: { store.saveSupplement($0) })
        }
    }

    private func header(_ data: SupplementData) -> some View {
        let state = SupplementTracker.state(on: day, data: data, trainedDays: trainedDays, calendar: calendar)
        let streak = SupplementTracker.streak(data: data, trainedDays: trainedDays, calendar: calendar)
        return HStack(spacing: 8) {
            VStack(alignment: .leading, spacing: 4) {
                NutritionCaption(text: "Добавки")
                if state.required > 0 {
                    Text(state.isComplete ? "Всё выпито" : "Выпито \(state.taken) из \(state.required)")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(state.isComplete ? Theme.success : .ink)
                        .contentTransition(.numericText())
                }
            }
            Spacer()
            if streak > 0 {
                HStack(spacing: 4) {
                    Image(systemName: "flame.fill")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(Theme.accent)
                    Text("\(streak) " + RuPlural.word(streak, "день", "дня", "дней"))
                        .font(.system(size: 13, weight: .bold, design: Theme.fontDesign))
                        .monospacedDigit()
                        .foregroundStyle(.ink)
                }
                .padding(.horizontal, 10)
                .frame(height: 30)
                .background(Color.tone(0.13), in: Capsule())
                .accessibilityElement(children: .combine)
                .accessibilityLabel("Серия \(streak) " + RuPlural.word(streak, "день", "дня", "дней") + " подряд")
            }
            Button { adding = true } label: {
                Image(systemName: "plus")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(.ink)
                    .frame(width: 30, height: 30)
                    .background(Color.tone(0.14), in: Circle())
            }
            .buttonStyle(.pressable)
            .accessibilityLabel("Добавить добавку")
        }
    }

    private var empty: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Креатин, омега-3, витамины — отмечай в одно касание, а напоминание не даст забыть.")
                .font(.system(size: 14))
                .foregroundStyle(Color.ink.opacity(0.7))
                .fixedSize(horizontal: false, vertical: true)
            NutritionButton(title: "Добавить добавку", systemImage: "plus") { adding = true }
        }
    }

    private func row(_ item: Supplement, data: SupplementData) -> some View {
        let intake = SupplementTracker.intake(item, on: day, in: data, calendar: calendar)
        let taken = intake != nil
        return Button {
            if taken {
                UISelectionFeedbackGenerator().selectionChanged()
            } else {
                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            }
            withAnimation(Motion.bouncy) { store.toggleSupplement(item, on: day) }
        } label: {
            HStack(spacing: 12) {
                ZStack {
                    Circle()
                        .strokeBorder(Color.ink.opacity(taken ? 0 : 0.25), lineWidth: 2)
                        .background(Circle().fill(taken ? Theme.accent : Color.clear))
                    if taken {
                        Image(systemName: "checkmark")
                            .font(.system(size: 13, weight: .black))
                            .foregroundStyle(.canvas)
                            .transition(.scale.combined(with: .opacity))
                    }
                }
                .frame(width: 30, height: 30)
                VStack(alignment: .leading, spacing: 2) {
                    Text(item.name)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(.ink)
                        .lineLimit(1)
                    Text(detail(item, intake: intake))
                        .font(.system(size: 12))
                        .foregroundStyle(Color.ink.opacity(0.45))
                        .lineLimit(1)
                }
                Spacer(minLength: 8)
                if let reminder = item.reminder {
                    HStack(spacing: 3) {
                        Image(systemName: "bell.fill").font(.system(size: 10, weight: .bold))
                        Text(reminder.text).font(.system(size: 12, weight: .semibold)).monospacedDigit()
                    }
                    .foregroundStyle(Color.ink.opacity(0.4))
                }
            }
            .padding(.vertical, 10)
            .contentShape(Rectangle())
        }
        .buttonStyle(.pressable)
        .contextMenu {
            Button { editing = item } label: { Label("Изменить", systemImage: "pencil") }
            Button(role: .destructive) {
                withAnimation(Motion.snappy) { store.deleteSupplement(item) }
            } label: {
                Label("Удалить", systemImage: "trash")
            }
        }
        .accessibilityLabel("\(item.name), \(taken ? "выпито" : "не выпито")")
        .accessibilityHint("Нажми, чтобы отметить. Удержи, чтобы изменить.")
    }

    private func detail(_ item: Supplement, intake: SupplementIntake?) -> String {
        var parts: [String] = []
        let dose = item.dose.trimmingCharacters(in: .whitespaces)
        if !dose.isEmpty { parts.append(dose) }
        if let intake, calendar.isDateInToday(intake.date) {
            parts.append("в \(NutritionFormat.time(intake.date))")
        } else {
            parts.append(item.slot.title.lowercased())
        }
        return parts.joined(separator: " · ")
    }

    /// Последние семь дней: полный кружок — выпито всё, дуга — часть.
    private func week(_ data: SupplementData) -> some View {
        let days = SupplementTracker.recent(7, data: data, trainedDays: trainedDays, today: Date(), calendar: calendar)
        return HStack(spacing: 0) {
            ForEach(days, id: \.day) { state in
                let isSelected = calendar.isDate(state.day, inSameDayAs: day)
                VStack(spacing: 6) {
                    Text(RuDate.short(weekday: calendar.component(.weekday, from: state.day)))
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(Color.ink.opacity(isSelected ? 0.9 : 0.4))
                    ZStack {
                        Circle().fill(state.isComplete ? Theme.accent : Color.tone(0.14))
                        if !state.isComplete && state.share > 0 {
                            Circle()
                                .trim(from: 0, to: state.share)
                                .stroke(Theme.accent, style: StrokeStyle(lineWidth: 3, lineCap: .round))
                                .rotationEffect(.degrees(-90))
                                .padding(1.5)
                        }
                        if state.isComplete {
                            Image(systemName: "checkmark")
                                .font(.system(size: 10, weight: .black))
                                .foregroundStyle(.canvas)
                        }
                    }
                    .frame(width: 26, height: 26)
                    .overlay(Circle().strokeBorder(Color.ink.opacity(isSelected ? 0.8 : 0), lineWidth: 1.5).padding(-3))
                }
                .frame(maxWidth: .infinity)
            }
        }
        .padding(.top, 4)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("За неделю всё выпито в \(days.filter(\.isComplete).count) из 7 дней")
    }
}

/// Чипы в строку с переносом: не влезает — на следующую строку.
struct ChipFlow: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = rows(width: proposal.width ?? .infinity, subviews: subviews)
        let height = rows.map(\.height).reduce(0, +) + spacing * CGFloat(max(rows.count - 1, 0))
        let width = rows.map(\.width).max() ?? 0
        return CGSize(width: proposal.width ?? width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var y = bounds.minY
        for row in rows(width: bounds.width, subviews: subviews) {
            var x = bounds.minX
            for index in row.indices {
                let size = subviews[index].sizeThatFits(.unspecified)
                subviews[index].place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
                x += size.width + spacing
            }
            y += row.height + spacing
        }
    }

    private struct Row {
        var indices: [Int] = []
        var width: CGFloat = 0
        var height: CGFloat = 0
    }

    private func rows(width: CGFloat, subviews: Subviews) -> [Row] {
        var rows: [Row] = []
        var current = Row()
        for index in subviews.indices {
            let size = subviews[index].sizeThatFits(.unspecified)
            let needed = current.indices.isEmpty ? size.width : current.width + spacing + size.width
            if needed > width, !current.indices.isEmpty {
                rows.append(current)
                current = Row()
            }
            current.width = current.indices.isEmpty ? size.width : current.width + spacing + size.width
            current.height = max(current.height, size.height)
            current.indices.append(index)
        }
        if !current.indices.isEmpty { rows.append(current) }
        return rows
    }
}

// MARK: - Добавить или изменить

struct SupplementSheet: View {
    @Environment(\.dismiss) private var dismiss

    let original: Supplement?
    let onSave: (Supplement) -> Void
    var onDelete: (() -> Void)?

    @State private var name = ""
    @State private var dose = ""
    @State private var slot: SupplementSlot = .morning
    @State private var remind = false
    @State private var time = Calendar.current.date(bySettingHour: 9, minute: 0, second: 0, of: Date()) ?? Date()
    @FocusState private var typing: Bool

    private var trimmedName: String { name.trimmingCharacters(in: .whitespacesAndNewlines) }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    if original == nil { presetsCard }
                    fieldsCard
                    slotCard
                    reminderCard
                    if let onDelete {
                        Button(role: .destructive) {
                            onDelete()
                            dismiss()
                        } label: {
                            Text("Удалить добавку")
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundStyle(Theme.record)
                                .frame(maxWidth: .infinity)
                                .frame(height: 48)
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .padding(.bottom, 32)
            }
            .scrollIndicators(.hidden)
            .scrollDismissesKeyboard(.interactively)
            .background(Color.canvas.ignoresSafeArea())
            .navigationTitle(original == nil ? "Новая добавка" : "Добавка")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Отмена") { dismiss() }
                        .foregroundStyle(Color.ink.opacity(0.7))
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Сохранить", action: save)
                        .font(.body.weight(.bold))
                        .foregroundStyle(trimmedName.isEmpty ? Color.ink.opacity(0.3) : Theme.accent)
                        .disabled(trimmedName.isEmpty)
                }
            }
        }
        .onAppear(perform: fill)
    }

    private var presetsCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            NutritionCaption(text: "Частые")
            ChipFlow(spacing: 8) {
                ForEach(SupplementPreset.all) { preset in
                    let selected = trimmedName == preset.name
                    Button {
                        UISelectionFeedbackGenerator().selectionChanged()
                        name = preset.name
                        dose = preset.dose
                        slot = preset.slot
                    } label: {
                        Text(preset.name)
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(selected ? Color.canvas : Color.ink)
                            .padding(.horizontal, 14)
                            .frame(height: 36)
                            .background(selected ? Theme.accent : Color.tone(0.14), in: Capsule())
                    }
                    .buttonStyle(.pressable)
                }
            }
        }
        .nutritionCard()
    }

    private var fieldsCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            NutritionCaption(text: "Название")
            field("Например, креатин", text: $name)
            NutritionCaption(text: "Доза")
                .padding(.top, 4)
            field("5 г, 2 капсулы", text: $dose)
        }
        .nutritionCard()
    }

    private func field(_ placeholder: String, text: Binding<String>) -> some View {
        TextField(placeholder, text: text)
            .focused($typing)
            .font(.system(size: 17, weight: .semibold))
            .foregroundStyle(.ink)
            .padding(.horizontal, 14)
            .frame(height: 50)
            .background(Color.tone(0.13), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private var slotCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            NutritionCaption(text: "Когда")
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)], spacing: 8) {
                ForEach(SupplementSlot.allCases) { option in
                    let selected = slot == option
                    Button {
                        UISelectionFeedbackGenerator().selectionChanged()
                        withAnimation(Motion.snappy) { slot = option }
                    } label: {
                        HStack(spacing: 8) {
                            Image(systemName: option.systemImage).font(.system(size: 14, weight: .bold))
                            Text(option.title).font(.system(size: 14, weight: .bold))
                            Spacer(minLength: 0)
                        }
                        .foregroundStyle(selected ? Color.canvas : Color.ink)
                        .padding(.horizontal, 14)
                        .frame(height: 46)
                        .background(selected ? Theme.accent : Color.tone(0.13), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                    }
                    .buttonStyle(.pressable)
                }
            }
            if slot == .workout {
                Text("В дни без тренировки её не ждём — серия не рвётся.")
                    .font(.system(size: 12))
                    .foregroundStyle(Color.ink.opacity(0.4))
            }
        }
        .nutritionCard()
    }

    private var reminderCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Toggle(isOn: $remind.animation(Motion.snappy)) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Напоминание")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(.ink)
                    Text("Каждый день, пока не отметишь")
                        .font(.system(size: 12))
                        .foregroundStyle(Color.ink.opacity(0.45))
                }
            }
            .tint(Theme.accent)
            if remind {
                DatePicker("Время", selection: $time, displayedComponents: .hourAndMinute)
                    .datePickerStyle(.wheel)
                    .labelsHidden()
                    .frame(maxWidth: .infinity)
                    .environment(\.locale, Locale(identifier: "ru_RU"))
                    .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .nutritionCard()
    }

    private func fill() {
        guard let original else { return }
        name = original.name
        dose = original.dose
        slot = original.slot
        if let reminder = original.reminder {
            remind = true
            time = Calendar.current.date(bySettingHour: reminder.hour, minute: reminder.minute, second: 0, of: Date()) ?? time
        }
    }

    private func save() {
        guard !trimmedName.isEmpty else { return }
        let parts = Calendar.current.dateComponents([.hour, .minute], from: time)
        var item = original ?? Supplement(name: trimmedName)
        item.name = trimmedName
        item.dose = dose.trimmingCharacters(in: .whitespacesAndNewlines)
        item.slot = slot
        item.reminder = remind ? SupplementReminder(hour: parts.hour ?? 9, minute: parts.minute ?? 0) : nil
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        onSave(item)
        dismiss()
    }
}
