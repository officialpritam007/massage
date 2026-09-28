package com.example.data.calls

import android.content.Context
import android.view.ViewGroup
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thin WebRTC media engine. Signaling remains in FirestoreCallSignaling; this class
 * only handles microphone/camera capture, PeerConnection, SDP and ICE candidates.
 */
class WebRtcCallEngine(private val context: Context) {
    companion object {
        private val initialized = AtomicBoolean(false)
        private fun initializeFactory(context: Context) {
            if (initialized.compareAndSet(false, true)) {
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                        .setEnableInternalTracer(false)
                        .createInitializationOptions()
                )
            }
        }
    }

    private val eglBase = EglBase.create()
    private val factory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var videoSource: VideoSource? = null
    private var cameraCapturer: CameraVideoCapturer? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null

    val localRenderer: SurfaceViewRenderer = SurfaceViewRenderer(context)
    val remoteRenderer: SurfaceViewRenderer = SurfaceViewRenderer(context)

    var onLocalIceCandidate: ((IceCandidate) -> Unit)? = null
    var onRemoteTrack: ((VideoTrack) -> Unit)? = null
    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null

    init {
        initializeFactory(context)
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
        localRenderer.init(eglBase.eglBaseContext, null)
        remoteRenderer.init(eglBase.eglBaseContext, null)
        localRenderer.setMirror(true)
        remoteRenderer.setMirror(false)
        localRenderer.layoutParams = ViewGroup.LayoutParams(-1, -1)
        remoteRenderer.layoutParams = ViewGroup.LayoutParams(-1, -1)
    }

    fun start(callType: String, isInitiator: Boolean, onOffer: (SessionDescription) -> Unit, onAnswer: (SessionDescription) -> Unit) {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        peerConnection = factory.createPeerConnection(config, observer())
        requireNotNull(peerConnection) { "Unable to create WebRTC PeerConnection" }

        createLocalTracks(callType)
        localAudioTrack?.let { peerConnection?.addTrack(it, listOf("audio")) }
        if (callType == "video") localVideoTrack?.let { peerConnection?.addTrack(it, listOf("video")) }

        if (isInitiator) {
            peerConnection?.createOffer(object : SdpObserverAdapter() {
                override fun onCreateSuccess(desc: SessionDescription) {
                    peerConnection?.setLocalDescription(SdpObserverAdapter(), desc)
                    onOffer(desc)
                }
            }, MediaConstraints())
        }
    }

    fun acceptOffer(offer: SessionDescription, onAnswer: (SessionDescription) -> Unit) {
        peerConnection?.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() {
                peerConnection?.createAnswer(object : SdpObserverAdapter() {
                    override fun onCreateSuccess(desc: SessionDescription) {
                        peerConnection?.setLocalDescription(SdpObserverAdapter(), desc)
                        onAnswer(desc)
                    }
                }, MediaConstraints())
            }
        }, offer)
    }

    fun setRemoteAnswer(answer: SessionDescription) {
        peerConnection?.setRemoteDescription(SdpObserverAdapter(), answer)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    fun setMicrophoneEnabled(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
    }

    fun setCameraEnabled(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
    }

    fun setSpeakerEnabled(enabled: Boolean) {
        // Audio routing is owned by Android AudioManager; the media track stays unchanged.
    }

    fun switchCamera() {
        cameraCapturer?.switchCamera(null)
    }

    fun release() {
        cameraCapturer?.stopCaptureSafely()
        cameraCapturer?.dispose()
        cameraCapturer = null
        localAudioTrack?.dispose()
        localVideoTrack?.dispose()
        audioSource?.dispose()
        videoSource?.dispose()
        peerConnection?.dispose()
        peerConnection = null
        localRenderer.release()
        remoteRenderer.release()
        eglBase.release()
        factory.dispose()
    }

    private fun createLocalTracks(callType: String) {
        val audioConstraints = MediaConstraints()
        audioSource = factory.createAudioSource(audioConstraints)
        localAudioTrack = factory.createAudioTrack("LC_AUDIO", audioSource)

        if (callType != "video") return
        val enumerator = Camera2Enumerator(context)
        val deviceName = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
            ?: enumerator.deviceNames.firstOrNull()
            ?: return
        cameraCapturer = enumerator.createCapturer(deviceName, null)
        videoSource = factory.createVideoSource(cameraCapturer!!.isScreencast)
        cameraCapturer!!.initialize(
            SurfaceTextureHelperHolder.get(context, eglBase),
            context,
            videoSource!!.capturerObserver
        )
        cameraCapturer!!.startCapture(1280, 720, 30)
        localVideoTrack = factory.createVideoTrack("LC_VIDEO", videoSource)
        localVideoTrack?.addSink(localRenderer)
    }

    private fun observer() = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) { onLocalIceCandidate?.invoke(candidate) }
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
            when (newState) {
                PeerConnection.PeerConnectionState.CONNECTED -> onConnected?.invoke()
                PeerConnection.PeerConnectionState.DISCONNECTED,
                PeerConnection.PeerConnectionState.FAILED,
                PeerConnection.PeerConnectionState.CLOSED -> onDisconnected?.invoke()
                else -> Unit
            }
        }
        override fun onTrack(transceiver: RtpTransceiver) {
            val track = transceiver.receiver.track()
            if (track is VideoTrack) {
                remoteVideoTrack = track
                track.addSink(remoteRenderer)
                onRemoteTrack?.invoke(track)
            }
        }
        override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) = Unit
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
        override fun onAddStream(stream: org.webrtc.MediaStream) = Unit
        override fun onRemoveStream(stream: org.webrtc.MediaStream) = Unit
        override fun onDataChannel(channel: org.webrtc.DataChannel) = Unit
        override fun onRenegotiationNeeded() = Unit
        override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out org.webrtc.MediaStream>) = Unit
    }

    private open class SdpObserverAdapter : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String) = Unit
        override fun onSetFailure(error: String) = Unit
    }

    private object SurfaceTextureHelperHolder {
        private var helper: org.webrtc.SurfaceTextureHelper? = null
        fun get(context: Context, eglBase: EglBase): org.webrtc.SurfaceTextureHelper {
            return helper ?: org.webrtc.SurfaceTextureHelper.create("LiquidChat-Camera", eglBase.eglBaseContext).also { helper = it }
        }
    }
}

private fun CameraVideoCapturer.stopCaptureSafely() {
    runCatching { stopCapture() }
}
