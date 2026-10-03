"""Owner-authorized, one-time inventory cutover; no credentials/user content logged.

The trusted completion certificate replaces per-account manual reviews only for
Auth accounts created after the clean cutover. Existing users are erased with
the same 70-minute stale-token gate as normal permanent account deletion.
"""

from __future__ import annotations

import json
import re
import secrets
import time
from urllib.parse import quote

from cloud_cleanup import CleanupBlocked, TOKEN_EXPIRY_GRACE_MS, media_identity


USER_COLLECTIONS = {"users", "directory", "usernames", "conversations", "reports",
                    "_rate", "_sessions", "media", "messageTombstones"}
ADMIN_COLLECTIONS = {"runtime", "accountDeletionRequests", "deletionProofs"}
USER_REFERENCE_FIELDS = {"uid", "userId", "ownerId", "senderId", "recipientId", "participantIds",
                         "conversationId", "messageId", "assetId", "fileId", "mediaUrl", "sessionId"}


def reset_collections(db, accounts):
    """Recognize old account/message/media registries without deleting config."""
    names = set(USER_COLLECTIONS)
    for ref in db.collections():
        if ref.id in names | ADMIN_COLLECTIONS:
            continue
        documents = list(ref.stream())
        if ref.id in accounts or (documents and all(
            doc.id in accounts or bool(USER_REFERENCE_FIELDS & (doc.to_dict() or {}).keys())
            for doc in documents
        )):
            names.add(ref.id)
        else:
            raise CleanupBlocked("An unreviewed database collection prevents an automatic inventory reset.")
    return names


def load_policy(path):
    with open(path, encoding="utf-8") as file:
        policy = json.load(file)
    if policy.get("projectId") != "liquid-chat-v2" or policy.get("cloudName") != "mthzgqhv":
        raise CleanupBlocked("The cleanup policy is outside the authorized project.")
    return policy


def verify_database_backups(session, project):
    name = f"projects/{project}/databases/(default)"
    config = session.get(f"https://firestore.googleapis.com/v1/{name}", timeout=30)
    backups = session.get(f"https://firestore.googleapis.com/v1/projects/{project}/locations/-/backups", timeout=30)
    if not config.ok or not backups.ok:
        raise CleanupBlocked("Database backup settings could not be verified.")
    if config.json().get("pointInTimeRecoveryEnablement") != "POINT_IN_TIME_RECOVERY_DISABLED":
        raise CleanupBlocked("Database recovery history must be disabled and reviewed before permanent deletion.")
    data = backups.json()
    if data.get("unreachable") or any(item.get("database") == name for item in data.get("backups", [])):
        raise CleanupBlocked("Database backups require cleanup before permanent deletion.")


def set_preset(cleaner, name, unsigned, *, create_owned=False):
    if not re.fullmatch(r"[A-Za-z0-9_-]+", name):
        raise CleanupBlocked("Invalid upload preset.")
    root = f"https://api.cloudinary.com/v1_1/{cleaner.cloud}/upload_presets"
    auth = (cleaner.key, cleaner.secret)
    url = f"{root}/{quote(name, safe='')}"
    current = cleaner.session.get(url, auth=auth, timeout=30)
    fields = {"unsigned": str(unsigned).lower()}
    if create_owned:
        fields.update({"disallow_public_id": "false", "use_filename": "false", "backup": "false",
                       "asset_folder": "liquid-chat", "use_asset_folder_as_public_id_prefix": "false",
                       "max_file_size": str(9 * 1024 * 1024)})
    if current.status_code == 404 and create_owned:
        response = cleaner.session.post(root, data={"name": name, **fields}, auth=auth, timeout=30)
    elif current.ok:
        response = cleaner.session.put(url, data=fields, auth=auth, timeout=30)
    else:
        raise CleanupBlocked("Upload preset configuration is unavailable.")
    if not response.ok:
        raise CleanupBlocked("Upload preset configuration could not be saved.")
    verify = cleaner.session.get(url, auth=auth, timeout=30)
    if not verify.ok or verify.json().get("unsigned") is not unsigned:
        raise CleanupBlocked("Upload preset configuration was not verified.")
    if create_owned:
        settings = verify.json().get("settings", {})
        if settings.get("disallow_public_id") or settings.get("public_id_prefix") or settings.get("use_asset_folder_as_public_id_prefix") or settings.get("backup"):
            raise CleanupBlocked("The owned upload preset changes ownership paths or keeps backups.")


def _list_resources(cleaner, path, params):
    cursor = None
    seen = set()
    while True:
        query = {"max_results": 500, **params}
        if cursor:
            query["next_cursor"] = cursor
        response = cleaner.session.get(f"https://api.cloudinary.com/v1_1/{cleaner.cloud}/{path}",
                                       params=query, auth=(cleaner.key, cleaner.secret), timeout=30)
        if not response.ok:
            raise CleanupBlocked("Cloudinary inventory could not be verified.")
        data = response.json()
        yield from data.get("resources", [])
        cursor = data.get("next_cursor")
        if not cursor:
            return
        if cursor in seen:
            raise CleanupBlocked("Cloudinary inventory pagination did not finish.")
        seen.add(cursor)


def app_resources(cleaner):
    found = {item["asset_id"]: item for item in _list_resources(cleaner, "resources/by_asset_folder", {"asset_folder": "liquid-chat"})}
    for kind in ("image", "video", "raw"):
        for item in _list_resources(cleaner, f"resources/{kind}/upload", {"prefix": "liquid-chat/"}):
            found[item["asset_id"]] = item
    return list(found.values())


def initialize_inventory(db, auth, cleaner, google_session, policy):
    from google.cloud.firestore_v1 import SERVER_TIMESTAMP

    if policy.get("ownerAuthorizedExistingUserReset") is not True or policy.get("ownerConfirmedNoExports") is not True:
        raise CleanupBlocked("The owner has not authorized the existing user reset and export review.")
    if policy.get("projectId") != "liquid-chat-v2" or policy.get("cloudName") != cleaner.cloud or cleaner.cloud != "mthzgqhv":
        raise CleanupBlocked("The reset is outside the authorized project.")
    certificate = db.document("runtime/cleanupInventory")
    existing = certificate.get().to_dict() or {}
    if existing.get("complete") is True:
        if existing.get("migrationId") != policy["migrationId"]:
            raise CleanupBlocked("An existing inventory certificate cannot be replaced by another reset.")
        print("Inventory cutover already verified; existing user data was not reset again.")
        return
    verify_database_backups(google_session, policy["projectId"])
    started_at = existing.get("startedAt") or int(time.time() * 1000)
    accounts = {user.uid for user in auth.list_users().iterate_all()}
    for collection in ("users", "directory", "accountDeletionRequests"):
        accounts.update(ref.id for ref in db.collection(collection).list_documents())
    user_collections = reset_collections(db, accounts)
    if len(accounts) > 200:
        raise CleanupBlocked("This reset requires a reviewed batch plan for the larger account inventory.")
    # Disable old unsigned clients first. The replacement preset remains signed
    # until the old inventory has been erased and verified.
    set_preset(cleaner, policy["oldUploadPreset"], False)
    set_preset(cleaner, policy["ownedUploadPreset"], False, create_owned=True)
    assets = app_resources(cleaner)
    identities = []
    for asset in assets:
        identity = media_identity(asset["secure_url"], cleaner.cloud)
        verified = cleaner.resource(identity)
        if verified is None or verified.get("asset_id") != asset["asset_id"]:
            raise CleanupBlocked("The app upload inventory changed before reset.")
        if verified.get("backup") or verified.get("versions"):
            raise CleanupBlocked("Provider backups must be erased before this reset.")
        identities.append(identity)
    if list(_list_resources(cleaner, "resources/search", {"expression": "status=deleted"})):
        raise CleanupBlocked("Deleted provider records need a backup review before reset.")
    batch = db.batch()
    batch.set(certificate, {"migrationId": policy["migrationId"], "startedAt": started_at, "complete": False})
    for account in accounts:
        ref = db.document(f"accountDeletionRequests/{account}")
        previous = ref.get().to_dict() or {}
        proof_id = previous.get("proofId") or secrets.token_hex(32)
        if not re.fullmatch(r"[0-9a-f]{64}", proof_id):
            raise CleanupBlocked("An existing deletion request has no valid completion proof.")
        batch.set(ref, {"uid": account, "status": "working", "proofId": proof_id, "requestedAt": SERVER_TIMESTAMP})
    batch.commit()
    # The UID gates are committed before media, documents or Auth are erased.
    for identity in identities:
        cleaner.delete(identity)
        if cleaner.resource(identity) is not None:
            raise CleanupBlocked("Provider deletion is not verified yet.")
    for name in user_collections:
        db.recursive_delete(db.collection(name))
    for account in accounts:
        try:
            auth.delete_user(account)
        except auth.UserNotFoundError:
            pass
    if app_resources(cleaner) or any(next(db.collection(name).list_documents(), None) is not None for name in user_collections):
        raise CleanupBlocked("The old inventory is not empty; initialization can be retried.")
    # Resuming after a crash keeps the original pending gates. A later completion
    # never needs a UID list in the permanent inventory certificate.
    ready_at = int(time.time() * 1000) + TOKEN_EXPIRY_GRACE_MS
    batch = db.batch()
    for account in accounts:
        ref = db.document(f"accountDeletionRequests/{account}")
        data = ref.get().to_dict()
        batch.set(ref, {"uid": account, "status": "revoking", "proofId": data["proofId"], "readyAt": ready_at})
        batch.set(db.document(f"deletionProofs/{data['proofId']}"), {"status": "revoking", "readyAt": ready_at})
    batch.commit()
    set_preset(cleaner, policy["ownedUploadPreset"], True, create_owned=True)
    certificate.set({"migrationId": policy["migrationId"], "startedAt": started_at, "complete": True,
                     "ownedPreset": policy["ownedUploadPreset"], "noExportsConfirmed": True,
                     "verifiedAt": SERVER_TIMESTAMP})
    print(f"Authorized inventory cutover verified: {len(accounts)} accounts reset, {len(identities)} app uploads erased. Old-token gates remain for 70 minutes.")
