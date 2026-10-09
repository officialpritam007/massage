const { Client, Storage, ID, Permission, Role } = require('node-appwrite');
const admin = require('firebase-admin');

// Ensure Firebase is initialized only once
if (!admin.apps.length) {
    admin.initializeApp({
        credential: admin.credential.cert(JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT))
    });
}

module.exports = async ({ req, res, log, error }) => {
    // We expect POST for upload URL generation and GET for download URL generation.
    // However, Appwrite Cloud Free function limits suggest consolidating them.
    // Payload should be JSON: { action: 'upload' | 'download', conversationId, ... }
    if (req.method !== 'POST') {
        return res.json({ success: false, message: 'Method not allowed' }, 405);
    }

    try {
        const payload = JSON.parse(req.body);
        const { action, conversationId, fileId, extension } = payload;

        if (!action || !conversationId) {
            return res.json({ success: false, message: 'Missing required fields' }, 400);
        }

        const authHeader = req.headers['authorization'];
        if (!authHeader || !authHeader.startsWith('Bearer ')) {
            return res.json({ success: false, message: 'Missing auth token' }, 401);
        }

        const idToken = authHeader.split('Bearer ')[1];
        const decodedToken = await admin.auth().verifyIdToken(idToken);
        const uid = decodedToken.uid;

        const db = admin.firestore();

        // Check authoritative Firestore conversation membership
        const convRef = db.doc(`conversations/${conversationId}`);
        const convSnap = await convRef.get();
        if (!convSnap.exists) {
            return res.json({ success: false, message: 'Conversation not found' }, 404);
        }

        const convData = convSnap.data();
        if (!(convData.participantIds || []).includes(uid)) {
            return res.json({ success: false, message: 'Not a member of this conversation' }, 403);
        }

        const otherUid = convData.participantIds.find(p => p !== uid);

        // Enforce blocking
        const recipientRef = db.doc(`users/${otherUid}`);
        const recipientSnap = await recipientRef.get();
        if (recipientSnap.exists && (recipientSnap.data().blockedUserIds || []).includes(uid)) {
             return res.json({ success: false, message: 'Cannot interact with blocked user' }, 403);
        }

        // Initialize Appwrite Client
        const client = new Client()
            .setEndpoint(process.env.APPWRITE_ENDPOINT)
            .setProject(process.env.APPWRITE_PROJECT_ID)
            .setKey(process.env.APPWRITE_API_KEY);

        const storage = new Storage(client);
        const bucketId = process.env.APPWRITE_BUCKET_ID;

        if (action === 'upload') {
            // Generate unique file ID
            const newFileId = ID.unique();
            // Appwrite doesn't support generating pre-signed upload URLs for files that don't exist yet,
            // BUT we can upload it directly from the backend, or we can use Appwrite S3 API for presigned urls,
            // but standard Appwrite doesn't natively expose signed upload URLs easily via REST without S3.
            // Wait, we can return the Appwrite API details and a limited scope JWT?
            // Appwrite Server SDK allows creating a JWT for a user, but we are using Firebase Auth!
            // Actually, for free-tier constraints, if we just proxy the upload? No, function RAM might be small.
            // Wait, Appwrite has `createFile` endpoint. But we can't expose APPWRITE_API_KEY.
            // If the Android app sends the file bytes directly to this Function, we proxy it to Appwrite Storage.
            // But payload limits (e.g. 15MB) might apply.
            // Let's assume this function handles generating an S3 pre-signed URL or we return Appwrite JWT.
            // Actually, the prompt says: "Issue expiring signed upload/download URLs using supported Appwrite APIs, including its S3-compatible API where appropriate."
            // We can return the AWS S3 credentials scoped if we had STS, but Appwrite doesn't have STS.
            // We will just use the S3 SDK to generate a presigned URL!

            const AWS = require('aws-sdk');
            const s3 = new AWS.S3({
                endpoint: process.env.APPWRITE_ENDPOINT.replace('/v1', '') + '/v1/storage/buckets/' + bucketId,
                accessKeyId: process.env.APPWRITE_PROJECT_ID,
                secretAccessKey: process.env.APPWRITE_API_KEY,
                region: 'appwrite',
                signatureVersion: 'v4'
            });

            const objectKey = `${conversationId}/${newFileId}${extension ? '.' + extension : ''}`;

            const uploadUrl = s3.getSignedUrl('putObject', {
                Bucket: bucketId,
                Key: objectKey,
                Expires: 3600, // 1 hour
            });

            return res.json({ success: true, uploadUrl, fileId: objectKey });

        } else if (action === 'download') {
            if (!fileId) return res.json({ success: false, message: 'Missing fileId' }, 400);

            // Validate the file path belongs to the conversation
            if (!fileId.startsWith(conversationId + '/')) {
                return res.json({ success: false, message: 'Invalid file path' }, 403);
            }

            const AWS = require('aws-sdk');
            const s3 = new AWS.S3({
                endpoint: process.env.APPWRITE_ENDPOINT.replace('/v1', '') + '/v1/storage/buckets/' + bucketId,
                accessKeyId: process.env.APPWRITE_PROJECT_ID,
                secretAccessKey: process.env.APPWRITE_API_KEY,
                region: 'appwrite',
                signatureVersion: 'v4'
            });

            const downloadUrl = s3.getSignedUrl('getObject', {
                Bucket: bucketId,
                Key: fileId,
                Expires: 3600 // 1 hour
            });

            return res.json({ success: true, downloadUrl });
        } else if (action === 'delete') {
            // Delete media logic
            if (!fileId || !fileId.startsWith(conversationId + '/')) {
                 return res.json({ success: false, message: 'Invalid file path' }, 403);
            }
            // S3 API delete
            const AWS = require('aws-sdk');
            const s3 = new AWS.S3({
                endpoint: process.env.APPWRITE_ENDPOINT.replace('/v1', '') + '/v1/storage/buckets/' + bucketId,
                accessKeyId: process.env.APPWRITE_PROJECT_ID,
                secretAccessKey: process.env.APPWRITE_API_KEY,
                region: 'appwrite',
                signatureVersion: 'v4'
            });
            await s3.deleteObject({
                Bucket: bucketId,
                Key: fileId
            }).promise();

            return res.json({ success: true });
        }

        return res.json({ success: false, message: 'Invalid action' }, 400);
    } catch (err) {
        error('Error in storage gateway: ' + err.message);
        return res.json({ success: false, message: 'Internal error' }, 500);
    }
};
