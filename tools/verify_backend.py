"""Read-only edition verification; never logs or persists administrator credentials."""

import json
import os
import sys

from google.auth.transport.requests import AuthorizedSession
from google.oauth2 import service_account


def main():
    project = os.environ.get("FIREBASE_PROJECT_ID", "liquid-chat-v2")
    raw = os.environ.get("FIREBASE_SERVICE_ACCOUNT_JSON", "")
    if not raw:
        raise SystemExit("Backend verification unavailable: repository service account secret is missing")
    info = json.loads(raw)
    if info.get("project_id") != project:
        raise SystemExit("Backend verification stopped: credential project differs from app project")
    credentials = service_account.Credentials.from_service_account_info(
        info, scopes=["https://www.googleapis.com/auth/cloud-platform.read-only"]
    )
    session = AuthorizedSession(credentials)
    response = session.get(
        f"https://firestore.googleapis.com/v1/projects/{project}/databases/(default)",
        timeout=30,
    )
    if response.status_code != 200:
        raise SystemExit(f"Database edition read failed (HTTP {response.status_code}); deployment remains unverified")
    database = response.json()
    # Firestore documents STANDARD as the default when no edition is specified.
    # https://firebase.google.com/docs/firestore/reference/rest/v1/projects.databases
    edition = database.get("databaseEdition", "STANDARD")
    result = {
        "project": project,
        "database": "(default)",
        "edition": edition,
        "type": database.get("type"),
        "location": database.get("locationId"),
        "mutations": 0,
    }
    print(json.dumps(result, sort_keys=True))
    if edition != "STANDARD" or database.get("type") != "FIRESTORE_NATIVE":
        raise SystemExit("This source targets Standard Native Firestore; review edition-specific SDK and rules before deployment")


if __name__ == "__main__":
    main()
