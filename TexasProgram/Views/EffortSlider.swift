import SwiftUI
import UIKit

/// Оценка усилия 1–10, как у REBRN: три клина растут слева направо,
/// палец ведёт по ним — заливка идёт следом, цифра и подпись меняются.
/// Увёл палец к самому левому краю — оценка снимается (её можно не ставить).
enum EffortScale {
    static let titles = [
        1: "разминка", 2: "очень легко", 3: "спокойно", 4: "комфортно", 5: "умеренно",
        6: "рабоче", 7: "тяжеловато", 8: "тяжело", 9: "на пределе", 10: "до отказа"
    ]

    static let easy = Color(red: 0.36, green: 0.86, blue: 0.29)
    static let normal = Color(red: 0.98, green: 0.78, blue: 0.27)
    static let hard = Color(red: 0.95, green: 0.32, blue: 0.14)

    /// Зоны: 1–3 легко, 4–7 нормально, 8–10 очень тяжело. Ширина клина — по числу оценок в нём.
    static let zones: [ClosedRange<Int>] = [1...3, 4...7, 8...10]

    static func color(_ score: Int) -> Color {
        switch score {
        case ...3: return easy
        case 4...7: return normal
        default: return hard
        }
    }

    static func zone(of score: Int) -> Int {
        zones.firstIndex { $0.contains(score) } ?? 0
    }

    /// Строка для истории: «8/10».
    static func text(_ score: Int?) -> String? {
        score.map { "\($0)/10" }
    }
}

struct EffortSlider: View {
    @Binding var score: Int?

    private let barHeight: CGFloat = 30
    private let gap: CGFloat = 8

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            header
            bar
            labels
        }
        .padding(18)
        .background(Color(white: 0.086), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).strokeBorder(Color.white.opacity(0.08), lineWidth: 1))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Усилие")
        .accessibilityValue(score.map { "\($0) из 10, \(EffortScale.titles[$0] ?? "")" } ?? "не указано")
        .accessibilityAdjustableAction { direction in
            switch direction {
            case .increment: set(min((score ?? 0) + 1, 10))
            case .decrement: set((score ?? 1) - 1 <= 0 ? nil : (score ?? 1) - 1)
            @unknown default: break
            }
        }
    }

    // MARK: Части

    private var header: some View {
        HStack(alignment: .firstTextBaseline) {
            if let score {
                HStack(alignment: .firstTextBaseline, spacing: 3) {
                    Text("\(score)")
                        .font(.system(size: 30, weight: .heavy, design: Theme.fontDesign))
                        .monospacedDigit()
                        .foregroundStyle(.white)
                        .contentTransition(.numericText(value: Double(score)))
                    Text("/10")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Color.white.opacity(0.35))
                }
            } else {
                Capsule()
                    .fill(Color.white.opacity(0.15))
                    .frame(width: 22, height: 4)
                    .frame(height: 36)
            }
            Spacer()
            Text(score.flatMap { EffortScale.titles[$0] } ?? "Можно пропустить")
                .font(.system(size: 14, weight: score == nil ? .medium : .semibold))
                .foregroundStyle(score.map { EffortScale.color($0) } ?? Color.white.opacity(0.4))
                .contentTransition(.opacity)
                .id(score ?? 0)
                .transition(.opacity)
        }
        .animation(.snappy(duration: 0.22), value: score)
    }

    private var bar: some View {
        GeometryReader { proxy in
            let width = proxy.size.width
            let segments = segmentFrames(width: width)
            let fill = fillWidth(width: width)
            let color = score.map { EffortScale.color($0) } ?? .clear
            ZStack(alignment: .bottomLeading) {
                ForEach(segments.indices, id: \.self) { index in
                    let frame = segments[index]
                    WedgeShape(left: wedgeHeight(frame.minX, width: width), right: wedgeHeight(frame.maxX, width: width), total: barHeight)
                        .fill(Color.white.opacity(0.07))
                        .frame(width: frame.width, height: barHeight)
                        .offset(x: frame.minX)
                    WedgeShape(left: wedgeHeight(frame.minX, width: width), right: wedgeHeight(frame.maxX, width: width), total: barHeight)
                        .fill(color)
                        .frame(width: frame.width, height: barHeight)
                        .mask(alignment: .leading) {
                            Rectangle().frame(width: max(0, min(frame.width, fill - frame.minX)))
                        }
                        .offset(x: frame.minX)
                }
            }
            .frame(width: width, height: barHeight, alignment: .bottomLeading)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in set(scoreAt(value.location.x, width: width)) }
            )
            .animation(.snappy(duration: 0.25), value: score)
        }
        .frame(height: barHeight)
    }

    private var labels: some View {
        let active = score.map { EffortScale.zone(of: $0) }
        return HStack {
            zoneLabel("ЛЕГКО", color: EffortScale.easy, on: active == 0)
            Spacer()
            zoneLabel("НОРМАЛЬНО", color: EffortScale.normal, on: active == 1)
            Spacer()
            zoneLabel("ОЧЕНЬ ТЯЖЕЛО", color: EffortScale.hard, on: active == 2)
        }
        .animation(.easeOut(duration: 0.2), value: active)
    }

    private func zoneLabel(_ text: String, color: Color, on: Bool) -> some View {
        Text(text)
            .font(.system(size: 10, weight: .heavy))
            .tracking(0.4)
            .foregroundStyle(color.opacity(on ? 1 : 0.35))
    }

    // MARK: Геометрия

    /// Клинья по ширине зон (3 : 4 : 3) с зазорами между ними.
    private func segmentFrames(width: CGFloat) -> [CGRect] {
        let usable = width - gap * CGFloat(EffortScale.zones.count - 1)
        var x: CGFloat = 0
        return EffortScale.zones.map { zone in
            let w = usable * CGFloat(zone.count) / 10
            defer { x += w + gap }
            return CGRect(x: x, y: 0, width: w, height: barHeight)
        }
    }

    /// Высота клина растёт слева направо: от трети до полной.
    private func wedgeHeight(_ x: CGFloat, width: CGFloat) -> CGFloat {
        guard width > 0 else { return barHeight }
        return barHeight * (0.34 + 0.66 * min(max(x / width, 0), 1))
    }

    /// Ширина заливки: оценка N закрашивает N делений, деления раскладываются по клиньям.
    private func fillWidth(width: CGFloat) -> CGFloat {
        guard let score else { return 0 }
        let frames = segmentFrames(width: width)
        for (index, zone) in EffortScale.zones.enumerated() where zone.contains(score) {
            let frame = frames[index]
            return frame.minX + frame.width * CGFloat(score - zone.lowerBound + 1) / CGFloat(zone.count)
        }
        return width
    }

    /// Какая оценка под пальцем. Совсем у левого края — оценки нет.
    private func scoreAt(_ x: CGFloat, width: CGFloat) -> Int? {
        guard width > 0, x > 6 else { return nil }
        let frames = segmentFrames(width: width)
        for (index, zone) in EffortScale.zones.enumerated() {
            let frame = frames[index]
            // Зазор между клиньями относим к левому.
            let right = index == frames.count - 1 ? .infinity : frame.maxX + gap
            if x < right {
                let part = max(0, min(1, (x - frame.minX) / frame.width))
                return min(zone.upperBound, zone.lowerBound + Int(part * CGFloat(zone.count)))
            }
        }
        return 10
    }

    private func set(_ value: Int?) {
        guard value != score else { return }
        UISelectionFeedbackGenerator().selectionChanged()
        score = value
    }
}

/// Клин: нижний край ровный, верхний поднимается слева направо, углы скруглены.
private struct WedgeShape: Shape {
    let left: CGFloat
    let right: CGFloat
    let total: CGFloat

    func path(in rect: CGRect) -> Path {
        let r: CGFloat = 5
        let bottom = rect.maxY
        let topLeft = bottom - min(left, rect.height)
        let topRight = bottom - min(right, rect.height)
        var path = Path()
        path.move(to: CGPoint(x: rect.minX + r, y: bottom))
        path.addLine(to: CGPoint(x: rect.maxX - r, y: bottom))
        path.addQuadCurve(to: CGPoint(x: rect.maxX, y: bottom - r), control: CGPoint(x: rect.maxX, y: bottom))
        path.addLine(to: CGPoint(x: rect.maxX, y: topRight + r))
        path.addQuadCurve(to: CGPoint(x: rect.maxX - r, y: topRight), control: CGPoint(x: rect.maxX, y: topRight))
        path.addLine(to: CGPoint(x: rect.minX + r, y: topLeft))
        path.addQuadCurve(to: CGPoint(x: rect.minX, y: topLeft + r), control: CGPoint(x: rect.minX, y: topLeft))
        path.addLine(to: CGPoint(x: rect.minX, y: bottom - r))
        path.addQuadCurve(to: CGPoint(x: rect.minX + r, y: bottom), control: CGPoint(x: rect.minX, y: bottom))
        path.closeSubpath()
        return path
    }
}
