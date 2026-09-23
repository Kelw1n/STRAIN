const express = require('express');
const cors = require('cors');
const fs = require('fs');
const path = require('path');

const app = express();
const PORT = process.env.PORT || 3000;

// Enable CORS for mobile apps and web
app.use(cors());

// Support large payloads (Base64 photos up to 25MB)
app.use(express.json({ limit: '25mb' }));
app.use(express.urlencoded({ extended: true, limit: '25mb' }));

// Data directories
const DATA_DIR = process.env.DATA_DIR || path.join(__dirname, 'data');
const PROFILES_DIR = path.join(DATA_DIR, 'profiles');
const CHANNELS_DIR = path.join(DATA_DIR, 'channels');

[DATA_DIR, PROFILES_DIR, CHANNELS_DIR].forEach(dir => {
    if (!fs.existsSync(dir)) {
        fs.mkdirSync(dir, { recursive: true });
    }
});

// In-memory cache for high performance
const profilesCache = new Map();
const channelsCache = new Map();

// Load existing data into cache on startup
try {
    const profileFiles = fs.readdirSync(PROFILES_DIR);
    for (const file of profileFiles) {
        if (file.endsWith('.json')) {
            const id = file.replace('.json', '');
            try {
                const data = JSON.parse(fs.readFileSync(path.join(PROFILES_DIR, file), 'utf-8'));
                profilesCache.set(id, data);
            } catch (_) {}
        }
    }
    console.log(`Loaded ${profilesCache.size} profiles from disk.`);
} catch (e) {
    console.error('Error loading profiles:', e.message);
}

try {
    const channelFiles = fs.readdirSync(CHANNELS_DIR);
    for (const file of channelFiles) {
        if (file.endsWith('.json')) {
            const channelId = file.replace('.json', '');
            try {
                const msgs = JSON.parse(fs.readFileSync(path.join(CHANNELS_DIR, file), 'utf-8'));
                channelsCache.set(channelId, msgs);
            } catch (_) {}
        }
    }
    console.log(`Loaded ${channelsCache.size} chat channels from disk.`);
} catch (e) {
    console.error('Error loading channels:', e.message);
}

// Helper: atomic write file
function saveJsonAtomic(filePath, data) {
    const tempPath = `${filePath}.tmp.${Date.now()}`;
    fs.writeFileSync(tempPath, JSON.stringify(data, null, 2), 'utf-8');
    fs.renameSync(tempPath, filePath);
}

// -----------------------------------------------------------------------------
// Health Check
// -----------------------------------------------------------------------------
app.get('/health', (req, res) => {
    res.json({
        status: 'ok',
        service: 'strain-backend',
        timestamp: Date.now(),
        profilesCount: profilesCache.size,
        channelsCount: channelsCache.size
    });
});

app.get('/', (req, res) => {
    res.json({
        name: 'STRAIN Backend API',
        version: '1.0.0',
        endpoints: {
            profile: '/api/profile/:id',
            heartbeat: '/api/profile/:id/heartbeat',
            chat: '/api/chat/:channelId/messages'
        }
    });
});

// -----------------------------------------------------------------------------
// Profile API
// -----------------------------------------------------------------------------

// Save / update user profile (Full program, customized days, accessories, lifts)
app.put('/api/profile/:id', (req, res) => {
    const { id } = req.params;
    if (!id || id.trim() === '') {
        return res.status(400).json({ error: 'Missing or empty profile ID' });
    }

    let profileData = req.body;
    if (!profileData || typeof profileData !== 'object') {
        return res.status(400).json({ error: 'Invalid profile payload' });
    }

    // Support both direct BroProfileData and legacy { data: BroProfileData } wrapper
    if (profileData.data && typeof profileData.data === 'object') {
        profileData = profileData.data;
    }

    // Ensure broId and lastActiveEpoch are set
    profileData.broId = id;
    if (!profileData.lastActiveEpoch) {
        profileData.lastActiveEpoch = Date.now();
    }

    profilesCache.set(id, profileData);

    try {
        const filePath = path.join(PROFILES_DIR, `${id}.json`);
        saveJsonAtomic(filePath, profileData);
    } catch (e) {
        console.error(`Failed to persist profile ${id}:`, e.message);
    }

    res.json({
        success: true,
        broId: id,
        updatedAt: profileData.lastActiveEpoch
    });
});

// Get profile of buddy
app.get('/api/profile/:id', (req, res) => {
    const { id } = req.params;
    const profile = profilesCache.get(id);

    if (!profile) {
        // Try reading from disk
        const filePath = path.join(PROFILES_DIR, `${id}.json`);
        if (fs.existsSync(filePath)) {
            try {
                const data = JSON.parse(fs.readFileSync(filePath, 'utf-8'));
                profilesCache.set(id, data);
                return res.json(data);
            } catch (_) {}
        }
        return res.status(404).json({ error: `Profile ${id} not found` });
    }

    res.json(profile);
});

// Quick heartbeat (updates online status without sending entire workout payload)
app.post('/api/profile/:id/heartbeat', (req, res) => {
    const { id } = req.params;
    let profile = profilesCache.get(id);

    if (profile) {
        profile.lastActiveEpoch = Date.now();
        profilesCache.set(id, profile);
        try {
            const filePath = path.join(PROFILES_DIR, `${id}.json`);
            saveJsonAtomic(filePath, profile);
        } catch (_) {}
        return res.json({ success: true, lastActiveEpoch: profile.lastActiveEpoch });
    }

    res.json({ success: false, message: 'Profile not found, please sync full profile first' });
});

// -----------------------------------------------------------------------------
// Chat Messages API
// -----------------------------------------------------------------------------

// Send message to channel
app.post('/api/chat/:channelId/messages', (req, res) => {
    const { channelId } = req.params;
    const message = req.body;

    if (!channelId || !message) {
        return res.status(400).json({ error: 'Missing channelId or message payload' });
    }

    if (!message.id) {
        message.id = 'msg_' + Date.now() + '_' + Math.random().toString(36).substring(2, 9);
    }
    if (!message.timestamp) {
        message.timestamp = Date.now();
    }
    message.channelId = channelId;

    let channelMessages = channelsCache.get(channelId);
    if (!channelMessages) {
        channelMessages = [];
        channelsCache.set(channelId, channelMessages);
    }

    // Deduplicate by message ID
    const exists = channelMessages.some(m => m.id === message.id);
    if (!exists) {
        channelMessages.push(message);
        // Keep up to 200 latest messages per channel
        if (channelMessages.length > 200) {
            channelMessages.splice(0, channelMessages.length - 200);
        }

        try {
            const filePath = path.join(CHANNELS_DIR, `${channelId}.json`);
            saveJsonAtomic(filePath, channelMessages);
        } catch (e) {
            console.error(`Failed to persist chat channel ${channelId}:`, e.message);
        }
    }

    res.json({
        success: true,
        message: message
    });
});

// Get messages for channel (supports ?after=:timestamp for incremental updates)
app.get('/api/chat/:channelId/messages', (req, res) => {
    const { channelId } = req.params;
    let channelMessages = channelsCache.get(channelId);

    if (!channelMessages) {
        const filePath = path.join(CHANNELS_DIR, `${channelId}.json`);
        if (fs.existsSync(filePath)) {
            try {
                channelMessages = JSON.parse(fs.readFileSync(filePath, 'utf-8'));
                channelsCache.set(channelId, channelMessages);
            } catch (_) {
                channelMessages = [];
            }
        } else {
            channelMessages = [];
        }
    }

    const afterTimestamp = req.query.after ? parseInt(req.query.after, 10) : 0;
    const limit = req.query.limit ? parseInt(req.query.limit, 10) : 100;

    let filtered = channelMessages;
    if (afterTimestamp > 0) {
        filtered = filtered.filter(m => m.timestamp > afterTimestamp);
    }

    if (filtered.length > limit) {
        filtered = filtered.slice(filtered.length - limit);
    }

    res.json(filtered);
});

// Start server
app.listen(PORT, '0.0.0.0', () => {
    console.log(`STRAIN Backend running on port ${PORT}`);
    console.log(`Health check: http://localhost:${PORT}/health`);
});
