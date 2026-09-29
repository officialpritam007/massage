const APPWRITE_ENDPOINT = process.env.APPWRITE_FUNCTION_API_ENDPOINT || "https://sgp.cloud.appwrite.io/v1";
const PROJECT_ID = process.env.APPWRITE_FUNCTION_PROJECT_ID;
const FIREBASE_WEB_API_KEY = process.env.FIREBASE_WEB_API_KEY;
const BUCKET_ID = process.env.APPWRITE_BUCKET_ID;

function parseBody(req) {
  if (!req.body) return {};
  if (typeof req.body === "object") return req.body;
  try { return JSON.parse(req.body); } catch (_) { return {}; }
}

function getRuntimeApiKey(req) {
  // Appwrite injects an ephemeral execution API key. Depending on runtime it
  // may be exposed as a request header and/or as an environment variable.
  return req.headers?.["x-appwrite-key"] ||
    req.headers?.["X-Appwrite-Key"] ||
    process.env.APPWRITE_FUNCTION_API_KEY ||
    "";
}

async function appwrite(req, path, options = {}) {
  const apiKey = getRuntimeApiKey(req);
  if (!apiKey) {
    const error = new Error("Appwrite function API key is unavailable");
    error.status = 500;
    throw error;
  }

  const response = await fetch(`${APPWRITE_ENDPOINT}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      "X-Appwrite-Project": PROJECT_ID,
      "X-Appwrite-Key": apiKey,
      ...(options.headers || {})
    }
  });

  const text = await response.text();
  let data = {};
  try { data = text ? JSON.parse(text) : {}; } catch (_) {}

  if (!response.ok) {
    const error = new Error(data.message || `Appwrite request failed: ${response.status}`);
    error.status = response.status;
    error.details = data;
    throw error;
  }
  return data;
}

async function verifyFirebase(req) {
  if (!FIREBASE_WEB_API_KEY) {
    const error = new Error("FIREBASE_WEB_API_KEY is not configured");
    error.status = 500;
    throw error;
  }

  const auth = req.headers?.authorization || req.headers?.Authorization || "";
  if (!auth.startsWith("Bearer ")) {
    const error = new Error("Missing Firebase ID token");
    error.status = 401;
    throw error;
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
    const error = new Error("Invalid Firebase ID token");
    error.status = 401;
    throw error;
  }

  return verified.users[0];
}

async function ensureAppwriteUser(req, firebaseUser) {
  const uid = firebaseUser.localId;
  const email = firebaseUser.email || undefined;
  const name = firebaseUser.displayName || undefined;

  try {
    await appwrite(req, `/users/${encodeURIComponent(uid)}`);
  } catch (e) {
    if (e.status !== 404) throw e;
    await appwrite(req, "/users", {
      method: "POST",
      body: JSON.stringify({
        userId: uid,
        ...(email ? { email } : {}),
        ...(name ? { name } : {})
      })
    });
  }
  return uid;
}

async function createUserJwt(req, uid) {
  return appwrite(req, `/users/${encodeURIComponent(uid)}/jwts`, {
    method: "POST",
    body: JSON.stringify({ duration: 3600 })
  });
}

async function createOwnedFileToken(req, uid, fileId) {
  if (!BUCKET_ID) {
    const error = new Error("APPWRITE_BUCKET_ID is not configured");
    error.status = 500;
    throw error;
  }
  if (!fileId || typeof fileId !== "string") {
    const error = new Error("Missing fileId");
    error.status = 400;
    throw error;
  }

  const file = await appwrite(
    req,
    `/storage/buckets/${encodeURIComponent(BUCKET_ID)}/files/${encodeURIComponent(fileId)}`
  );

  const permissions = Array.isArray(file.$permissions) ? file.$permissions : [];
  const role = `user:${uid}`;
  const ownsFile = permissions.some((permission) =>
    (permission.startsWith("update(") ||
      permission.startsWith("delete(") ||
      permission.startsWith("write(")) &&
    permission.includes(role)
  );

  if (!ownsFile) {
    const error = new Error("You do not own this file");
    error.status = 403;
    throw error;
  }

  const token = await appwrite(
    req,
    `/tokens/buckets/${encodeURIComponent(BUCKET_ID)}/files/${encodeURIComponent(fileId)}`,
    { method: "POST", body: JSON.stringify({}) }
  );

  const fileUrl = `${APPWRITE_ENDPOINT}/storage/buckets/${encodeURIComponent(BUCKET_ID)}` +
    `/files/${encodeURIComponent(fileId)}/view?project=${encodeURIComponent(PROJECT_ID)}` +
    `&token=${encodeURIComponent(token.secret)}`;

  return { token: token.secret, fileUrl };
}

export default async ({ req, res, log, error }) => {
  try {
    if (!PROJECT_ID) {
      return res.json({ error: "Appwrite project context is unavailable" }, 500);
    }

    const firebaseUser = await verifyFirebase(req);
    const uid = await ensureAppwriteUser(req, firebaseUser);
    const body = parseBody(req);
    const action = body.action || "auth";

    if (action === "auth") {
      const jwt = await createUserJwt(req, uid);
      return res.json({ jwt: jwt.jwt, userId: uid, expiresIn: 3600 });
    }

    if (action === "fileToken") {
      const result = await createOwnedFileToken(req, uid, body.fileId);
      return res.json({ userId: uid, ...result });
    }

    return res.json({ error: "Unsupported action" }, 400);
  } catch (e) {
    const message = e?.message || String(e);
    error(message);
    const status = Number(e?.status) || 500;
    const safeMessage = status >= 500 ? "Appwrite authentication bridge failed" : message;
    return res.json({ error: safeMessage }, status);
  }
};
