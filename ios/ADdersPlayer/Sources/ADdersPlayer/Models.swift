import Foundation

// Shared clamp function - matches Android logic
public func clampFadeDuration(_ seconds: Int) -> Int {
    if seconds == 0 { return 0 }
    if seconds < 3 { return 3 }
    if seconds > 15 { return 15 }
    return seconds
}

// Fade schedule calculator (pure function)
public struct FadeSchedule {
    public let fadeInDuration: Double
    public let fadeOutDuration: Double
    public let fadeOutStartTime: Double

    public init(trackDuration: Double, fadeInSec: Int, fadeOutSec: Int) {
        let inSec = Double(clampFadeDuration(fadeInSec))
        let outSec = Double(clampFadeDuration(fadeOutSec))
        let totalFade = inSec + outSec

        if totalFade > trackDuration && trackDuration > 0 {
            // Shrink both proportionally if track is too short
            let factor = trackDuration / totalFade
            self.fadeInDuration = inSec * factor
            self.fadeOutDuration = outSec * factor
            self.fadeOutStartTime = self.fadeInDuration
        } else {
            self.fadeInDuration = inSec
            self.fadeOutDuration = outSec
            self.fadeOutStartTime = max(0, trackDuration - outSec)
        }
    }
}

public struct Track: Identifiable, Equatable {
    public let id = UUID()
    public let url: URL
    public let title: String
    public let duration: Double

    public init(url: URL, title: String, duration: Double) {
        self.url = url
        self.title = title
        self.duration = duration
    }

    public static func == (lhs: Track, rhs: Track) -> Bool {
        return lhs.id == rhs.id
    }
}
