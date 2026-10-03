"""Live, disposable-account validation. Never logs IDs, URLs or credentials."""
import json
import os
import secrets
import struct
import zlib
from pathlib import Path


def main():
    import firebase_admin
    from firebase_admin import auth, credentials, firestore
    import requests
    from google.cloud.firestore_v1 import SERVER_TIMESTAMP
    from cloud_cleanup import CloudinaryCleaner, cleanup_request, verify_upload_receipt
    from cleanup_inventory import load_policy

    policy = load_policy(Path(__file__).with_name("cleanup-policy.json"))
    with open(os.environ["GOOGLE_APPLICATION_CREDENTIALS"], encoding="utf-8") as file:
        service = json.load(file)
    if service.get("project_id") != policy["projectId"]:
        raise RuntimeError("Wrong project")
    firebase_admin.initialize_app(credentials.Certificate(service))
    db = firestore.client()
    if (db.document("runtime/cleanupInventory").get().to_dict() or {}).get("complete") is not True:
        raise RuntimeError("Inventory cutover is not complete")
    cleaner = CloudinaryCleaner(policy["cloudName"], os.environ["CLOUDINARY_API_KEY"], os.environ["CLOUDINARY_API_SECRET"], requests.Session())
    accounts = ["cleanup-smoke-" + secrets.token_hex(16) for _ in range(2)]
    for account in accounts:
        auth.create_user(uid=account)
        db.document(f"accountDeletionRequests/{account}").set({"uid": account, "status": "pending", "proofId": secrets.token_hex(32), "requestedAt": SERVER_TIMESTAMP})
        db.document(f"users/{account}").set({"uid": account, "displayName": "Cleanup validation"})
    owner, peer = accounts
    public_id = f"liquid-chat/accounts/{owner}/{secrets.token_hex(16)}"
    # A fixed 1x1 fixture; no user photograph or message is uploaded.
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 1, 1, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(b"\x00\x00\x00\x00\xff")) + chunk(b"IEND", b"")
    response = requests.post(f"https://api.cloudinary.com/v1_1/{policy['cloudName']}/image/upload",
                             data={"upload_preset": policy["ownedUploadPreset"], "public_id": public_id},
                             files={"file": ("cleanup-fixture.png", png, "image/png")}, timeout=30)
    if not response.ok:
        raise RuntimeError("Disposable upload failed")
    uploaded = response.json()
    receipt = {"assetId": uploaded["asset_id"], "publicId": uploaded["public_id"], "version": uploaded["version"],
               "resourceType": uploaded["resource_type"], "secureUrl": uploaded["secure_url"], "signature": uploaded["signature"]}
    if receipt["publicId"] != public_id:
        raise RuntimeError("Upload preset rewrote the owner namespace")
    identity = verify_upload_receipt(receipt, cleaner.secret, cleaner.cloud)
    db.document(f"users/{owner}/mediaUploads/{receipt['assetId']}").set({**receipt, "createdAt": SERVER_TIMESTAMP})
    conversation = "cleanup-smoke-" + secrets.token_hex(16)
    db.document(f"conversations/{conversation}").set({"participantIds": accounts})
    db.document(f"conversations/{conversation}/messages/fixture").set({"senderId": owner, "mediaUrl": receipt["secureUrl"]})
    cleanup_request(db, auth, db.document(f"accountDeletionRequests/{owner}").get(), cleaner, {})
    if db.document(f"users/{owner}").get().exists or db.document(f"conversations/{conversation}/messages/fixture").get().exists or cleaner.resource(identity) is not None:
        raise RuntimeError("Disposable owner data was not erased")
    try:
        auth.get_user(owner)
    except auth.UserNotFoundError:
        pass
    else:
        raise RuntimeError("Disposable Auth account was not erased")
    if not db.document(f"users/{peer}").get().exists:
        raise RuntimeError("Cleanup touched unrelated account data")
    for account in accounts:
        gate = db.document(f"accountDeletionRequests/{account}").get().to_dict()
        if gate["status"] != "revoking":
            cleanup_request(db, auth, db.document(f"accountDeletionRequests/{account}").get(), cleaner, {})
        gate = db.document(f"accountDeletionRequests/{account}").get().to_dict()
        if gate["status"] != "revoking" or not gate.get("readyAt"):
            raise RuntimeError("The stale-token deletion gate was removed early")
    db.document("runtime/cleanupSelfTest").set({"complete": True, "verifiedAt": SERVER_TIMESTAMP,
                                               "ownedUploadVerified": True, "unrelatedAccountPreserved": True})
    print("Live cleanup validation passed: scoped unsigned upload, signed receipt, media/document/Auth erasure, peer isolation and stale-token gates. Only disposable fixtures were used.")


if __name__ == "__main__":
    try:
        main()
    except Exception:
        print("::error::Disposable cleanup validation failed; no user content or credential values are logged. Pending fixture requests can be retried by the worker.")
        raise SystemExit(1)
