const APPWRITE_ENDPOINT =
  process.env.APPWRITE_FUNCTION_API_ENDPOINT ||
  "https://sgp.cloud.appwrite.io/v1";

const PROJECT_ID = process.env.APPWRITE_FUNCTION_PROJECT_ID;
const FIREBASE_WEB_API_KEY = process.env.FIREBASE_WEB_API_KEY;

export default async ({ req, res, log, error }) => {
  try {
    // ---------------------------------------------------------
    // Basic configuration checks
    // ---------------------------------------------------------
    if (!PROJECT_ID || !FIREBASE_WEB_API_KEY) {
      error("Missing required environment variables");
      return res.json(
        {
          error: "Function is not configured"
        },
        500
      );
    }

    // ---------------------------------------------------------
    // Only POST is accepted
    // ---------------------------------------------------------
    if (req.method !== "POST") {
      return res.json(
        {
          error: "Method not allowed"
        },
        405
      );
    }

    // ---------------------------------------------------------
    // Appwrite automatically provides an ephemeral API key
    // through the x-appwrite-key header.
    // ---------------------------------------------------------
    const APPWRITE_KEY =
      req.headers["x-appwrite-key"] ||
      process.env.APPWRITE_FUNCTION_API_KEY;

    if (!APPWRITE_KEY) {
      error("Missing Appwrite Function API key");
      return res.json(
        {
          error: "Appwrite authentication is not configured"
        },
        500
      );
    }

    // ---------------------------------------------------------
    // Firebase ID token
    // Authorization: Bearer <Firebase ID Token>
    // ---------------------------------------------------------
    const authHeader =
      req.headers.authorization ||
      req.headers.Authorization ||
      "";

    if (!authHeader.startsWith("Bearer ")) {
      return res.json(
        {
          error: "Missing Firebase ID token"
        },
        401
      );
    }

    const idToken = authHeader
      .substring("Bearer ".length)
      .trim();

    if (!idToken) {
      return res.json(
        {
          error: "Invalid Firebase ID token"
        },
        401
      );
    }

    // ---------------------------------------------------------
    // Verify Firebase ID token using Firebase Identity Toolkit
    // ---------------------------------------------------------
    const verifyResponse = await fetch(
      "https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=" +
        encodeURIComponent(FIREBASE_WEB_API_KEY),
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          idToken
        })
      }
    );

    const verifyText = await verifyResponse.text();

    let verified = {};

    try {
      verified = verifyText
        ? JSON.parse(verifyText)
        : {};
    } catch (_) {
      verified = {};
    }

    if (
      !verifyResponse.ok ||
      !verified.users ||
      !Array.isArray(verified.users) ||
      verified.users.length === 0
    ) {
      log("Firebase ID token verification failed");

      return res.json(
        {
          error: "Invalid Firebase ID token"
        },
        401
      );
    }

    // ---------------------------------------------------------
    // Firebase user information
    // ---------------------------------------------------------
    const firebaseUser = verified.users[0];

    const uid = firebaseUser.localId;
    const email = firebaseUser.email || undefined;
    const name = firebaseUser.displayName || undefined;

    if (!uid) {
      return res.json(
        {
          error: "Firebase user ID is missing"
        },
        401
      );
    }

    // Firebase UIDs are used as Appwrite user IDs.
    // Appwrite user IDs allow alphanumeric characters,
    // periods, hyphens and underscores, max 36 characters.
    if (uid.length > 36) {
      return res.json(
        {
          error: "Firebase user ID is too long"
        },
        400
      );
    }

    // ---------------------------------------------------------
    // Helper for Appwrite REST API
    // ---------------------------------------------------------
    async function appwrite(path, options = {}) {
      const response = await fetch(
        `${APPWRITE_ENDPOINT}${path}`,
        {
          ...options,
          headers: {
            "Content-Type": "application/json",
            "X-Appwrite-Project": PROJECT_ID,
            "X-Appwrite-Key": APPWRITE_KEY,
            ...(options.headers || {})
          }
        }
      );

      const text = await response.text();

      let data = {};

      try {
        data = text ? JSON.parse(text) : {};
      } catch (_) {
        data = {};
      }

      if (!response.ok) {
        const message =
          data.message ||
          `Appwrite request failed: ${response.status}`;

        const appwriteError = new Error(message);
        appwriteError.status = response.status;

        throw appwriteError;
      }

      return data;
    }

    // ---------------------------------------------------------
    // Check whether Appwrite user already exists
    // ---------------------------------------------------------
    try {
      await appwrite(
        `/users/${encodeURIComponent(uid)}`
      );

      log(`Appwrite user already exists: ${uid}`);
    } catch (e) {
      // User does not exist → create it.
      if (e.status !== 404) {
        throw e;
      }

      const createUserBody = {
        userId: uid
      };

      if (email) {
        createUserBody.email = email;
      }

      if (name) {
        createUserBody.name = name;
      }

      await appwrite("/users", {
        method: "POST",
        body: JSON.stringify(createUserBody)
      });

      log(`Created Appwrite user: ${uid}`);
    }

    // ---------------------------------------------------------
    // Create short-lived Appwrite JWT
    // ---------------------------------------------------------
    // 900 seconds = 15 minutes
    const jwt = await appwrite(
      `/users/${encodeURIComponent(uid)}/jwts`,
      {
        method: "POST",
        body: JSON.stringify({
          duration: 900
        })
      }
    );

    if (!jwt || !jwt.jwt) {
      throw new Error(
        "Appwrite JWT was not generated"
      );
    }

    // ---------------------------------------------------------
    // Success
    // ---------------------------------------------------------
    return res.json({
      success: true,
      jwt: jwt.jwt,
      userId: uid,
      expiresIn: 900
    });
  } catch (e) {
    error(
      e?.message ||
        String(e)
    );

    return res.json(
      {
        error: "Appwrite authentication bridge failed"
      },
      500
    );
  }
};
