const APPWRITE_ENDPOINT = process.env.APPWRITE_FUNCTION_API_ENDPOINT || "https://sgp.cloud.appwrite.io/v1";
const PROJECT_ID = process.env.APPWRITE_FUNCTION_PROJECT_ID;
const APPWRITE_KEY = process.env.APPWRITE_FUNCTION_API_KEY;
const FIREBASE_WEB_API_KEY = process.env.FIREBASE_WEB_API_KEY;

async function appwrite(path, options = {}) {
  const response = await fetch(`${APPWRITE_ENDPOINT}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      "X-Appwrite-Project": PROJECT_ID,
      "X-Appwrite-Key": APPWRITE_KEY,
      ...(options.headers || {})
    }
  });
  const text = await response.text();
  let data = {};
  try { data = text ? JSON.parse(text) : {}; } catch (_) {}
  if (!response.ok) {
    const error = new Error(data.message || `Appwrite request failed: ${response.status}`);
    error.status = response.status;
    throw error;
  }
  return data;
}

export default async ({ req, res, log, error }) => {
  try {
    if (!FIREBASE_WEB_API_KEY) {
      return res.json({ error: "Function is not configured" }, 500);
    }

    const auth = req.headers.authorization || req.headers.Authorization || "";
    if (!auth.startsWith("Bearer ")) {
      return res.json({ error: "Missing Firebase ID token" }, 401);
    }
    const idToken = auth.substring("Bearer ".length).trim();

    const verifyResponse = await fetch(
      `https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=${encodeURIComponent(FIREBASE_WEB_API_KEY)}`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ idToken })
      }
    );

    const verifyText = await verifyResponse.text();
    let verified = {};
    try { verified = verifyText ? JSON.parse(verifyText) : {}; } catch (_) {}

    if (!verifyResponse.ok || !verified.users || !verified.users.length) {
      return res.json({ error: "Invalid Firebase ID token" }, 401);
    }

    const firebaseUser = verified.users[0];
    const uid = firebaseUser.localId;
    const email = firebaseUser.email || undefined;
    const name = firebaseUser.displayName || undefined;

    // Mirror the Firebase UID as the Appwrite user ID. This lets file permissions
    // use the same identifier on both sides without exposing an Appwrite API key.
    try {
      await appwrite(`/users/${encodeURIComponent(uid)}`);
    } catch (e) {
      if (e.status !== 404) throw e;
      await appwrite(`/users`, {
        method: "POST",
        body: JSON.stringify({
          userId: uid,
          ...(email ? { email } : {}),
          ...(name ? { name } : {})
        })
      });
    }

    const jwt = await appwrite(`/users/${encodeURIComponent(uid)}/jwts`, {
      method: "POST",
      body: JSON.stringify({ duration: 3600 })
    });

    return res.json({
      jwt: jwt.jwt,
      userId: uid,
      expiresIn: 3600
    });
  } catch (e) {
    error(e.message || String(e));
    return res.json({ error: "Appwrite authentication bridge failed" }, 500);
  }
};
