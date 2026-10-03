import hashlib
import sys
import types
import unittest
from unittest.mock import Mock, patch
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from cloud_cleanup import CleanupBlocked, CloudinaryCleaner, TOKEN_EXPIRY_GRACE_MS, cleanup_request, media_identity, verify_upload_receipt


SECRET = "test-secret-never-used-outside-tests"
CLOUD = "mthzgqhv"
PUBLIC_ID = "liquid-chat/accounts/alice/" + "a" * 32
URL = f"https://res.cloudinary.com/{CLOUD}/image/upload/v123/{PUBLIC_ID}.jpg"
PROOF_ID = "c" * 64


def receipt():
    return {"assetId": "asset-one", "publicId": PUBLIC_ID, "version": 123,
            "resourceType": "image", "secureUrl": URL,
            "signature": hashlib.sha1(f"public_id={PUBLIC_ID}&version=123{SECRET}".encode()).hexdigest()}


class Snapshot:
    def __init__(self, reference):
        self.reference, self.id = reference, reference.path.rsplit("/", 1)[-1]
        self.exists = reference.path in reference.db.data

    def to_dict(self):
        return dict(self.reference.db.data.get(self.reference.path, {}))


class Ref:
    def __init__(self, db, path):
        self.db, self.path = db, path

    def get(self):
        return Snapshot(self)

    def update(self, fields):
        self.db.data[self.path].update(fields)

    def set(self, fields):
        self.db.data[self.path] = dict(fields)

    def delete(self):
        self.db.data.pop(self.path, None)

    def collection(self, name):
        return Query(self.db, f"{self.path}/{name}")


class Query:
    def __init__(self, db, path, condition=None):
        self.db, self.path, self.condition = db, path, condition

    def where(self, *, filter):
        return Query(self.db, self.path, filter)

    def stream(self):
        for path, data in list(self.db.data.items()):
            if path.rsplit("/", 1)[0] != self.path:
                continue
            if self.condition:
                key, op, value = self.condition
                if op == "==" and data.get(key) != value:
                    continue
                if op == "array_contains" and value not in data.get(key, []):
                    continue
            yield self.db.document(path).get()


class Batch:
    def __init__(self, db):
        self.db, self.operations = db, []

    def set(self, ref, fields):
        self.operations.append(("set", ref, fields))

    def delete(self, ref):
        self.operations.append(("delete", ref, None))

    def commit(self):
        if self.db.fail_batch:
            raise RuntimeError("Transient batch failure")
        for action, ref, fields in self.operations:
            if action == "set":
                ref.set(fields)
            else:
                ref.delete()


class DB:
    def __init__(self, data):
        self.data = data
        self.deleted = []
        self.fail_batch = False

    def batch(self):
        return Batch(self)

    def document(self, path):
        return Ref(self, path)

    def collection(self, path):
        return Query(self, path)

    def recursive_delete(self, ref):
        self.deleted.append(ref.path)
        for path in list(self.data):
            if path == ref.path or path.startswith(ref.path + "/"):
                del self.data[path]


class Cleaner:
    cloud, secret = CLOUD, SECRET

    def __init__(self):
        self.assets = {("image", PUBLIC_ID, 123): {"asset_id": "asset-one", "version": 123}}
        self.deleted = []

    def resource(self, identity):
        return self.assets.get(identity)

    def delete(self, identity):
        self.deleted.append(identity)
        self.assets.pop(identity, None)


class CleanupTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # The worker imports privileged SDKs only when actually running. These
        # stand-ins test traversal/failure behavior without credentials or network.
        cls.saved_modules = dict(sys.modules)
        firestore = types.ModuleType("google.cloud.firestore_v1")
        firestore.SERVER_TIMESTAMP = "server-time"
        firestore.ArrayRemove = lambda values: ("remove", values)
        query = types.ModuleType("google.cloud.firestore_v1.base_query")
        query.FieldFilter = lambda field, op, value: (field, op, value)
        sys.modules[firestore.__name__] = firestore
        sys.modules[query.__name__] = query

    @classmethod
    def tearDownClass(cls):
        for name in ("google.cloud.firestore_v1", "google.cloud.firestore_v1.base_query"):
            if name in cls.saved_modules:
                sys.modules[name] = cls.saved_modules[name]
            else:
                sys.modules.pop(name, None)

    def fixture(self):
        db = DB({
            "accountDeletionRequests/alice": {"uid": "alice", "status": "pending", "proofId": PROOF_ID},
            "users/alice": {"photoUrl": URL},
            "users/alice/mediaUploads/asset-one": receipt(),
            "users/alice/devices/phone": {"token": "private"},
            "directory/alice": {"uid": "alice"},
            "conversations/pair": {"participantIds": ["alice", "bob"]},
            "conversations/pair/messages/one": {"senderId": "alice", "mediaUrl": URL},
            "conversations/pair/typing/alice": {"until": 0},
            "conversations/unrelated": {"participantIds": ["bob", "carol"]},
            "conversations/unrelated/messages/two": {"senderId": "bob"},
            "reports/one": {"reporterId": "alice", "reportedUid": "bob"},
            "reports/two": {"reporterId": "bob", "reportedUid": "alice"},
            "usernames/alice": {"uid": "alice"},
            "users/bob": {"uid": "bob"},
        })
        auth = Mock()
        auth.UserNotFoundError = type("UserNotFoundError", (Exception,), {})
        return db, auth, Cleaner()

    def run_cleanup(self, db, auth, cleaner, approvals=None):
        if approvals is None:
            approvals = {"alice": {"inventoryReviewed": True, "urls": []}}
        return cleanup_request(db, auth, db.document("accountDeletionRequests/alice").get(), cleaner, approvals)

    def test_unreviewed_orphan_inventory_never_claims_complete_erasure(self):
        db, auth, cleaner = self.fixture()
        with self.assertRaises(CleanupBlocked):
            self.run_cleanup(db, auth, cleaner, {})
        self.assertEqual([], cleaner.deleted)
        self.assertEqual([], db.deleted)
        auth.delete_user.assert_not_called()

    def test_original_media_identity_preserves_raw_extensions(self):
        self.assertEqual(("image", PUBLIC_ID, 123), media_identity(URL, CLOUD))
        self.assertEqual(("raw", "folder/doc.pdf", 1), media_identity(f"https://res.cloudinary.com/{CLOUD}/raw/upload/v1/folder/doc.pdf", CLOUD))
        for bad in (URL.replace(CLOUD, "foreign"), URL.replace("v123/", "w_200/v123/"), URL + "?token=x", URL.replace("https:", "http:"), URL.replace("accounts", "../accounts")):
            with self.assertRaises(CleanupBlocked):
                media_identity(bad, CLOUD)

    def test_signature_url_version_and_resource_type_must_all_match(self):
        self.assertEqual(("image", PUBLIC_ID, 123), verify_upload_receipt(receipt(), SECRET, CLOUD))
        for field, value in (("publicId", "someone-else"), ("version", 124), ("version", True), ("resourceType", "video"), ("signature", "0" * 40)):
            with self.assertRaises(CleanupBlocked):
                verify_upload_receipt({**receipt(), field: value}, SECRET, CLOUD)

    def test_deleted_original_with_backup_is_not_reported_as_erased(self):
        session = Mock()
        missing = Mock(status_code=404)
        deleted = Mock(ok=True)
        deleted.json.return_value = {"resources": [{"asset_id": "asset-one", "status": "deleted"}]}
        session.get.side_effect = [missing, deleted]
        with self.assertRaises(CleanupBlocked):
            CloudinaryCleaner(CLOUD, "key", SECRET, session).resource(("image", PUBLIC_ID, 123))
        session.post.assert_not_called()

    def test_unverified_legacy_media_blocks_bytes_and_document_deletion(self):
        db, auth, cleaner = self.fixture()
        db.data.pop("users/alice/mediaUploads/asset-one")
        with self.assertRaises(CleanupBlocked):
            self.run_cleanup(db, auth, cleaner)
        self.assertEqual([], cleaner.deleted)
        self.assertEqual([], db.deleted)
        auth.delete_user.assert_not_called()
        self.assertTrue(db.data["conversations/pair"]["purging"])

    def test_forged_receipt_cannot_delete_another_asset(self):
        db, auth, cleaner = self.fixture()
        db.data["users/alice/mediaUploads/asset-one"]["signature"] = "0" * 40
        with self.assertRaises(CleanupBlocked):
            self.run_cleanup(db, auth, cleaner)
        self.assertEqual([], cleaner.deleted)
        self.assertEqual([], db.deleted)

    def test_provider_asset_id_mismatch_blocks_cleanup(self):
        db, auth, cleaner = self.fixture()
        cleaner.assets[("image", PUBLIC_ID, 123)]["asset_id"] = "different-owner"
        with self.assertRaises(CleanupBlocked):
            self.run_cleanup(db, auth, cleaner)
        self.assertEqual([], cleaner.deleted)

    def test_success_deletes_nested_data_but_preserves_other_accounts(self):
        db, auth, cleaner = self.fixture()
        self.run_cleanup(db, auth, cleaner)
        self.assertEqual("revoking", db.data["accountDeletionRequests/alice"]["status"])
        self.assertEqual({"status", "readyAt"}, set(db.data[f"deletionProofs/{PROOF_ID}"]))
        self.assertFalse(any(p.startswith(("users/alice", "directory/alice", "conversations/pair", "reports/", "usernames/alice")) for p in db.data))
        self.assertIn("users/bob", db.data)
        self.assertIn("conversations/unrelated/messages/two", db.data)
        auth.delete_user.assert_called_once_with("alice")

    def test_retry_manifest_removes_orphans_after_parent_was_deleted(self):
        db, auth, cleaner = self.fixture()
        db.data["accountDeletionRequests/alice"].update(status="working", conversationIds=["orphan"])
        db.data["conversations/orphan/messages/leftover"] = {"senderId": "alice"}
        cleaner.assets.clear()  # The first attempt already removed this upload.
        self.run_cleanup(db, auth, cleaner)
        self.assertNotIn("conversations/orphan/messages/leftover", db.data)
        self.assertIn("conversations/unrelated/messages/two", db.data)

    def test_backups_block_success_until_operator_erases_them(self):
        db, auth, cleaner = self.fixture()
        cleaner.assets[("image", PUBLIC_ID, 123)]["versions"] = [{"version_id": "backup"}]
        with self.assertRaises(CleanupBlocked):
            self.run_cleanup(db, auth, cleaner)
        self.assertEqual([], db.deleted)
        self.assertEqual([], cleaner.deleted)

    def test_even_a_valid_signed_receipt_cannot_claim_another_accounts_namespace(self):
        db, auth, cleaner = self.fixture()
        foreign_id = PUBLIC_ID.replace("/alice/", "/bob/")
        foreign_url = URL.replace("/alice/", "/bob/")
        forged_owner_receipt = {**receipt(), "publicId": foreign_id, "secureUrl": foreign_url,
            "signature": hashlib.sha1(f"public_id={foreign_id}&version=123{SECRET}".encode()).hexdigest()}
        db.data["users/alice/mediaUploads/asset-one"] = forged_owner_receipt
        db.data["users/alice"]["photoUrl"] = foreign_url
        with self.assertRaises(CleanupBlocked):
            self.run_cleanup(db, auth, cleaner)
        self.assertEqual([], cleaner.deleted)
        self.assertEqual([], db.deleted)
        auth.delete_user.assert_not_called()

    def test_old_session_gate_survives_until_the_full_token_lifetime_expires(self):
        db, auth, cleaner = self.fixture()
        with patch("cloud_cleanup.time.time", return_value=1000):
            self.assertFalse(self.run_cleanup(db, auth, cleaner))
        ready_at = 1000000 + TOKEN_EXPIRY_GRACE_MS
        self.assertEqual(ready_at, db.data["accountDeletionRequests/alice"]["readyAt"])
        with patch("cloud_cleanup.time.time", return_value=(ready_at - 1) / 1000):
            self.assertFalse(self.run_cleanup(db, auth, cleaner))
        self.assertEqual("revoking", db.data["accountDeletionRequests/alice"]["status"])
        self.assertEqual("revoking", db.data[f"deletionProofs/{PROOF_ID}"]["status"])
        auth.delete_user.assert_called_once_with("alice")

    def test_final_completion_atomically_removes_uid_and_keeps_only_anonymous_proof(self):
        db, auth, cleaner = self.fixture()
        self.run_cleanup(db, auth, cleaner)
        ready_at = db.data["accountDeletionRequests/alice"]["readyAt"]
        with patch("cloud_cleanup.time.time", return_value=ready_at / 1000):
            self.assertTrue(self.run_cleanup(db, auth, cleaner))
        self.assertNotIn("accountDeletionRequests/alice", db.data)
        self.assertEqual({"status": "complete", "completedAt": "server-time"}, db.data[f"deletionProofs/{PROOF_ID}"])
        auth.delete_user.assert_called_once_with("alice")

    def test_batch_failure_cannot_publish_completion_or_remove_the_session_gate(self):
        db, auth, cleaner = self.fixture()
        self.run_cleanup(db, auth, cleaner)
        ready_at = db.data["accountDeletionRequests/alice"]["readyAt"]
        db.fail_batch = True
        with patch("cloud_cleanup.time.time", return_value=ready_at / 1000):
            with self.assertRaises(RuntimeError):
                self.run_cleanup(db, auth, cleaner)
        self.assertEqual("revoking", db.data["accountDeletionRequests/alice"]["status"])
        self.assertEqual("revoking", db.data[f"deletionProofs/{PROOF_ID}"]["status"])
        db.fail_batch = False
        with patch("cloud_cleanup.time.time", return_value=ready_at / 1000):
            self.assertTrue(self.run_cleanup(db, auth, cleaner))

    def test_invalid_capability_cannot_begin_destructive_cleanup(self):
        db, auth, cleaner = self.fixture()
        db.data["accountDeletionRequests/alice"]["proofId"] = "guessable"
        with self.assertRaises(CleanupBlocked):
            self.run_cleanup(db, auth, cleaner)
        self.assertEqual([], cleaner.deleted)
        self.assertEqual([], db.deleted)
        auth.delete_user.assert_not_called()


if __name__ == "__main__":
    unittest.main()
