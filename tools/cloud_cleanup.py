"""Trusted, resumable account cleanup. Never put these credentials in Android.

Runs on GitHub Actions, or any already-authorized server with ADC. Requests are
created by recently authenticated owners under strict Firestore rules. A signed
upload receipt proves possession of each new upload; legacy media requires an
explicit operator-approved URL list. No client-supplied deletion scope is trusted.
"""

from __future__ import annotations

import argparse
import hashlib
import hmac
import json
import os
import re
import time
from urllib.parse import quote, urlsplit, unquote


class CleanupBlocked(RuntimeError):
    pass


# Firebase ID tokens remain valid for up to one hour after Auth deletion. Keep
# the rules gate until that lifetime has elapsed, with a ten-minute margin.
TOKEN_EXPIRY_GRACE_MS = 70 * 60 * 1000


def media_identity(url: str, cloud: str):
    parsed = urlsplit(url)
    if parsed.scheme != "https" or parsed.hostname != "res.cloudinary.com" or parsed.query or parsed.fragment:
        raise CleanupBlocked("Legacy media requires verified provider cleanup.")
    path = unquote(parsed.path).split("/")
    if len(path) < 6 or path[1] != cloud or path[2] not in {"image", "video", "raw"} or path[3] != "upload":
        raise CleanupBlocked("Media URL is outside this Cloudinary environment.")
    # Only original, versioned upload URLs can be used as ownership evidence.
    if not re.fullmatch(r"v[0-9]+", path[4]):
        raise CleanupBlocked("Transformed media needs an operator-approved original URL.")
    public_id = "/".join(path[5:])
    if path[2] != "raw":
        public_id = public_id.rsplit(".", 1)[0]
    if not public_id or ".." in public_id.split("/"):
        raise CleanupBlocked("Invalid media identity.")
    if not public_id.isprintable():
        raise CleanupBlocked("Invalid media identity.")
    return path[2], public_id, int(path[4][1:])


def verify_upload_receipt(data: dict, secret: str, cloud: str):
    required = {"assetId", "publicId", "version", "resourceType", "secureUrl", "signature"}
    if not required <= data.keys() or not isinstance(data["version"], int) or isinstance(data["version"], bool):
        raise CleanupBlocked("An upload ownership receipt is incomplete.")
    identity = media_identity(data["secureUrl"], cloud)
    if identity != (data["resourceType"], data["publicId"], data["version"]):
        raise CleanupBlocked("Upload ownership and media URL do not match.")
    raw = f"public_id={data['publicId']}&version={data['version']}{secret}".encode()
    supplied = data["signature"]
    if not isinstance(supplied, str) or not any(hmac.compare_digest(supplied, digest(raw).hexdigest()) for digest in (hashlib.sha1, hashlib.sha256)):
        raise CleanupBlocked("An upload ownership receipt could not be verified.")
    return identity


class CloudinaryCleaner:
    def __init__(self, cloud, key, secret, session):
        if not re.fullmatch(r"[a-zA-Z0-9_-]+", cloud):
            raise CleanupBlocked("Invalid Cloudinary environment.")
        self.cloud, self.key, self.secret, self.session = cloud, key, secret, session

    def check_access(self):
        response = self.session.get(f"https://api.cloudinary.com/v1_1/{self.cloud}/resources/image", params={"max_results": 1}, auth=(self.key, self.secret), timeout=30)
        if not response.ok:
            raise CleanupBlocked("Cloudinary cleanup credentials are unavailable.")

    def resource(self, identity):
        kind, public_id, version = identity
        response = self.session.get(
            f"https://api.cloudinary.com/v1_1/{self.cloud}/resources/{kind}/upload/{quote(public_id, safe='')}",
            params={"versions": "true"}, auth=(self.key, self.secret), timeout=30,
        )
        if response.status_code == 404:
            # A missing original can still have a restorable backup. Check the
            # provider's deleted-asset index before calling it fully erased.
            expression = f'resource_type={kind} AND type=upload AND status=deleted AND public_id={json.dumps(public_id, ensure_ascii=False)}'
            deleted = self.session.get(f"https://api.cloudinary.com/v1_1/{self.cloud}/resources/search",
                params={"expression": expression, "max_results": 2}, auth=(self.key, self.secret), timeout=30)
            if not deleted.ok:
                raise CleanupBlocked("Cloudinary backup verification failed; cleanup can be retried.")
            if deleted.json().get("resources"):
                raise CleanupBlocked("A deleted Cloudinary asset still has a provider record; verify and erase its backups first.")
            return None
        if not response.ok:
            raise CleanupBlocked("Cloudinary media lookup failed; cleanup can be retried.")
        data = response.json()
        if data.get("version") != version:
            raise CleanupBlocked("A media version changed; owner verification is required.")
        return data

    def delete(self, identity):
        kind, public_id, _ = identity
        timestamp = str(int(time.time()))
        signature = hashlib.sha1(f"invalidate=true&public_id={public_id}&timestamp={timestamp}{self.secret}".encode()).hexdigest()
        response = self.session.post(
            f"https://api.cloudinary.com/v1_1/{self.cloud}/{kind}/destroy",
            data={"public_id": public_id, "timestamp": timestamp, "invalidate": "true", "api_key": self.key, "signature": signature}, timeout=30,
        )
        if not response.ok or response.json().get("result") not in {"ok", "not found"}:
            raise CleanupBlocked("Cloudinary media deletion failed; cleanup can be retried.")


def cleanup_request(db, auth, request, cleaner, approvals):
    from google.cloud.firestore_v1.base_query import FieldFilter
    from google.cloud.firestore_v1 import SERVER_TIMESTAMP

    account = request.id
    data = request.to_dict()
    proof_id = data.get("proofId", "")
    if data.get("uid") != account or data.get("status") not in {"pending", "working", "failed", "revoking"} or not re.fullmatch(r"[0-9a-f]{64}", proof_id):
        raise CleanupBlocked("Invalid deletion request.")
    proof = db.document(f"deletionProofs/{proof_id}")
    now = int(time.time() * 1000)
    if "readyAt" in data:
        if not isinstance(data["readyAt"], int) or isinstance(data["readyAt"], bool):
            raise CleanupBlocked("Invalid session expiry deadline.")
        if now < data["readyAt"]:
            proof.set({"status": "revoking", "readyAt": data["readyAt"]})
            return False
        # Completion and removal of the last UID-bearing gate are atomic. The
        # random proof has no UID and can be acknowledged without deleted Auth.
        batch = db.batch()
        batch.set(proof, {"status": "complete", "completedAt": SERVER_TIMESTAMP})
        batch.delete(request.reference)
        batch.commit()
        return True
    profile = db.document(f"users/{account}")
    # Store a server-derived manifest before deleting parent documents, so a
    # process crash cannot lose the paths of orphaned message subcollections.
    conversations = list(db.collection("conversations").where(filter=FieldFilter("participantIds", "array_contains", account)).stream())
    ids = set(data.get("conversationIds", [])) | {doc.id for doc in conversations}
    if any(not isinstance(cid, str) or "/" in cid for cid in ids):
        raise CleanupBlocked("Invalid deletion manifest.")
    request.reference.update({"status": "working", "conversationIds": sorted(ids), "updatedAt": SERVER_TIMESTAMP})
    proof.set({"status": "working"})
    for cid in ids:
        ref = db.document(f"conversations/{cid}")
        snap = ref.get()
        if snap.exists:
            if account not in snap.to_dict().get("participantIds", []):
                raise CleanupBlocked("A conversation is outside the account deletion scope.")
            ref.update({"purging": True})

    approval = approvals.get(account, {})
    if not isinstance(approval, dict) or approval.get("inventoryReviewed") is not True:
        raise CleanupBlocked("An operator must review legacy/orphaned media and backups before all-data deletion can be confirmed.")
    receipts = [doc.to_dict() for doc in profile.collection("mediaUploads").stream()]
    assets = {}
    approved = set(approval.get("urls", []))
    for receipt in receipts:
        identity = verify_upload_receipt(receipt, cleaner.secret, cleaner.cloud)
        prefix = f"liquid-chat/accounts/{account}/"
        # Unsigned duplicate uploads can return an existing asset's signed
        # response. A signature alone therefore does not bind an asset to its
        # Firebase owner. Require the owner's exact namespace and random leaf;
        # any earlier/misconfigured upload still needs explicit operator review.
        scoped = identity[1].startswith(prefix) and re.fullmatch(r"[0-9a-f]{32}(\.[A-Za-z0-9]{1,12})?", identity[1][len(prefix):])
        if not scoped and receipt["secureUrl"] not in approved:
            raise CleanupBlocked("Legacy media ownership must be reviewed before cloud deletion can finish.")
        resource = cleaner.resource(identity)
        if resource is not None and resource.get("asset_id") != receipt["assetId"]:
            raise CleanupBlocked("An upload asset identity changed.")
        if resource and (resource.get("backup") or resource.get("versions")):
            raise CleanupBlocked("Cloudinary backups require operator deletion before completing this request.")
        assets[identity] = receipt["secureUrl"]

    owned_urls = set()
    own = profile.get()
    if own.exists and own.to_dict().get("photoUrl"):
        owned_urls.add(own.to_dict()["photoUrl"])
    for cid in ids:
        for message in db.collection(f"conversations/{cid}/messages").stream():
            msg = message.to_dict()
            if msg.get("senderId") == account and msg.get("mediaUrl"):
                owned_urls.add(msg["mediaUrl"])
    known = {url for url in assets.values()}
    for url in owned_urls - known:
        if url not in approved:
            raise CleanupBlocked("Legacy media ownership must be reviewed before cloud deletion can finish.")
        identity = media_identity(url, cleaner.cloud)
        resource = cleaner.resource(identity)
        if resource and (resource.get("backup") or resource.get("versions")):
            raise CleanupBlocked("Cloudinary backups require operator deletion before completing this request.")
        assets[identity] = url

    # Media preflight completes before any bytes or message documents are erased.
    for identity in assets:
        cleaner.delete(identity)
        if cleaner.resource(identity) is not None:
            raise CleanupBlocked("Cloudinary deletion has not been verified yet.")
    for cid in ids:
        db.recursive_delete(db.document(f"conversations/{cid}"))
    for field in ("reporterId", "reportedUid"):
        for report in db.collection("reports").where(filter=FieldFilter(field, "==", account)).stream():
            db.recursive_delete(report.reference)
    for reservation in db.collection("usernames").where(filter=FieldFilter("uid", "==", account)).stream():
        reservation.reference.delete()
    from google.cloud.firestore_v1 import ArrayRemove
    for other in db.collection("users").where(filter=FieldFilter("blockedUserIds", "array_contains", account)).stream():
        other.reference.update({"blockedUserIds": ArrayRemove([account])})
    db.recursive_delete(profile)
    db.recursive_delete(db.document(f"directory/{account}"))
    try:
        auth.delete_user(account)
    except auth.UserNotFoundError:
        pass # Resume after a crash between Auth deletion and receipt publication.
    ready_at = int(time.time() * 1000) + TOKEN_EXPIRY_GRACE_MS
    request.reference.set({"uid": account, "status": "revoking", "proofId": proof_id, "readyAt": ready_at})
    proof.set({"status": "revoking", "readyAt": ready_at})
    return False


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check-only", action="store_true")
    args = parser.parse_args()
    import firebase_admin
    from firebase_admin import auth, credentials, firestore
    import requests
    from google.cloud.firestore_v1.base_query import FieldFilter
    credential_path = os.environ["GOOGLE_APPLICATION_CREDENTIALS"]
    with open(credential_path, encoding="utf-8") as file:
        service = json.load(file)
    if service.get("project_id") != os.environ["FIREBASE_PROJECT_ID"]:
        raise CleanupBlocked("The cleanup service account belongs to a different project.")
    firebase_admin.initialize_app(credentials.Certificate(service))
    db = firestore.client()
    cleaner = CloudinaryCleaner(os.environ["CLOUDINARY_CLOUD_NAME"], os.environ["CLOUDINARY_API_KEY"], os.environ["CLOUDINARY_API_SECRET"], requests.Session())
    cleaner.check_access()
    if args.check_only:
        db.document("runtime/cleanup").get()
        print("Cleanup credentials validated; no accounts were deleted.")
        return
    db.document("runtime/cleanup").set({"enabled": os.environ.get("CLEANUP_AUTOMATION_ENABLED") == "true", "updatedAt": int(time.time() * 1000)})
    approvals = json.loads(os.environ.get("LEGACY_MEDIA_APPROVALS_JSON") or "{}")
    completed = blocked = waiting = 0
    for request in db.collection("accountDeletionRequests").where(filter=FieldFilter("status", "in", ["pending", "working", "failed", "revoking"])).limit(20).stream():
        try:
            if cleanup_request(db, auth, request, cleaner, approvals):
                completed += 1
            else:
                waiting += 1
        except Exception as error:
            reason = str(error) if isinstance(error, CleanupBlocked) else "Cloud cleanup failed. Check the worker configuration and retry."
            current = request.reference.get().to_dict() or {}
            request.reference.update({"status": "revoking" if "readyAt" in current else "failed", "error": reason})
            proof_id = current.get("proofId", "")
            if isinstance(proof_id, str) and re.fullmatch(r"[0-9a-f]{64}", proof_id):
                db.document(f"deletionProofs/{proof_id}").set({"status": "failed", "error": reason})
            blocked += 1
    print(f"Account cleanup: {completed} completed, {waiting} waiting for session expiry, {blocked} require attention. No user content is logged.")
    if blocked:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
