"""Read-only deployment diagnostics. Output counts and configuration flags only.

Never log owner IDs, emails, media URLs, tokens, credentials or provider bodies.
This module cannot accept deletion requests, enable cleanup or remove any data.
"""

from __future__ import annotations

import json
import time
from urllib.parse import quote


def _read_json(session, url, **kwargs):
    response = session.get(url, timeout=30, **kwargs)
    if not response.ok:
        return None, response.status_code
    return response.json(), response.status_code


def audit_readiness(db, auth, cleaner, credential, project, approvals, automation_enabled, preset):
    from google.auth.transport.requests import AuthorizedSession

    report = {"readOnly": True, "automationConfigured": automation_enabled}
    runtime = db.document("runtime/cleanup").get().to_dict() or {}
    age = int(time.time() * 1000) - runtime.get("updatedAt", 0)
    report["runtime"] = {"enabled": runtime.get("enabled") is True, "heartbeatFresh": 0 <= age <= 3_600_000}

    approved = {uid for uid, value in approvals.items() if isinstance(value, dict) and value.get("inventoryReviewed") is True}
    account_count = reviewed_count = 0
    for user in auth.list_users().iterate_all():
        account_count += 1
        reviewed_count += user.uid in approved
    report["accounts"] = {"authTotal": account_count, "inventoryReviewed": reviewed_count,
                          "inventoryUnreviewed": account_count - reviewed_count}
    for collection, label in (("users", "profiles"), ("accountDeletionRequests", "deletionRequests")):
        report[label] = db.collection(collection).count().get()[0][0].value
    report["uploadReceipts"] = db.collection_group("mediaUploads").count().get()[0][0].value

    cloud_root = f"https://api.cloudinary.com/v1_1/{cleaner.cloud}"
    cloud_auth = (cleaner.key, cleaner.secret)
    config, config_status = _read_json(cleaner.session, f"{cloud_root}/config", params={"settings": "true"}, auth=cloud_auth)
    folder_mode = (config or {}).get("settings", {}).get("folder_mode")
    if folder_mode not in {"dynamic", "fixed"}:
        folder_mode = "unknown"
    upload, preset_status = _read_json(cleaner.session, f"{cloud_root}/upload_presets/{quote(preset, safe='')}", auth=cloud_auth)
    settings = (upload or {}).get("settings", {})
    report["cloudinary"] = {
        "configReadStatus": config_status, "presetReadStatus": preset_status,
        "folderMode": folder_mode, "unsignedPreset": (upload or {}).get("unsigned") is True,
        "publicIdAllowed": upload is not None and settings.get("disallow_public_id") is not True,
        "publicIdPrefixConfigured": bool(settings.get("public_id_prefix")),
        "assetFolderPrefixesPublicId": settings.get("use_asset_folder_as_public_id_prefix") is True,
        "fixedFolderPrefixesPublicId": folder_mode != "dynamic" and bool(settings.get("folder")),
        "presetBackupEnabled": settings.get("backup") is True,
    }

    google_session = AuthorizedSession(credential.get_credential().with_scopes(["https://www.googleapis.com/auth/cloud-platform"]))
    database_name = f"projects/{project}/databases/(default)"
    database, database_status = _read_json(google_session, f"https://firestore.googleapis.com/v1/{database_name}")
    recovery = (database or {}).get("pointInTimeRecoveryEnablement")
    if recovery not in {"POINT_IN_TIME_RECOVERY_ENABLED", "POINT_IN_TIME_RECOVERY_DISABLED"}:
        recovery = "unknown"
    backups, backup_status = _read_json(google_session, f"https://firestore.googleapis.com/v1/projects/{project}/locations/-/backups")
    report["firestore"] = {
        "databaseReadStatus": database_status, "pointInTimeRecovery": recovery,
        "backupsReadStatus": backup_status,
        "managedBackupsForDatabase": None if backups is None else sum(item.get("database") == database_name for item in backups.get("backups", [])),
        "backupRegionsUnreachable": len((backups or {}).get("unreachable", [])),
    }
    permissions = ["datastore.entities.get", "datastore.entities.list", "datastore.entities.create",
                   "datastore.entities.update", "datastore.entities.delete", "firebaseauth.users.get", "firebaseauth.users.delete"]
    permission_response = google_session.post(f"https://cloudresourcemanager.googleapis.com/v3/projects/{project}:testIamPermissions",
                                              json={"permissions": permissions}, timeout=30)
    granted = set(permission_response.json().get("permissions", [])) if permission_response.ok else set()
    report["permissions"] = {"readStatus": permission_response.status_code,
                             "firestoreDeleteGranted": "datastore.entities.delete" in granted,
                             "authDeleteGranted": "firebaseauth.users.delete" in granted,
                             "requiredPermissionsMissing": len(set(permissions) - granted) if permission_response.ok else None}
    # Hand-managed exports and untracked old provider data cannot be inferred
    # from API settings. Their review remains an explicit operator prerequisite.
    report["independentExportsReviewed"] = False
    print("Cleanup readiness audit: " + json.dumps(report, sort_keys=True))
    print("Read-only audit finished; no accounts, media, settings or heartbeat were changed.")
