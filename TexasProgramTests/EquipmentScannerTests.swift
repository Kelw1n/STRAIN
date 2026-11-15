import XCTest
@testable import TexasProgram

final class EquipmentScannerTests: XCTestCase {
    func testInclinePressMusclesComeFromCatalog() throws {
        let catalog = try EquipmentScanner.loadCatalog()
        let result = try EquipmentScanner.parse(
            #"{"status":"identified","candidates":["incline_press"],"observation":"Handles move forward and up.","muscles":["untrusted"]}"#,
            catalog: catalog
        )
        XCTAssertEqual(result.status, .identified)
        XCTAssertEqual(result.candidates.first?.en.primary, ["Upper chest"])
        XCTAssertEqual(result.candidates.first?.en.secondary, ["Front deltoids", "Triceps"])
    }

    func testAmbiguousPressRequestsSidePhoto() throws {
        let catalog = try EquipmentScanner.loadCatalog()
        let result = try EquipmentScanner.parse(
            #"{"status":"side_view","candidates":["incline_press","shoulder_press"],"observation":"Arm travel is not visible."}"#,
            catalog: catalog
        )
        XCTAssertEqual(result.status, .sideView)
        XCTAssertEqual(result.candidates.map(\.id), ["incline_press", "shoulder_press"])
    }

    func testUnknownIDCannotBecomeAResult() throws {
        let catalog = try EquipmentScanner.loadCatalog()
        let result = try EquipmentScanner.parse(
            #"{"status":"identified","candidates":["imaginary_model"],"observation":"Unsupported identification."}"#,
            catalog: catalog
        )
        XCTAssertEqual(result.status, .sideView)
        XCTAssertTrue(result.candidates.isEmpty)
        XCTAssertTrue(result.observation.isEmpty)
    }
}
