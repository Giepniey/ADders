import Foundation
import AVFoundation
import MediaPlayer

public class ADdersAudioPlayer: NSObject, ObservableObject, AVAudioPlayerDelegate {
    private var player: AVAudioPlayer?
    private var fadeTimer: Timer?
    private var progressTimer: Timer?

    @Published public var queue: [Track] = []
    @Published public var currentIndex: Int = 0
    @Published public var isPlaying: Bool = false
    @Published public var currentTime: Double = 0
    @Published public var duration: Double = 0
    @Published public var selectedVolume: Float = 1.0 {
        didSet {
            UserDefaults.standard.set(selectedVolume, forKey: "selectedVolume")
            if isPlaying, let p = player, fadeTimer == nil {
                p.volume = selectedVolume
            }
        }
    }
    @Published public var fadeInSec: Int = 0 {
        didSet { UserDefaults.standard.set(fadeInSec, forKey: "fadeInSec") }
    }
    @Published public var fadeOutSec: Int = 0 {
        didSet { UserDefaults.standard.set(fadeOutSec, forKey: "fadeOutSec") }
    }

    public override init() {
        super.init()
        let defaults = UserDefaults.standard
        if defaults.object(forKey: "selectedVolume") != nil {
            self.selectedVolume = defaults.float(forKey: "selectedVolume")
        } else {
            self.selectedVolume = 1.0
        }
        self.fadeInSec = defaults.integer(forKey: "fadeInSec")
        self.fadeOutSec = defaults.integer(forKey: "fadeOutSec")

        setupAudioSession()
        setupRemoteCommands()
    }

    private func setupAudioSession() {
        #if os(iOS)
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .default, options: [])
            try session.setActive(true)
        } catch {
            print("Failed to set audio session category: \(error)")
        }
        #endif
    }

    private func setupRemoteCommands() {
        #if os(iOS)
        let commandCenter = MPRemoteCommandCenter.shared()
        commandCenter.playCommand.addTarget { [weak self] _ in
            self?.play()
            return .success
        }
        commandCenter.pauseCommand.addTarget { [weak self] _ in
            self?.pause()
            return .success
        }
        commandCenter.nextTrackCommand.addTarget { [weak self] _ in
            self?.next()
            return .success
        }
        commandCenter.previousTrackCommand.addTarget { [weak self] _ in
            self?.previous()
            return .success
        }
        #endif
    }

    public func setQueue(_ tracks: [Track], startAtIndex index: Int = 0) {
        self.queue = tracks
        self.currentIndex = index
        loadAndPlayCurrent()
    }

    public func addTracks(_ newTracks: [Track]) {
        self.queue.append(contentsOf: newTracks)
        if player == nil && !queue.isEmpty {
            currentIndex = 0
            loadAndPlayCurrent()
        }
    }

    public func loadAndPlayCurrent() {
        guard currentIndex >= 0 && currentIndex < queue.count else { return }
        let track = queue[currentIndex]
        stopFades()

        do {
            player = try AVAudioPlayer(contentsOf: track.url)
            player?.delegate = self
            duration = player?.duration ?? track.duration
            currentTime = 0
            player?.prepareToPlay()
            play()
            updateNowPlayingInfo()
        } catch {
            print("Failed to load audio file: \(error)")
        }
    }

    public func play() {
        guard let player = player else {
            if !queue.isEmpty { loadAndPlayCurrent() }
            return
        }

        cancelFade()
        let schedule = FadeSchedule(trackDuration: player.duration, fadeInSec: fadeInSec, fadeOutSec: fadeOutSec)

        if schedule.fadeInDuration > 0 {
            player.volume = 0
            player.play()
            isPlaying = true
            startFadeIn(duration: schedule.fadeInDuration)
        } else {
            player.volume = selectedVolume
            player.play()
            isPlaying = true
        }

        startProgressTimer()
        startFadeOutMonitor(schedule: schedule)
        updateNowPlayingInfo()
    }

    public func pause() {
        stopFades()
        player?.pause()
        // Volume immediately returns to selected volume on pause
        player?.volume = selectedVolume
        isPlaying = false
        stopProgressTimer()
        updateNowPlayingInfo()
    }

    public func seek(to time: Double) {
        stopFades()
        #if os(iOS)
        player?.currentTime = time
        #endif
        currentTime = time
        player?.volume = selectedVolume
        if isPlaying {
            let schedule = FadeSchedule(trackDuration: player?.duration ?? 0, fadeInSec: fadeInSec, fadeOutSec: fadeOutSec)
            startFadeOutMonitor(schedule: schedule)
        }
        updateNowPlayingInfo()
    }

    public func next() {
        if currentIndex + 1 < queue.count {
            currentIndex += 1
            loadAndPlayCurrent()
        } else {
            pause()
            seek(to: 0)
        }
    }

    public func previous() {
        if currentIndex > 0 {
            currentIndex -= 1
            loadAndPlayCurrent()
        } else {
            seek(to: 0)
        }
    }

    private func startFadeIn(duration: Double) {
        let startTime = CACurrentMediaTime()
        let startVol: Float = 0
        let targetVol = selectedVolume

        fadeTimer = Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { [weak self] timer in
            guard let self = self, let p = self.player else {
                timer.invalidate()
                return
            }
            let elapsed = CACurrentMediaTime() - startTime
            if elapsed >= duration {
                p.volume = targetVol
                timer.invalidate()
                self.fadeTimer = nil
            } else {
                let progress = Float(elapsed / duration)
                p.volume = startVol + (targetVol - startVol) * progress
            }
        }
    }

    private func startFadeOutMonitor(schedule: FadeSchedule) {
        // Monitor playback time to trigger fade out
        Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { [weak self] timer in
            guard let self = self, let p = self.player, self.isPlaying else {
                timer.invalidate()
                return
            }
            if schedule.fadeOutDuration > 0 && p.currentTime >= schedule.fadeOutStartTime {
                timer.invalidate()
                self.startFadeOut(duration: schedule.fadeOutDuration)
            }
        }
    }

    private func startFadeOut(duration: Double) {
        cancelFade()
        let startTime = CACurrentMediaTime()
        let startVol = player?.volume ?? selectedVolume
        let targetVol: Float = 0

        fadeTimer = Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { [weak self] timer in
            guard let self = self, let p = self.player else {
                timer.invalidate()
                return
            }
            let elapsed = CACurrentMediaTime() - startTime
            if elapsed >= duration {
                p.volume = targetVol
                timer.invalidate()
                self.fadeTimer = nil
            } else {
                let progress = Float(elapsed / duration)
                p.volume = startVol + (targetVol - startVol) * progress
            }
        }
    }

    private func cancelFade() {
        fadeTimer?.invalidate()
        fadeTimer = nil
    }

    private func stopFades() {
        cancelFade()
        if let p = player {
            p.volume = selectedVolume
        }
    }

    private func startProgressTimer() {
        progressTimer?.invalidate()
        progressTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in
            guard let self = self, let p = self.player else { return }
            self.currentTime = p.currentTime
        }
    }

    private func stopProgressTimer() {
        progressTimer?.invalidate()
        progressTimer = nil
    }

    private func updateNowPlayingInfo() {
        #if os(iOS)
        guard currentIndex >= 0 && currentIndex < queue.count else {
            MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
            return
        }
        let track = queue[currentIndex]
        var info = [String: Any]()
        info[MPMediaItemPropertyTitle] = track.title
        info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = currentTime
        info[MPMediaItemPropertyPlaybackDuration] = duration
        info[MPNowPlayingInfoPropertyPlaybackRate] = isPlaying ? 1.0 : 0.0
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
        #endif
    }

    public func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        if flag {
            next()
        }
    }
}
