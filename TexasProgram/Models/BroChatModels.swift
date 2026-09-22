import Foundation

enum BroMessageType: String, Codable, Sendable {
    case text
    case workoutResult = "workout_result"
    case photo
}

struct WorkoutSharePayload: Codable, Equatable, Hashable, Sendable {
    var exercise: String
    var weight: String
    var sets: String
    var reps: String
    var isPR: Bool
    var week: Int
    var day: Int
}

struct BroChatMessage: Codable, Identifiable, Equatable, Hashable, Sendable {
    let id: String
    let channelId: String
    let senderId: String
    let senderName: String
    let timestamp: Int64
    let text: String
    let type: BroMessageType
    var workoutPayload: WorkoutSharePayload?
    var photoBase64: String?

    init(
        id: String = UUID().uuidString,
        channelId: String,
        senderId: String,
        senderName: String,
        timestamp: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        text: String = "",
        type: BroMessageType = .text,
        workoutPayload: WorkoutSharePayload? = nil,
        photoBase64: String? = nil
    ) {
        self.id = id
        self.channelId = channelId
        self.senderId = senderId
        self.senderName = senderName
        self.timestamp = timestamp
        self.text = text
        self.type = type
        self.workoutPayload = workoutPayload
        self.photoBase64 = photoBase64
    }

    var timeFormatted: String {
        let date = Date(timeIntervalSince1970: Double(timestamp) / 1000.0)
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm"
        return formatter.string(from: date)
    }
}

struct BroChatChannelPayload: Codable, Sendable {
    let channelId: String
    var messages: [BroChatMessage]
    var updatedAt: Int64
}
