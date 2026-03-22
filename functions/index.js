const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

const db = admin.firestore();

/**
 * registerUser Cloud Function
 * Handles secure device-locked registration with IP-based rate limiting.
 */
exports.registerUser = functions.https.onCall(async (data, context) => {
    // 1. Basic Validation
    const phoneNumber = data.phoneNumber;
    const deviceFingerprint = data.deviceFingerprint;
    const integrityToken = data.integrityToken; // Optional for now, but logged
    const ipAddress = context.rawRequest.ip || "unknown";

    if (!phoneNumber || !deviceFingerprint) {
        throw new functions.https.HttpsError("invalid-argument", "Missing required fields.");
    }

    // 2. IP-Based Rate Limiting
    const ipRef = db.collection("registrations_by_ip").doc(ipAddress);
    const ipDoc = await ipRef.get();
    const now = admin.firestore.Timestamp.now();
    
    if (ipDoc.exists) {
        const ipData = ipDoc.data();
        const lastReg = ipData.last_registration.toDate();
        const count = ipData.count || 0;
        
        // Limit: 3 registrations per hour from the same IP
        const oneHourAgo = new Date(now.toDate().getTime() - 3600000);
        if (lastReg > oneHourAgo && count >= 3) {
            throw new functions.https.HttpsError("resource-exhausted", "Too many registrations from this IP. Please try again later.");
        }
        
        await ipRef.set({
            count: lastReg > oneHourAgo ? (count + 1) : 1,
            last_registration: now
        }, { merge: true });
    } else {
        await ipRef.set({ count: 1, last_registration: now });
    }

    // 3. User Check
    const userRef = db.collection("users").doc(phoneNumber);
    const userDoc = await userRef.get();
    
    if (userDoc.exists) {
        return { success: true, message: "User already exists", plan: userDoc.data().plan };
    }

    // 4. Master Bypass Check
    const bypassRef = db.collection("master_bypass").doc(phoneNumber);
    const bypassDoc = await bypassRef.get();
    const isMasterUser = bypassDoc.exists;

    // 5. Check if device already has a user
    const deviceUsers = await db.collection("users")
        .where("device_fingerprint", "==", deviceFingerprint)
        .limit(1)
        .get();
    
    if (!deviceUsers.empty) {
        // Device already registered with a DIFFERENT phone number
        throw new functions.https.HttpsError("already-exists", "This device is already registered with another account.");
    }

    // 6. Trial Check (Device Fingerprint)
    const trialRef = db.collection("trials").doc(deviceFingerprint);
    const trialDoc = await trialRef.get();
    const hasHadTrial = trialDoc.exists;

    let plan = "Standard";
    let expiryDate = null;

    if (isMasterUser || !hasHadTrial) {
        plan = "Premium";
        const expiry = new Date();
        expiry.setDate(expiry.getDate() + 7); // 7-day trial
        expiryDate = admin.firestore.Timestamp.fromDate(expiry);
        
        // Record Trial
        await trialRef.set({
            claimed_by: phoneNumber,
            claimed_at: now,
            master_bypass: isMasterUser,
            ip: ipAddress
        });
    }

    // 6. Create User
    const userData = {
        phone_number: phoneNumber,
        device_fingerprint: deviceFingerprint,
        plan: plan,
        status: "active",
        created_at: now,
        ip_registration: ipAddress
    };
    if (expiryDate) userData.expiry_date = expiryDate;

    await userRef.set(userData);

    return { 
        success: true, 
        plan: plan, 
        expiry_date: expiryDate ? expiryDate.toDate().toISOString() : null 
    };
});

/**
 * getPlaylist Cloud Function
 * Securely returns the appropriate playlist URL based on the user's plan.
 */
exports.getPlaylist = functions.https.onCall(async (data, context) => {
    const phoneNumber = data.phoneNumber;
    if (!phoneNumber) {
        throw new functions.https.HttpsError("invalid-argument", "Missing phone number.");
    }

    const userRef = db.collection("users").doc(phoneNumber);
    const userDoc = await userRef.get();

    if (!userDoc.exists) {
        throw new functions.https.HttpsError("not-found", "Account not found.");
    }

    const userData = userDoc.data();
    const plan = userData.plan || "Standard";

    // Fetch master config for URLs
    // [NOTE] Admin must populate config/playlists with 'standard' and 'premium' fields.
    const configRef = db.collection("config").doc("playlists");
    const configDoc = await configRef.get();

    if (!configDoc.exists) {
        throw new functions.https.HttpsError("internal", "Playlist configuration is not set up on the server.");
    }

    const playlists = configDoc.data();
    const playlistUrl = (plan === "Premium") ? playlists.premium : playlists.standard;

    if (!playlistUrl) {
        throw new functions.https.HttpsError("internal", `No ${plan} playlist available.`);
    }

    return { 
        success: true, 
        url: playlistUrl,
        plan: plan 
    };
});
