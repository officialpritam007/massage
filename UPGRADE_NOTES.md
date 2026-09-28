# Liquid Chat v3.2.0

## WebRTC media engine
- Added `org.webrtc:google-webrtc`.
- Added `WebRtcCallEngine` for microphone/camera capture, PeerConnection, SDP and ICE handling.
- Added Firestore ICE candidate observation support.
- Added Google STUN server configuration as a baseline.

## Important
The media engine is intentionally separated from the existing Firestore signaling layer. It requires runtime CAMERA and RECORD_AUDIO permission before starting capture and a TURN server for reliable connections across restrictive/mobile networks. The current call UI/signaling flow remains compatible; production deployment should configure TURN credentials and complete accept/reject UI wiring before claiming carrier-grade calling.

## GitHub build
Push the project to GitHub and run `.github/workflows/build-apk.yml`. Android Studio is not required.
