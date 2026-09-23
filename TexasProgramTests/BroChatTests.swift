import XCTest
@testable import TexasProgram

final class BroChatTests: XCTestCase {

    func testChannelIdSymmetry() {
        func makeChannelId(id1: String, id2: String) -> String {
            let ids = [id1, id2].sorted()
            return "chat_\(ids[0])_\(ids[1])"
        }

        let ch1 = makeChannelId(id1: "bro_alpha", id2: "bro_beta")
        let ch2 = makeChannelId(id1: "bro_beta", id2: "bro_alpha")
        XCTAssertEqual(ch1, ch2)
        XCTAssertEqual(ch1, "chat_bro_alpha_bro_beta")
    }

    func testTextMessageSerialization() throws {
        let msg = BroChatMessage(
            id: "msg_123",
            channelId: "chat_a_b",
            senderId: "a",
            senderName: "Алексей",
            timestamp: 1700000000000,
            text: "Я в зале 🏋️",
            type: .text
        )

        let data = try JSONEncoder().encode(msg)
        let decoded = try JSONDecoder().decode(BroChatMessage.self, from: data)

        XCTAssertEqual(decoded.id, "msg_123")
        XCTAssertEqual(decoded.channelId, "chat_a_b")
        XCTAssertEqual(decoded.senderName, "Алексей")
        XCTAssertEqual(decoded.text, "Я в зале 🏋️")
        XCTAssertEqual(decoded.type, .text)
    }

    func testWorkoutResultSharingSerialization() throws {
        let payload = WorkoutSharePayload(
            exercise: "Жим лёжа",
            weight: "120 кг",
            sets: "5",
            reps: "5",
            isPR: true,
            week: 8,
            day: 1
        )
        let msg = BroChatMessage(
            id: "msg_pr",
            channelId: "chat_a_b",
            senderId: "a",
            senderName: "Алексей",
            timestamp: 1700000010000,
            text: "🔥 НОВЫЙ РЕКОРД! Жим лёжа 120 кг 5×5",
            type: .workoutResult,
            workoutPayload: payload
        )

        let data = try JSONEncoder().encode(msg)
        let decoded = try JSONDecoder().decode(BroChatMessage.self, from: data)

        XCTAssertEqual(decoded.type, .workoutResult)
        XCTAssertNotNil(decoded.workoutPayload)
        XCTAssertEqual(decoded.workoutPayload?.exercise, "Жим лёжа")
        XCTAssertEqual(decoded.workoutPayload?.weight, "120 кг")
        XCTAssertEqual(decoded.workoutPayload?.isPR, true)
        XCTAssertEqual(decoded.workoutPayload?.week, 8)
    }

    func testPhotoMessageBase64Serialization() throws {
        let dummyBase64 = "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD/2wBD..."
        let msg = BroChatMessage(
            id: "msg_photo",
            channelId: "chat_a_b",
            senderId: "b",
            senderName: "Дмитрий",
            timestamp: 1700000020000,
            text: "📷 Фотография с тренировки",
            type: .photo,
            photoBase64: dummyBase64
        )

        let data = try JSONEncoder().encode(msg)
        let decoded = try JSONDecoder().decode(BroChatMessage.self, from: data)

        XCTAssertEqual(decoded.type, .photo)
        XCTAssertEqual(decoded.photoBase64, dummyBase64)
    }

    func testMessageMergingAndDeduplication() {
        let m1 = BroChatMessage(id: "1", channelId: "ch", senderId: "a", senderName: "Алексей", timestamp: 100, text: "Привет", type: .text)
        let m2 = BroChatMessage(id: "2", channelId: "ch", senderId: "b", senderName: "Дмитрий", timestamp: 200, text: "Здорово", type: .text)
        let m3 = BroChatMessage(id: "3", channelId: "ch", senderId: "a", senderName: "Алексей", timestamp: 300, text: "Жму", type: .text)

        var local = [m1, m2]
        let incoming = [m2, m3]

        var existingIds = Set(local.map { $0.id })
        for msg in incoming where !existingIds.contains(msg.id) {
            local.append(msg)
            existingIds.insert(msg.id)
        }
        local.sort { $0.timestamp < $1.timestamp }

        XCTAssertEqual(local.count, 3)
        XCTAssertEqual(local[0].id, "1")
        XCTAssertEqual(local[1].id, "2")
        XCTAssertEqual(local[2].id, "3")
    }

    func testCrossPlatformMessageTypeCompatibility() throws {
        let androidJson = """
        {"id":"android_1","channelId":"chat_a_b","senderId":"b","senderName":"Android","timestamp":100,"text":"Привет","type":"TEXT"}
        """.data(using: .utf8)!
        let decodedFromAndroid = try JSONDecoder().decode(BroChatMessage.self, from: androidJson)
        XCTAssertEqual(decodedFromAndroid.type, .text)

        let iosJson = """
        {"id":"ios_1","channelId":"chat_a_b","senderId":"a","senderName":"iOS","timestamp":100,"text":"Привет","type":"text"}
        """.data(using: .utf8)!
        let decodedFromIos = try JSONDecoder().decode(BroChatMessage.self, from: iosJson)
        XCTAssertEqual(decodedFromIos.type, .text)

        let unknownJson = """
        {"id":"future_1","channelId":"chat_a_b","senderId":"a","senderName":"Future","timestamp":100,"text":"Привет","type":"FUTURE_UNKNOWN"}
        """.data(using: .utf8)!
        let decodedUnknown = try JSONDecoder().decode(BroChatMessage.self, from: unknownJson)
        XCTAssertEqual(decodedUnknown.type, .text)
    }
}
