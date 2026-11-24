package com.example.webrtc

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.example.data.model.SignalSenderRole
import com.example.data.model.WebRtcSignal
import com.example.data.model.WebRtcSignalType
import com.example.service.RemoteSessionRuntimeState
import com.example.service.WebRtcTransportState
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule

class WebRtcSessionManager(
  private val context: Context,
  private val onLocalSignal: (
    signalType: WebRtcSignalType,
    sdpOrCandidate: String,
    sdpMid: String?,
    sdpMLineIndex: Int?,
  ) -> Unit,
  private val onProjectionStoppedBySystem: () -> Unit,
) {
  private var eglBase: EglBase? = null
  private var peerConnectionFactory: PeerConnectionFactory? = null
  private var peerConnection: PeerConnection? = null
  private var videoCapturer: VideoCapturer? = null
  private var surfaceTextureHelper: SurfaceTextureHelper? = null
  private var videoSource: VideoSource? = null
  private var videoTrack: VideoTrack? = null
  private var audioSource: AudioSource? = null
  private var audioTrack: AudioTrack? = null
  private var controlDataChannel: DataChannel? = null

  private val processedSignalIds = mutableSetOf<String>()
  private var isStarted = false

  fun startScreenShareAndPeer(
    mediaProjectionResultData: Intent,
    includeMicrophone: Boolean,
  ) {
    stop()
    isStarted = true
    RemoteSessionRuntimeState.update {
      it.copy(
        webRtcState = WebRtcTransportState.INITIALIZING,
        iceCandidateCount = 0,
        statusMessage = "Initializing encrypted WebRTC peer connection",
      )
    }

    try {
      ensureFactoryInitialized()
      val factory = peerConnectionFactory ?: error("PeerConnectionFactory unavailable")
      val eglContext = eglBase?.eglBaseContext ?: error("EglBase unavailable")

      val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
      )
      val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
        sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
      }

      val pc = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState?) {}

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
          val mapped = when (state) {
            PeerConnection.IceConnectionState.CONNECTED,
            PeerConnection.IceConnectionState.COMPLETED -> WebRtcTransportState.CONNECTED
            PeerConnection.IceConnectionState.FAILED -> WebRtcTransportState.ERROR
            PeerConnection.IceConnectionState.DISCONNECTED,
            PeerConnection.IceConnectionState.CLOSED -> WebRtcTransportState.STOPPED
            else -> WebRtcTransportState.OFFERING
          }
          RemoteSessionRuntimeState.update {
            it.copy(
              webRtcState = mapped,
              statusMessage = "WebRTC ICE: ${state?.name ?: "UNKNOWN"}",
            )
          }
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) {}

        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}

        override fun onIceCandidate(candidate: IceCandidate?) {
          if (candidate == null) return
          RemoteSessionRuntimeState.update {
            it.copy(iceCandidateCount = it.iceCandidateCount + 1)
          }
          onLocalSignal(
            WebRtcSignalType.ICE_CANDIDATE,
            candidate.sdp,
            candidate.sdpMid,
            candidate.sdpMLineIndex,
          )
        }

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
        override fun onAddStream(stream: MediaStream?) {}
        override fun onRemoveStream(stream: MediaStream?) {}
        override fun onDataChannel(channel: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
      }) ?: error("Failed to create PeerConnection")

      peerConnection = pc

      val dcInit = DataChannel.Init().apply { ordered = true }
      controlDataChannel = pc.createDataChannel("wink-support-control", dcInit)?.also { dc ->
        dc.registerObserver(object : DataChannel.Observer {
          override fun onBufferedAmountChange(previousAmount: Long) {}
          override fun onStateChange() {
            val open = dc.state() == DataChannel.State.OPEN
            RemoteSessionRuntimeState.update { it.copy(dataChannelOpen = open) }
          }

          override fun onMessage(buffer: DataChannel.Buffer?) {}
        })
      }

      // Attach ScreenCapturerAndroid bound to MediaProjection consent intent
      val capturer = ScreenCapturerAndroid(
        mediaProjectionResultData,
        object : MediaProjection.Callback() {
          override fun onStop() {
            Log.i(TAG, "MediaProjection stopped by system or status chip")
            onProjectionStoppedBySystem()
          }
        },
      )
      videoCapturer = capturer

      val helper = SurfaceTextureHelper.create("WinkScreenCaptureThread", eglContext)
      surfaceTextureHelper = helper
      val vSource = factory.createVideoSource(capturer.isScreencast)
      videoSource = vSource
      capturer.initialize(helper, context.applicationContext, vSource.capturerObserver)

      val (width, height) = resolveCaptureResolution()
      capturer.startCapture(width, height, 30)

      val vTrack = factory.createVideoTrack("WINK_SCREEN_VIDEO", vSource)
      vTrack.setEnabled(true)
      videoTrack = vTrack
      pc.addTrack(vTrack, listOf("WINK_MEDIA_STREAM"))

      if (includeMicrophone) {
        attachAudioTrack(factory, pc)
      }

      createAndDispatchOffer(pc)
    } catch (e: Exception) {
      Log.e(TAG, "WebRTC startup failed", e)
      RemoteSessionRuntimeState.update {
        it.copy(
          webRtcState = WebRtcTransportState.ERROR,
          statusMessage = e.localizedMessage ?: "WebRTC setup error",
        )
      }
      stop()
    }
  }

  fun startAudioOnlyPeer() {
    stop()
    isStarted = true
    try {
      ensureFactoryInitialized()
      val factory = peerConnectionFactory ?: error("PeerConnectionFactory unavailable")
      val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
      )
      val pc = factory.createPeerConnection(
        PeerConnection.RTCConfiguration(iceServers),
        object : PeerConnection.Observer {
          override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
          override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
          override fun onIceConnectionReceivingChange(p0: Boolean) {}
          override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
          override fun onIceCandidate(candidate: IceCandidate?) {
            candidate ?: return
            RemoteSessionRuntimeState.update { it.copy(iceCandidateCount = it.iceCandidateCount + 1) }
            onLocalSignal(WebRtcSignalType.ICE_CANDIDATE, candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex)
          }

          override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
          override fun onAddStream(p0: MediaStream?) {}
          override fun onRemoveStream(p0: MediaStream?) {}
          override fun onDataChannel(p0: DataChannel?) {}
          override fun onRenegotiationNeeded() {}
          override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
        },
      ) ?: error("Failed to create PeerConnection")
      peerConnection = pc
      attachAudioTrack(factory, pc)
      createAndDispatchOffer(pc)
    } catch (e: Exception) {
      Log.e(TAG, "Audio WebRTC peer failed", e)
      RemoteSessionRuntimeState.update {
        it.copy(
          webRtcState = WebRtcTransportState.ERROR,
          statusMessage = e.localizedMessage ?: "Audio WebRTC error",
        )
      }
    }
  }

  fun applyRemoteSignals(signals: List<WebRtcSignal>) {
    val pc = peerConnection ?: return
    for (signal in signals) {
      if (signal.id.isBlank() || !processedSignalIds.add(signal.id)) continue
      if (signal.parsedSenderRole != SignalSenderRole.CONTROLLER) continue

      when (signal.parsedSignalType) {
        WebRtcSignalType.ANSWER -> {
          val answer = SessionDescription(SessionDescription.Type.ANSWER, signal.sdpOrCandidate)
          pc.setRemoteDescription(SimpleSdpObserver("setRemoteDescription"), answer)
        }
        WebRtcSignalType.ICE_CANDIDATE -> {
          val mid = signal.sdpMid ?: "0"
          val mLineIndex = signal.sdpMLineIndex ?: 0
          pc.addIceCandidate(IceCandidate(mid, mLineIndex, signal.sdpOrCandidate))
        }
        WebRtcSignalType.OFFER -> Unit
      }
    }
  }

  fun setMicrophoneEnabled(enabled: Boolean) {
    audioTrack?.setEnabled(enabled)
  }

  fun stop() {
    if (!isStarted && peerConnection == null && videoCapturer == null) return
    isStarted = false
    processedSignalIds.clear()
    try {
      videoCapturer?.stopCapture()
    } catch (e: Exception) {
      Log.w(TAG, "Error stopping capturer: ${e.message}")
    }
    try {
      videoCapturer?.dispose()
    } catch (_: Exception) {
    }
    videoCapturer = null

    try {
      videoTrack?.dispose()
      videoSource?.dispose()
      audioTrack?.dispose()
      audioSource?.dispose()
      surfaceTextureHelper?.dispose()
      controlDataChannel?.dispose()
      peerConnection?.close()
      peerConnection?.dispose()
    } catch (e: Exception) {
      Log.w(TAG, "Error disposing WebRTC resources: ${e.message}")
    }
    videoTrack = null
    videoSource = null
    audioTrack = null
    audioSource = null
    surfaceTextureHelper = null
    controlDataChannel = null
    peerConnection = null
  }

  private fun ensureFactoryInitialized() {
    if (peerConnectionFactory != null) return
    val egl = EglBase.create()
    eglBase = egl
    PeerConnectionFactory.initialize(
      PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
        .setEnableInternalTracer(false)
        .createInitializationOptions()
    )
    val adm = JavaAudioDeviceModule.builder(context.applicationContext).createAudioDeviceModule()
    peerConnectionFactory = PeerConnectionFactory.builder()
      .setAudioDeviceModule(adm)
      .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
      .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
      .createPeerConnectionFactory()
    adm.release()
  }

  private fun attachAudioTrack(factory: PeerConnectionFactory, pc: PeerConnection) {
    val constraints = MediaConstraints().apply {
      mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
      mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
    }
    val aSource = factory.createAudioSource(constraints)
    val aTrack = factory.createAudioTrack("WINK_AUDIO_TRACK", aSource)
    aTrack.setEnabled(true)
    audioSource = aSource
    audioTrack = aTrack
    pc.addTrack(aTrack, listOf("WINK_MEDIA_STREAM"))
  }

  private fun createAndDispatchOffer(pc: PeerConnection) {
    val constraints = MediaConstraints().apply {
      mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
      mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
    }
    pc.createOffer(
      object : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) {
          if (sdp == null) return
          pc.setLocalDescription(SimpleSdpObserver("setLocalDescription"), sdp)
          RemoteSessionRuntimeState.update {
            it.copy(
              webRtcState = WebRtcTransportState.OFFERING,
              statusMessage = "WebRTC SDP Offer published to Firestore",
            )
          }
          onLocalSignal(WebRtcSignalType.OFFER, sdp.description, null, null)
        }

        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {
          RemoteSessionRuntimeState.update {
            it.copy(
              webRtcState = WebRtcTransportState.ERROR,
              statusMessage = "Offer failed: ${error ?: "unknown"}",
            )
          }
        }

        override fun onSetFailure(error: String?) {}
      },
      constraints,
    )
  }

  @Suppress("DEPRECATION")
  private fun resolveCaptureResolution(): Pair<Int, Int> {
    val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    val metrics = DisplayMetrics()
    wm?.defaultDisplay?.getRealMetrics(metrics)
    val rawW = metrics.widthPixels.takeIf { it > 0 } ?: 720
    val rawH = metrics.heightPixels.takeIf { it > 0 } ?: 1280
    val scale = minOf(1.0f, 1280f / maxOf(rawW, rawH))
    val w = ((rawW * scale).toInt() / 2) * 2
    val h = ((rawH * scale).toInt() / 2) * 2
    return Pair(maxOf(w, 360), maxOf(h, 640))
  }

  private open class SimpleSdpObserver(private val tag: String) : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String?) {
      Log.w(TAG, "$tag create failure: $error")
    }

    override fun onSetFailure(error: String?) {
      Log.w(TAG, "$tag set failure: $error")
    }
  }

  private companion object {
    const val TAG = "WinkWebRTC"
  }
}
