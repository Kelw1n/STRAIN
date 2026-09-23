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
    @State private var selectedBuddyForChat: BroProfileData?
    @State private var copyAlertMessage: String?
    @State private var showingCopyAlert = false
    @State private var scanAlertMessage: String?
    @State private var showingScanAlert = false

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
                MyQRCodeSheet(profile: profile, service: service)
            }
            .sheet(isPresented: $showingScanner) {
                NavigationStack {
                    QRScannerView(
                        onScan: { code in
                            showingScanner = false
                            Task {
                                let res = await service.addBuddy(from: code)
                                await MainActor.run {
                                    scanAlertMessage = res.message
                                    showingScanAlert = true
                                }
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
                    Task {
                        let res = await service.addBuddy(from: text)
                        await MainActor.run {
                            scanAlertMessage = res.message
                            showingScanAlert = true
                        }
                    }
                }
                Button("Отмена", role: .cancel) {}
            }
            .alert("Бро-трекер", isPresented: $showingScanAlert) {
                Button("ОК", role: .cancel) {}
            } message: {
                Text(scanAlertMessage ?? "")
            }
            .sheet(item: $selectedBuddyForProgram) { buddy in
                BuddyProgramView(buddy: buddy) {
                    copyProgramFromBuddy(buddy)
                }
            }
            .sheet(item: $selectedBuddyForChat) { buddy in
                BroChatView(buddy: buddy, profile: profile)
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
                    } onOpenChat: {
                        selectedBuddyForChat = buddy
                    } onDelete: {
                        service.removeBuddy(id: buddy.broId)
                    }
                }
            }
        }
    }

    private func copyProgramFromBuddy(_ buddy: BroProfileData) {
        let progKind = TrainingProgramKind.allCases.first { $0.backupCode == buddy.programKind || $0.rawValue == buddy.programKind } ?? .texas
        let sq = buddy.squat5RM > 0 ? buddy.squat5RM : 100
        let bp = buddy.bench5RM > 0 ? buddy.bench5RM : 100
        let dl = buddy.deadlift5RM > 0 ? buddy.deadlift5RM : 100
        let newProfile: ProgramProfile
        switch progKind {
        case .upperLower:
            newProfile = ProgramProfile(upperLowerInput: UpperLowerInput(squat1RM: sq, bench1RM: bp, deadlift1RM: dl), name: "\(buddy.name) (копия)")
        case .fullBody:
            newProfile = ProgramProfile(fullBodyInput: ProgramInput(squat5RM: sq, bench5RM: bp, deadlift5RM: dl, level: .beginner), level: .aboutYear, name: "\(buddy.name) (копия)")
        case .proTexas:
            newProfile = ProgramProfile(proTexasInput: ProgramInput(squat5RM: sq, bench5RM: bp, deadlift5RM: dl, level: .beginner), name: "\(buddy.name) (копия)")
        default:
            newProfile = ProgramProfile(input: ProgramInput(squat5RM: sq, bench5RM: bp, deadlift5RM: dl, level: .beginner), name: "\(buddy.name) (копия)")
        }
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
    let onOpenChat: () -> Void
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
                        Button("Чат с бро", action: onOpenChat)
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
                        ForEach(buddy.recentLifts) { lift in
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

                HStack(spacing: 10) {
                    Button(action: onOpenChat) {
                        HStack(spacing: 6) {
                            Image(systemName: "bubble.left.and.bubble.right.fill")
                            Text("Чат с бро")
                        }
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(Theme.accentGradient, in: Capsule())
                    }

                    Spacer()

                    Button(action: onOpenProgram) {
                        HStack(spacing: 4) {
                            Image(systemName: "list.clipboard")
                            Text("Программа")
                            Image(systemName: "chevron.right")
                        }
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(Theme.accent)
                    }
                }
                .padding(.top, 4)
            }
        }
    }
}

/// Модальный экран показа своего QR-кода
private struct MyQRCodeSheet: View {
    @Environment(\.dismiss) private var dismiss
    let profile: ProgramProfile
    let service: BroTrackerService

    private var qrLink: String {
        service.buildQRLink(profile: profile)
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
                    Text(profile.name.isEmpty ? "Твой профиль" : profile.name)
                        .font(.title2.weight(.bold))
                    Text("Пусть бро наведёт камеру сканера на этот код")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                }

                HStack(spacing: 8) {
                    Text(service.myBroId)
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

                if !service.isConfirmedServerId && service.isSyncing {
                    HStack(spacing: 8) {
                        ProgressView()
                            .scaleEffect(0.8)
                        Text("Подключение к облаку...")
                            .font(.caption)
                            .foregroundStyle(.secondary)
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
            .task {
                if !service.isConfirmedServerId {
                    await service.syncMyProfile(profile: profile)
                }
            }
        }
    }
}

/// Экран просмотра расписания и упражнений друга с возможностью копирования
private struct BuddyProgramView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let buddy: BroProfileData
    let onCopy: () -> Void

    @State private var selectedWeek: Int
    @Namespace private var weekPill

    init(buddy: BroProfileData, onCopy: @escaping () -> Void) {
        self.buddy = buddy
        self.onCopy = onCopy
        _selectedWeek = State(initialValue: buddy.currentWeek)
    }

    private var availableWeeks: [Int] {
        let set = Set(buddy.programDays.map(\.week))
        let sorted = set.sorted()
        return sorted.isEmpty ? [buddy.currentWeek] : sorted
    }

    private var daysForSelectedWeek: [BroWorkoutDay] {
        buddy.programDays
            .filter { $0.week == selectedWeek }
            .sorted { $0.day < $1.day }
    }

    private var weekSelector: some View {
        ScrollViewReader { proxy in
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 9) {
                    ForEach(availableWeeks, id: \.self) { weekNum in
                        weekChip(weekNum)
                            .id(weekNum)
                    }
                }
                .padding(.horizontal)
                .padding(.vertical, 6)
            }
            .onChange(of: selectedWeek) { _, newValue in
                withAnimation(Motion.maybe(Motion.smooth, reduce: reduceMotion)) {
                    proxy.scrollTo(newValue, anchor: .center)
                }
            }
            .onAppear {
                proxy.scrollTo(selectedWeek, anchor: .center)
            }
        }
    }

    private func weekChip(_ weekNum: Int) -> some View {
        let isSelected = selectedWeek == weekNum
        let isCurrent = buddy.currentWeek == weekNum
        return Button {
            withAnimation(Motion.maybe(Motion.snappy, reduce: reduceMotion)) {
                selectedWeek = weekNum
            }
        } label: {
            VStack(spacing: 3) {
                Text("\(weekNum)")
                    .font(.system(size: 17, weight: .bold, design: .rounded))
                if isCurrent {
                    Circle()
                        .fill(isSelected ? Color.white : Theme.accent)
                        .frame(width: 6, height: 6)
                } else {
                    Circle()
                        .fill(Color.clear)
                        .frame(width: 6, height: 6)
                }
            }
            .frame(width: 48, height: 52)
            .foregroundStyle(isSelected ? Color.white : (isCurrent ? Theme.accent : Color.primary))
            .background {
                ZStack {
                    Capsule().fill(Color.primary.opacity(0.06))
                    if isSelected {
                        Capsule()
                            .fill(Theme.accentGradient)
                            .matchedGeometryEffect(id: "buddyWeekPill", in: weekPill)
                    }
                }
            }
        }
        .buttonStyle(.pressable)
        .accessibilityLabel("Неделя \(weekNum)\(isCurrent ? ", текущая" : "")")
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    CardView {
                        VStack(alignment: .leading, spacing: 8) {
                            HStack {
                                Text(buddy.name)
                                    .font(.title3.weight(.bold))
                                Spacer()
                                if buddy.isOnline {
                                    TagBadge(text: "В сети", systemImage: "circle.fill", gradient: Theme.successGradient)
                                }
                            }
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

                    if !buddy.programDays.isEmpty {
                        VStack(alignment: .leading, spacing: 8) {
                            HStack {
                                Text("Неделя \(selectedWeek)")
                                    .font(.headline)
                                Spacer()
                                if selectedWeek == buddy.currentWeek {
                                    TagBadge(text: "Текущая неделя", systemImage: "star.fill", gradient: Theme.accentGradient)
                                }
                            }
                            weekSelector
                        }
                    }

                    Text("Дни и упражнения")
                        .font(.headline)
                        .padding(.top, 4)

                    if buddy.programDays.isEmpty {
                        CardView {
                            VStack(spacing: 8) {
                                Image(systemName: "calendar.badge.exclamationmark")
                                    .font(.largeTitle)
                                    .foregroundStyle(.secondary)
                                Text("Детальные дни не загружены")
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 8)
                        }
                    } else if daysForSelectedWeek.isEmpty {
                        CardView {
                            Text("Для недели \(selectedWeek) нет запланированных дней")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                    } else {
                        ForEach(daysForSelectedWeek) { day in
                            let isActiveDay = (selectedWeek == buddy.currentWeek && day.day == buddy.currentDay)
                            if isActiveDay {
                                HighlightCard {
                                    dayContent(day: day, isActive: true)
                                }
                            } else {
                                CardView {
                                    dayContent(day: day, isActive: false)
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
            .onAppear {
                if !availableWeeks.contains(selectedWeek), let first = availableWeeks.first {
                    selectedWeek = first
                }
            }
        }
    }

    @ViewBuilder
    private func dayContent(day: BroWorkoutDay, isActive: Bool) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("День \(day.day) · \(day.title)")
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(isActive ? Theme.accent : .primary)
                Spacer()
                if isActive {
                    TagBadge(text: "Активный день", systemImage: "flame.fill", gradient: Theme.accentGradient)
                }
            }

            Divider().opacity(0.3)

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
