# Cloudinary direct-upload setup — zero paid backend

Liquid Chat 4.2.0 uses Firebase Auth + Cloud Firestore + Cloudinary direct unsigned uploads.

## Create the unsigned upload preset

In Cloudinary Console:

1. Open **Settings → Upload → Upload presets**.
2. Create a new preset.
3. Set **Signing mode = Unsigned**.
4. Set preset name to **`liquid_chat_unsigned`**.
5. Set the asset folder to **`liquid-chat`**.
6. Restrict allowed formats. Recommended:
   - images: jpg, jpeg, png, webp
   - optional voice/video: m4a, aac, mp4
7. Keep paid/expensive add-ons, AI analysis and eager transformations disabled.

Connected cloud name: **`mthzgqhv`**.

## GitHub variables

Optional repository variables:

- `CLOUDINARY_CLOUD_NAME=mthzgqhv`
- `CLOUDINARY_UPLOAD_PRESET=liquid_chat_unsigned`

These are public identifiers, not secrets.

## Never add the API secret

Do not put the Cloudinary API secret, Firebase service-account JSON, or private release-key passwords in the APK or public repository.

## Runtime flow

1. User signs in with Firebase Auth.
2. User/profile/chat/message data is written to Cloud Firestore.
3. Android uploads media directly to Cloudinary.
4. Cloudinary returns `secure_url`.
5. That URL is saved in the Firestore message/profile document.
6. Other participants read the Firestore document and load the Cloudinary URL.

## Privacy limitation

Firestore controls who may read a message document, but the Cloudinary delivery URL itself is public-by-URL. Direct unsigned mode also cannot securely issue Cloudinary Admin deletion calls from the APK.
