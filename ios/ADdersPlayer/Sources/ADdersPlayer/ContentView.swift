import SwiftUI
import UniformTypeIdentifiers
import AVFoundation

public struct ContentView: View {
    @StateObject private var player = ADdersAudioPlayer()
    @State private var showingFilePicker = false

    public var body: some View {
        NavigationView {
            VStack(spacing: 24) {
                // Header
                HStack {
                    Text("ADders Player")
                        .font(.largeTitle)
                        .fontWeight(.bold)
                    Spacer()
                    Text("iOS v1")
                        .font(.headline)
                        .foregroundColor(.gray)
                }
                .padding(.horizontal)

                // Current Track Info
                VStack(spacing: 8) {
                    Text(currentTrackTitle())
                        .font(.title2)
                        .lineLimit(1)
                    Text(formatTime(player.currentTime) + " / " + formatTime(player.duration))
                        .font(.subheadline)
                        .foregroundColor(.gray)

                    ProgressView(value: player.duration > 0 ? player.currentTime / player.duration : 0)
                        .accentColor(.red)
                        .padding(.horizontal)
                }
                .padding()
                .background(Color(.systemGray6))
                .cornerRadius(12)
                .padding(.horizontal)

                // Playback Controls
                HStack(spacing: 32) {
                    Button(action: { player.previous() }) {
                        Image(systemName: "backward.fill")
                            .font(.system(size: 28))
                    }
                    Button(action: {
                        if player.isPlaying { player.pause() }
                        else { player.play() }
                    }) {
                        Image(systemName: player.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                            .font(.system(size: 56))
                    }
                    Button(action: { player.next() }) {
                        Image(systemName: "forward.fill")
                            .font(.system(size: 28))
                    }
                }

                // Volume Slider (Selected Volume, never touches hardware volume)
                VStack(alignment: .leading, spacing: 8) {
                    Text("Selected Volume: \(Int(player.selectedVolume * 100))%")
                        .font(.headline)
                    Slider(value: $player.selectedVolume, in: 0...1)
                        .accentColor(.red)
                    Text("Note: iOS restricts system volume control. This slider controls app audio only.")
                        .font(.caption)
                        .foregroundColor(.gray)
                }
                .padding()
                .background(Color(.systemGray6))
                .cornerRadius(12)
                .padding(.horizontal)

                // Fade Settings
                VStack(alignment: .leading, spacing: 12) {
                    Text("Fade Settings")
                        .font(.headline)

                    HStack {
                        Text("Fade-in:")
                        Spacer()
                        Picker("Fade-in", selection: $player.fadeInSec) {
                            Text("OFF").tag(0)
                            Text("3 s").tag(3)
                            Text("5 s").tag(5)
                            Text("7 s").tag(7)
                            Text("10 s").tag(10)
                            Text("15 s").tag(15)
                        }
                        .pickerStyle(SegmentedPickerStyle())
                    }

                    HStack {
                        Text("Fade-out:")
                        Spacer()
                        Picker("Fade-out", selection: $player.fadeOutSec) {
                            Text("OFF").tag(0)
                            Text("3 s").tag(3)
                            Text("5 s").tag(5)
                            Text("7 s").tag(7)
                            Text("10 s").tag(10)
                            Text("15 s").tag(15)
                        }
                        .pickerStyle(SegmentedPickerStyle())
                    }
                }
                .padding()
                .background(Color(.systemGray6))
                .cornerRadius(12)
                .padding(.horizontal)

                // Queue & Add Files
                Button(action: { showingFilePicker = true }) {
                    Label("Add Audio Files", systemImage: "plus.circle.fill")
                        .font(.headline)
                        .padding()
                        .frame(maxWidth: .infinity)
                        .background(Color.red)
                        .foregroundColor(.white)
                        .cornerRadius(12)
                }
                .padding(.horizontal)
                .fileImporter(
                    isPresented: $showingFilePicker,
                    allowedContentTypes: [.audio],
                    allowsMultipleSelection: true
                ) { result in
                    switch result {
                    case .success(let urls):
                        let newTracks = urls.map { url -> Track in
                            let asset = AVAsset(url: url)
                            let durationSec = CMTimeGetSeconds(asset.duration)
                            return Track(url: url, title: url.lastPathComponent, duration: durationSec.isNaN ? 0 : durationSec)
                        }
                        player.addTracks(newTracks)
                    case .failure(let error):
                        print("File import failed: \(error)")
                    }
                }

                Spacer()
            }
            .navigationBarHidden(true)
        }
    }

    private func currentTrackTitle() -> String {
        guard player.currentIndex >= 0 && player.currentIndex < player.queue.count else {
            return "No track selected"
        }
        return player.queue[player.currentIndex].title
    }

    private func formatTime(_ seconds: Double) -> String {
        guard !seconds.isNaN && seconds.isFinite else { return "0:00" }
        let mins = Int(seconds) / 60
        let secs = Int(seconds) % 60
        return String(format: "%d:%02d", mins, secs)
    }
}
