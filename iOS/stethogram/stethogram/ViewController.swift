//
//  ViewController.swift
//  MicrophoneAnalysis
//
//  Created by Kanstantsin Linou, revision history on Githbub.
//  Copyright © 2018 AudioKit. All rights reserved.
//

import AudioKit
import AudioKitUI
import AVFoundation
import UIKit

class ViewController: UIViewController {

    @IBOutlet private var amplitudeLabel: UILabel!
    @IBOutlet private var audioInputPlot: EZAudioPlot!
    @IBOutlet private var outputLabel: UILabel!
    
    var mic: AKMicrophone!
    //var tracker: AKFrequencyTracker!
    var tracker: AKAmplitudeTracker!
    var silence: AKBooster!
    var plot: AKNodeOutputPlot!
    var whistleRecorder: AVAudioRecorder!

    private var run = false
    private var strOutput = ""
    private var initializing = false
    private var audioSession: AVAudioSession!
    private var filename: String!
    
    @IBAction func outputSwitchAction(_ sender: UISwitch) {
        print(sender.isOn ? "output on" : "output off")
        if (sender.isOn) {
            startAudioOutput()
        } else {
            self.run = false
        }
    }
    
    @IBAction func fileSwitchAction(_ sender: UISwitch) {
        print(sender.isOn ? "file on" : "file off")
        if (sender.isOn) {
            startRecording()
        } else {
            whistleRecorder.stop()
            if (filename != nil) {
                let msg = "file " + filename + " saved"
                showToast(controller: self, message: msg, seconds: 2)
            }
        }
    }
    
    func showToast(controller: UIViewController, message : String, seconds: Double) {
        let alert = UIAlertController(title: nil, message: message, preferredStyle: .alert)
        alert.view.backgroundColor = .black
        alert.view.alpha = 0.5
        alert.view.layer.cornerRadius = 15
        
        controller.present(alert, animated: true)
        DispatchQueue.main.asyncAfter(deadline: DispatchTime.now() + seconds) {
            alert.dismiss(animated: true)
        }
    }
    
    func getWhistleURL() -> URL {
        let DocumentDirURL = try! FileManager.default.url(for: .documentDirectory, in: .userDomainMask, appropriateFor: nil, create: true)

        let today = Date()
        let formatter = DateFormatter()
        formatter.dateFormat = "y-MM-dd-HH-mm-ss"
        filename = formatter.string(from: today)
        
        return DocumentDirURL.appendingPathComponent(filename).appendingPathExtension("wav")
    }
    
    func startRecording() {
        //
        let audioURL = self.getWhistleURL()
        print(audioURL.absoluteString)

        //
        let settings = [
            AVFormatIDKey: Int(kAudioFormatLinearPCM),
            AVSampleRateKey: 44100,
            AVNumberOfChannelsKey: 1,
            AVEncoderAudioQualityKey: AVAudioQuality.high.rawValue
        ]

        do {
            //
            print("start recording")
            whistleRecorder = try AVAudioRecorder(url: audioURL, settings: settings)
            //whistleRecorder.delegate = self
            whistleRecorder.record()
        } catch {
            whistleRecorder.stop()
        }
    }
    
    func startAudioOutput() {
        if (!self.run) {
            let dispatchQueue = DispatchQueue(label: "QueueIdentification", qos: .background)
            dispatchQueue.async{
                // background thread
                do {
                    self.initializing = true
                    
                    // Configure the audio session for the app.
                    try self.audioSession.setCategory(.playAndRecord, mode: .measurement, options: .allowBluetoothA2DP)
                    try self.audioSession.overrideOutputAudioPort(AVAudioSession.PortOverride.none)
                    try self.audioSession.setActive(true)
                    
                    self.checkOutput()

                    // connect microphone input
                    let audioEngine = AVAudioEngine()
                    let micInput = audioEngine.inputNode
                    let micFormat = micInput.inputFormat(forBus: 0)
                    audioEngine.connect(micInput, to: audioEngine.mainMixerNode, format: micFormat)
                    
                    let pcm16Format = AVAudioFormat(commonFormat: .pcmFormatInt16, sampleRate: Double(44100), channels: 1, interleaved: true)
                    let formatConverter =  AVAudioConverter(from:micFormat, to: pcm16Format!)

                    // add tap to microphone input
                    micInput.installTap(onBus: 0, bufferSize: 1024, format: micFormat) {
                        (buffer: AVAudioPCMBuffer, when: AVAudioTime) in
                        // send buffer to socket
                        //client!.send(data: buffer.)
                    }
                    
                    audioEngine.prepare()
                    try audioEngine.start()
                    
                    self.run = true
                    print("background thread running ...")

                    //
                    while (self.run) {
                        usleep(1000)
                    }
                } catch {
                    print("background thread throw: \(error)")
                }
                
                print("background thread exit ...")
            } // end backgound thread
        }
    }
    
    func checkOutput() {
        // check output device
        let session = AVAudioSession.sharedInstance()
        var str = session.currentRoute.outputs[0].portName
        switch (session.currentRoute.outputs[0].portType) {
        case AVAudioSession.Port.bluetoothA2DP:
            str += " (Bluetooth)"
            break
        case AVAudioSession.Port.headphones:
            str += " (Aux)"
            break
        default:
            break
        }
        print(str)
        strOutput = str
    }
    
    func setupPlot() {
        plot = AKNodeOutputPlot(mic, frame: audioInputPlot.bounds)
        plot.translatesAutoresizingMaskIntoConstraints = false
        plot.plotType = .rolling
        plot.gain = 5.0
        plot.shouldFill = false
        plot.shouldMirror = true
        plot.color = UIColor.blue
        audioInputPlot.addSubview(plot)

        // Pin the AKNodeOutputPlot to the audioInputPlot
        var constraints = [plot.leadingAnchor.constraint(equalTo: audioInputPlot.leadingAnchor)]
        constraints.append(plot.trailingAnchor.constraint(equalTo: audioInputPlot.trailingAnchor))
        constraints.append(plot.topAnchor.constraint(equalTo: audioInputPlot.topAnchor))
        constraints.append(plot.bottomAnchor.constraint(equalTo: audioInputPlot.bottomAnchor))
        constraints.forEach { $0.isActive = true }
    }

    override func viewDidLoad() {
        super.viewDidLoad()

        AKSettings.audioInputEnabled = true
        // AKSettings.useBluetooth = true
        mic = AKMicrophone()
        //tracker = AKFrequencyTracker(mic)
        tracker = AKAmplitudeTracker(mic)
        silence = AKBooster(tracker, gain: 0)
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)

        AudioKit.output = silence
        do {
            try AudioKit.start()
        } catch {
            AKLog("AudioKit did not start!")
        }
        setupPlot()
        
        // update UI timer
        Timer.scheduledTimer(timeInterval: 0.1,
                             target: self,
                             selector: #selector(ViewController.updateUI),
                             userInfo: nil,
                             repeats: true)
        
        // initialize audio session
        audioSession = AVAudioSession.sharedInstance()
        do {
            try self.audioSession.setCategory(.playAndRecord, mode: .measurement, options: .allowBluetoothA2DP)
            try self.audioSession.overrideOutputAudioPort(AVAudioSession.PortOverride.none)
            try self.audioSession.setActive(true)
        } catch {
            print("init AVAudioSession throw: \(error)")
        }

        self.checkOutput()
        outputLabel.text = strOutput
        
        audioSession.requestRecordPermission() { [unowned self] allowed in
            DispatchQueue.main.async {
                if allowed {
                    //self.loadRecordingUI()
                } else {
                    //self.loadFailUI()
                }
            }
        }
    }

    var amplitudes = [Double](repeating: 0, count: 16)
    var counter = 0
    
    func avg() -> Double {
        var ret:Double = 0.0
        for i in 0..<16 {
            ret += amplitudes[i]
        }
        return ret/16.0
    }
    
    @objc func updateUI() {
        //amplitudes[counter & 0x0F] = tracker.amplitude
        //counter += 1
        //let amplitudesAverage = avg()
        //amplitudeLabel.text = String(format: "%0.2f / %0.2f", tracker.amplitude, amplitudesAverage)

        amplitudeLabel.text = String(format: "%0.2f", tracker.amplitude)
        
        if (self.run && self.initializing) {
            outputLabel.text = strOutput
            initializing = false
        }
    }

    // MARK: - Actions

    @IBAction func didTapInputDevicesButton(_ sender: UIBarButtonItem) {
        let inputDevices = InputDeviceTableViewController()
        inputDevices.settingsDelegate = self
        let navigationController = UINavigationController(rootViewController: inputDevices)
        navigationController.preferredContentSize = CGSize(width: 300, height: 300)
        navigationController.modalPresentationStyle = .popover
        navigationController.popoverPresentationController!.delegate = self
        self.present(navigationController, animated: true, completion: nil)
    }

}

// MARK: - UIPopoverPresentationControllerDelegate

extension ViewController: UIPopoverPresentationControllerDelegate {

    func adaptivePresentationStyle(for controller: UIPresentationController) -> UIModalPresentationStyle {
        return .none
    }

    func prepareForPopoverPresentation(_ popoverPresentationController: UIPopoverPresentationController) {
        popoverPresentationController.permittedArrowDirections = .up
        popoverPresentationController.barButtonItem = navigationItem.rightBarButtonItem
    }
}

// MARK: - InputDeviceDelegate

extension ViewController: InputDeviceDelegate {

    func didSelectInputDevice(_ device: AKDevice) {
        do {
            try mic.setDevice(device)
        } catch {
            AKLog("Error setting input device")
        }
    }

}

