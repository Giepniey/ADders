import XCTest

@testable import ADdersPlayer

final class TestModels: XCTestCase {
    func testClampFadeDurationZero() {
        XCTAssertEqual(clampFadeDuration(0), 0)
    }

    func testClampFadeDurationBelowMinimum() {
        XCTAssertEqual(clampFadeDuration(1), 3)
        XCTAssertEqual(clampFadeDuration(2), 3)
        XCTAssertEqual(clampFadeDuration(-5), 3)
    }

    func testClampFadeDurationValidRange() {
        XCTAssertEqual(clampFadeDuration(3), 3)
        XCTAssertEqual(clampFadeDuration(7), 7)
        XCTAssertEqual(clampFadeDuration(15), 15)
    }

    func testClampFadeDurationAboveMaximum() {
        XCTAssertEqual(clampFadeDuration(20), 15)
        XCTAssertEqual(clampFadeDuration(100), 15)
    }

    // MARK: - Fade Schedule Tests

    func testFadeScheduleNormalDuration() {
        let schedule = FadeSchedule(trackDuration: 60, fadeInSec: 3, fadeOutSec: 5)
        XCTAssertEqual(schedule.fadeInDuration, 3, accuracy: 0.001)
        XCTAssertEqual(schedule.fadeOutDuration, 5, accuracy: 0.001)
        XCTAssertEqual(schedule.fadeOutStartTime, 55, accuracy: 0.001)
    }

    func testFadeScheduleShortTrackShrink() {
        let schedule = FadeSchedule(trackDuration: 5, fadeInSec: 3, fadeOutSec: 5)
        // Total fade 8s > track 5s, should shrink proportionally
        XCTAssertEqual(schedule.fadeInDuration + schedule.fadeOutDuration, 5, accuracy: 0.001)
        XCTAssertTrue(schedule.fadeInDuration > 0)
        XCTAssertTrue(schedule.fadeOutDuration > 0)
    }

    func testFadeScheduleOffDurations() {
        let schedule = FadeSchedule(trackDuration: 60, fadeInSec: 0, fadeOutSec: 0)
        XCTAssertEqual(schedule.fadeInDuration, 0, accuracy: 0.001)
        XCTAssertEqual(schedule.fadeOutDuration, 0, accuracy: 0.001)
    }

    func testFadeScheduleFadeInOnly() {
        let schedule = FadeSchedule(trackDuration: 60, fadeInSec: 5, fadeOutSec: 0)
        XCTAssertEqual(schedule.fadeInDuration, 5, accuracy: 0.001)
        XCTAssertEqual(schedule.fadeOutDuration, 0, accuracy: 0.001)
    }

    func testFadeScheduleFadeOutOnly() {
        let schedule = FadeSchedule(trackDuration: 60, fadeInSec: 0, fadeOutSec: 5)
        XCTAssertEqual(schedule.fadeInDuration, 0, accuracy: 0.001)
        XCTAssertEqual(schedule.fadeOutDuration, 5, accuracy: 0.001)
        XCTAssertEqual(schedule.fadeOutStartTime, 55, accuracy: 0.001)
    }
}
