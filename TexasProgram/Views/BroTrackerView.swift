import SwiftUI
import SwiftData

/// Экран «Бро-трекер»: онлайн отслеживание прогресса друзей и просмотр их программ
struct BroTrackerView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var modelContext
    let profile: ProgramProfile

    @State private var service = BroTrackerService.shared
    @State private var showingMyQR = false
    @State private var showingScanner = false
    @State private var showingManualInput = false
    @State private var manualInputText = ""
    @State private var selectedBuddyForProgram: BroProfileData?
    @State private var copyAlertMessage: String?
    @State private var showingCopyAlert = false

    init(profile: ProgramProfile) {
        self.profile = profile
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    myStatusCard
                    crewSection
                }
                .padding(.horizontal)
                .padding(.vertical, 12)
            }
            .screenBackground()
            .navigationTitle("Бро-трекер")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Закрыть") { dismiss() }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    HStack(spacing: 12) {
                        Button {
                            showingMyQR = true
                        } label: {
                            Image(systemName: "qrcode")
                                .font(.body.weight(.semibold))
                        }

                        Button {
                            showingScanner = true
                        } label: {
                            Image(systemName: "qrcode.viewfinder")
                                .font(.body.weight(.semibold))
                        }
                    }
                }
            }
            .task {
                await service.syncMyProfile(profile: profile)
                await service.refreshBuddies()
            }
            .refreshable {
                await service.syncMyProfile(profile: profile)
                await service.refreshBuddies()
            }
            .sheet(isPresented: $showingMyQR) {
                MyQRCodeSheet(myBroId: service.myBroId, profileName: profile.name)
            }
            .sheet(isPresented: $showingScanner) {
                NavigationStack {
                    QRScannerView(
                        onScan: { code in
                            showingScanner = false
                            Task {
                                _ = await service.addBuddy(from: code)
                            }
                        },
                        onCancel: { showingScanner = false }
                    )
                    .navigationTitle("Сканировать QR бро")
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .topBarLeading) {
                            Button("Отмена") { showingScanner = false }
                        }
                        ToolbarItem(placement: .topBarTrailing) {
                            Button("Ввести код") {
                                showingScanner = false
                                showingManualInput = true
                            }
                        }
                    }
                }
            }
            .alert("Ввести код бро", isPresented: $showingManualInput) {
                TextField("Код бро (ID или ссылка)", text: $manualInputText)
                Button("Добавить") {
                    let text = manualInputText
                    manualInputText = ""
                    Task { _ = await service.addBuddy(from: text) }
                }
                Button("Отмена", role: .cancel) {}
            }
            .sheet(item: $selectedBuddyForProgram) { buddy in
                BuddyProgramView(buddy: buddy) {
                    copyProgramFromBuddy(buddy)
                }
            }
            .alert("Программа скопирована", isPresented: $showingCopyAlert) {
                Button("ОК", role: .cancel) {}
            } message: {
                Text(copyAlertMessage ?? "")
            }
        }
    }

    private var myStatusCard: some View {
        CardView {
            HStack(spacing: 12) {
                ZStack {
                    Circle()
                        .fill(Theme.accentGradient)
                        .frame(width: 44, height: 44)
                    Image(systemName: "person.crop.circle.fill")
                        .font(.system(size: 24))
                        .foregroundStyle(.white)
                }

                VStack(alignment: .leading, spacing: 3) {
                    HStack {
                        Text(profile.name.isEmpty ? "Ты" : profile.name)
                            .font(.headline)
                            .foregroundStyle(.primary)
                        TagBadge(text: "Онлайн", systemImage: "circle.fill", gradient: Theme.successGradient)
                    }

                    Text("Неделя \(profile.currentWeek) · \(profile.programKind.rawValue)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Spacer()

                Button {
                    showingMyQR = true
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "qrcode")
                        Text("QR")
                    }
                    .font(.caption.weight(.semibold))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(Theme.accent.opacity(0.12), in: Capsule())
                }
            }
        }
    }

    private var crewSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Твоя банда (\(service.buddies.count))")
                    .font(.title3.weight(.bold))
                    .foregroundStyle(.primary)

                Spacer()

                Button {
                    showingScanner = true
                } label: {
                    Label("Добавить", systemImage: "plus")
                        .font(.subheadline.weight(.semibold))
                }
            }
            .padding(.top, 4)

            if service.buddies.isEmpty {
                CardView {
                    VStack(spacing: 12) {
                        Image(systemName: "person.2.slash")
                            .font(.system(size: 36))
                            .foregroundStyle(.secondary)
                            .padding(.top, 8)

                        Text("Пока нет добавленных друзей")
                            .font(.headline)
                            .foregroundStyle(.primary)

                        Text("Отсканируй QR-код друга или дай ему сосканировать свой, чтобы видеть тренировки друг друга в реальном времени.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)

                        HStack(spacing: 12) {
                            Button {
                                showingScanner = true
                            } label: {
                                Label("Сканировать QR", systemImage: "qrcode.viewfinder")
                            }
                            .buttonStyle(.gradientProminent)

                            Button {
                                showingMyQR = true
                            } label: {
                                Label("Мой QR", systemImage: "qrcode")
                            }
                            .buttonStyle(.pressable)
                        }
                        .padding(.top, 4)
                        .padding(.bottom, 6)
                    }
                    .frame(maxWidth: .infinity)
                }
            } else {
                ForEach(service.buddies) { buddy in
                    BuddyCardView(buddy: buddy) {
                        selectedBuddyForProgram = buddy
                    } onDelete: {
                        service.removeBuddy(id: buddy.broId)
                    }
                }
            }
        }
    }

    private func copyProgramFromBuddy(_ buddy: BroProfileData) {
        let newProfile = ProgramProfile(
            input: ProgramInput(
                squat5RM: buddy.squat5RM > 0 ? buddy.squat5RM : 100,
                bench5RM: buddy.bench5RM > 0 ? buddy.bench5RM : 100,
                deadlift5RM: buddy.deadlift5RM > 0 ? buddy.deadlift5RM : 100,
                level: .beginner
            )
        )
        newProfile.name = "\(buddy.name) (копия)"
        modelContext.insert(newProfile)
        try? modelContext.save()

        copyAlertMessage = "Программа друга «\(buddy.name)» успешно добавлена в твои профили!"
        showingCopyAlert = true
    }
}

/// Карточка отдельного бро в списке
private struct BuddyCardView: View {
    let buddy: BroProfileData
    let onOpenProgram: () -> Void
    let onDelete: () -> Void

    var body: some View {
        CardView {
            VStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 3) {
                        HStack {
                            Text(buddy.name)
                                .font(.headline)
                                .foregroundStyle(.primary)

                            Circle()
                                .fill(buddy.isRecentlyActive ? Theme.success : Color.gray)
                                .frame(width: 8, height: 8)
                        }

                        Text(buddy.statusDescription)
                            .font(.caption2)
                            .foregroundStyle(buddy.isRecentlyActive ? Theme.success : .secondary)
                    }

                    Spacer()

                    Menu {
                        Button("Смотреть программу", action: onOpenProgram)
                        Button("Удалить из друзей", role: .destructive, action: onDelete)
                    } label: {
                        Image(systemName: "ellipsis")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .padding(8)
                    }
                }

                Divider().opacity(0.4)

                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("ТЕКУЩИЙ ЭТАП")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundStyle(.secondary)
                        Text("Неделя \(buddy.currentWeek) · День \(buddy.currentDay)")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Theme.accent)
                    }

                    Spacer()

                    VStack(alignment: .trailing, spacing: 2) {
                        Text("ПРОГРАММА")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundStyle(.secondary)
                        Text(buddy.programTitle)
                            .font(.subheadline)
                            .foregroundStyle(.primary)
                    }
                }

                if !buddy.recentLifts.isEmpty {
                    VStack(alignment: .leading, spacing: 4) {
                        ForEach(buddy.recentLifts.prefix(2)) { lift in
                            HStack {
                                Text(lift.name)
                                    .font(.caption)
                                    .foregroundStyle(.primary)
                                Spacer()
                                Text(lift.prescription)
                                    .font(.caption.weight(.medium))
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                    .padding(8)
                    .background(Color.white.opacity(0.04), in: RoundedRectangle(cornerRadius: 8))
                }

                Button(action: onOpenProgram) {
                    HStack {
                        Image(systemName: "list.clipboard")
                        Text("Посмотреть программу")
                        Spacer()
                        Image(systemName: "chevron.right")
                    }
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(Theme.accent)
                }
                .padding(.top, 2)
            }
        }
    }
}

/// Модальный экран показа своего QR-кода
private struct MyQRCodeSheet: View {
    @Environment(\.dismiss) private var dismiss
    let myBroId: String
    let profileName: String

    private var qrLink: String {
        "strain://bro/\(myBroId)"
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 20) {
                Spacer()

                if let qrImage = QRCodeService.generateQRCode(from: qrLink, size: 260) {
                    Image(uiImage: qrImage)
                        .interpolation(.none)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 240, height: 240)
                        .padding(16)
                        .background(Color.white, in: RoundedRectangle(cornerRadius: 20))
                        .shadow(color: .black.opacity(0.15), radius: 12)
                }

                VStack(spacing: 6) {
                    Text(profileName.isEmpty ? "Твой профиль" : profileName)
                        .font(.title2.weight(.bold))
                    Text("Пусть бро наведёт камеру сканера на этот код")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                }

                HStack(spacing: 8) {
                    Text(myBroId)
                        .font(.system(.subheadline, design: .monospaced).weight(.bold))
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(Color.white.opacity(0.08), in: Capsule())

                    Button {
                        UIPasteboard.general.string = qrLink
                    } label: {
                        Image(systemName: "doc.on.doc")
                            .font(.subheadline)
                    }
                }

                Spacer()

                Button("Готово") { dismiss() }
                    .buttonStyle(.gradientProminent)
                    .padding(.horizontal, 24)
                    .padding(.bottom, 16)
            }
            .screenBackground()
            .navigationTitle("Мой QR-код")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

/// Экран просмотра расписания и упражнений друга с возможностью копирования
private struct BuddyProgramView: View {
    @Environment(\.dismiss) private var dismiss
    let buddy: BroProfileData
    let onCopy: () -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    CardView {
                        VStack(alignment: .leading, spacing: 8) {
                            Text(buddy.name)
                                .font(.title3.weight(.bold))
                            Text("Программа: \(buddy.programTitle) · Неделя \(buddy.currentWeek)")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)

                            HStack(spacing: 16) {
                                if buddy.squat5RM > 0 {
                                    VStack(alignment: .leading) {
                                        Text("Присед").font(.caption2).foregroundStyle(.secondary)
                                        Text("\(Int(buddy.squat5RM)) кг").font(.headline)
                                    }
                                }
                                if buddy.bench5RM > 0 {
                                    VStack(alignment: .leading) {
                                        Text("Жим").font(.caption2).foregroundStyle(.secondary)
                                        Text("\(Int(buddy.bench5RM)) кг").font(.headline)
                                    }
                                }
                                if buddy.deadlift5RM > 0 {
                                    VStack(alignment: .leading) {
                                        Text("Тяга").font(.caption2).foregroundStyle(.secondary)
                                        Text("\(Int(buddy.deadlift5RM)) кг").font(.headline)
                                    }
                                }
                            }
                            .padding(.top, 4)
                        }
                    }

                    Button {
                        onCopy()
                        dismiss()
                    } label: {
                        Label("Скопировать программу себе", systemImage: "arrow.down.doc.fill")
                    }
                    .buttonStyle(.gradientProminent)

                    Text("Дни и упражнения")
                        .font(.headline)
                        .padding(.top, 8)

                    if buddy.programDays.isEmpty {
                        CardView {
                            Text("Детальные дни не загружены")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                    } else {
                        ForEach(buddy.programDays) { day in
                            CardView {
                                VStack(alignment: .leading, spacing: 8) {
                                    Text("Неделя \(day.week) · \(day.title)")
                                        .font(.subheadline.weight(.bold))
                                        .foregroundStyle(Theme.accent)

                                    ForEach(day.exercises) { ex in
                                        HStack {
                                            Text(ex.name)
                                                .font(.subheadline)
                                            Spacer()
                                            Text("\(ex.sets > 0 ? "\(ex.sets)×\(ex.reps)" : ex.reps) · \(ex.weight)")
                                                .font(.caption)
                                                .foregroundStyle(.secondary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                .padding()
            }
            .screenBackground()
            .navigationTitle("Программа бро")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Назад") { dismiss() }
                }
            }
        }
    }
}
