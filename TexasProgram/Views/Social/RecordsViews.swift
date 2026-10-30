import SwiftUI
import UIKit

// MARK: - Рекорды в профиле

/// Витрина в профиле: присед, жим и становая по расчётному максимуму и сумма
/// троеборья. Нажатие — все рекорды; в своём профиле оттуда видна история движения.
struct ProfileRecordsCard: View {
    let records: [SocialRecord]
    let isMe: Bool
    /// Вес тела — чтобы сумма читалась и в разах от веса.
    var bodyWeight: Double?
    var onOpen: () -> Void

    var body: some View {
        Button(action: onOpen) {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Text("РЕКОРДЫ")
                        .font(.system(size: 13, weight: .bold))
                        .tracking(1.4)
                        .foregroundStyle(Color.ink.opacity(0.5))
                    Spacer()
                    if !records.isEmpty {
                        Text("Все \(records.count)")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(Color.ink.opacity(0.6))
                        Image(systemName: "chevron.right")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(Color.ink.opacity(0.4))
                    }
                }
                if records.isEmpty {
                    Text(isMe ? "Рекорды появятся после первой тренировки с весами" : "Рекордов пока нет")
                        .font(.system(size: 14))
                        .foregroundStyle(Color.ink.opacity(0.5))
                        .frame(maxWidth: .infinity, alignment: .center)
                        .padding(.vertical, 8)
                } else {
                    HStack(spacing: 8) {
                        ForEach(PersonalRecords.Lift.allCases, id: \.self) { lift in
                            RecordTile(lift: lift, record: PersonalRecords.best(records, lift: lift))
                        }
                    }
                    if let total = PersonalRecords.total(records) {
                        RecordTotalLine(total: total, bodyWeight: bodyWeight)
                    }
                }
            }
            .padding(18)
            .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        }
        .buttonStyle(.pressable)
        .disabled(records.isEmpty)
    }
}

private struct RecordTile: View {
    let lift: PersonalRecords.Lift
    let record: SocialRecord?

    var body: some View {
        VStack(spacing: 4) {
            Text(lift.title)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Color.ink.opacity(0.6))
                .lineLimit(1)
            HStack(alignment: .firstTextBaseline, spacing: 2) {
                Text(record.map { WeightFormat.plain($0.estimate) } ?? "—")
                    .font(.system(size: 24, weight: .heavy, design: Theme.fontDesign))
                    .monospacedDigit()
                    .foregroundStyle(record == nil ? Color.ink.opacity(0.3) : .ink)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                if record != nil {
                    Text("кг")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(Color.ink.opacity(0.5))
                }
            }
            Text(record?.setText ?? "нет подходов")
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(record == nil ? Color.ink.opacity(0.3) : Theme.accent)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 12)
        .background(Color.tone(0.12), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .accessibilityElement(children: .combine)
    }
}

/// «Сумма ≈ 478 кг · ×5,6 веса тела».
private struct RecordTotalLine: View {
    let total: Double
    let bodyWeight: Double?

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: "sum")
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(Theme.accent)
            Text("Сумма троеборья")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(.ink)
            Spacer(minLength: 6)
            Text("≈ \(WeightFormat.plain(total)) кг")
                .font(.system(size: 15, weight: .heavy, design: Theme.fontDesign))
                .monospacedDigit()
                .foregroundStyle(.ink)
            if let bodyWeight, bodyWeight > 0 {
                Text("· ×\(ChallengeScoring.decimal(total / bodyWeight))")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(Color.ink.opacity(0.5))
            }
        }
    }
}

// MARK: - Все рекорды

struct RecordsSheet: View {
    @Environment(\.dismiss) private var dismiss
    let username: String
    let records: [SocialRecord]
    var bodyWeight: Double?
    /// Свой профиль: строка ведёт в историю движения.
    var profile: ProgramProfile?

    private static let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ru_RU")
        formatter.dateFormat = "d MMM yyyy"
        return formatter
    }()

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    if let total = PersonalRecords.total(records) {
                        RecordTotalLine(total: total, bodyWeight: bodyWeight)
                            .padding(16)
                            .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                    }
                    VStack(spacing: 0) {
                        ForEach(records) { record in
                            row(record)
                            if record.id != records.last?.id {
                                Divider().overlay(Color.ink.opacity(0.06)).padding(.leading, 16)
                            }
                        }
                    }
                    .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
                    Text("Число справа — расчётный максимум на один раз по лучшему подходу (формула Эпли). Сумма троеборья — по приседу, жиму лёжа и становой.")
                        .font(.system(size: 12))
                        .foregroundStyle(Color.ink.opacity(0.4))
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .padding(.bottom, 32)
            }
            .scrollIndicators(.hidden)
            .background(Color.canvas.ignoresSafeArea())
            .navigationTitle(profile == nil ? "Рекорды @\(username)" : "Рекорды")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Готово") { dismiss() }
                        .font(.body.weight(.bold))
                        .foregroundStyle(Theme.accent)
                }
            }
        }
    }

    @ViewBuilder
    private func row(_ record: SocialRecord) -> some View {
        if let profile {
            NavigationLink {
                ExerciseHistoryView(profile: profile, name: record.name)
            } label: {
                rowContent(record, opens: true)
            }
            .buttonStyle(.pressable)
        } else {
            rowContent(record, opens: false)
        }
    }

    private func rowContent(_ record: SocialRecord, opens: Bool) -> some View {
        HStack(spacing: 12) {
            ExerciseThumb(name: record.name, size: 40)
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(record.name)
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(.ink)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                    if record.lift != nil {
                        Image(systemName: "trophy.fill")
                            .font(.system(size: 10))
                            .foregroundStyle(Theme.accent)
                    }
                }
                Text(detail(record))
                    .font(.system(size: 12))
                    .foregroundStyle(Color.ink.opacity(0.5))
            }
            Spacer(minLength: 8)
            Text("≈ \(WeightFormat.plain(record.estimate))")
                .font(.system(size: 16, weight: .heavy, design: Theme.fontDesign))
                .monospacedDigit()
                .foregroundStyle(Theme.accent)
            if opens {
                Image(systemName: "chevron.right")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(Color.ink.opacity(0.3))
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .contentShape(Rectangle())
    }

    private func detail(_ record: SocialRecord) -> String {
        guard record.date > 0 else { return record.setText }
        let date = Date(timeIntervalSince1970: record.date / 1000)
        return "\(record.setText) · \(Self.dateFormatter.string(from: date))"
    }
}
