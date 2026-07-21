// Regenerates the tutorial's bundled Oliver audio (app/src/main/res/raw/)
// through the production TTS voice, using the service-account credential from
// the sibling oracle-mobile-api checkout. Run whenever a line's text changes
// in TutorialScript.kt — the LINES map below must stay byte-identical to it.
//
//   node scripts/gen-tutorial-audio.js [line-name ...]
//
// With no arguments, regenerates every line. Pass one or more line names
// (e.g. tutorial_deal) to regenerate only those — use this when only one
// line's text or pause tuning changed, so the others aren't re-rendered
// (Google TTS isn't guaranteed byte-identical run to run).
//
// Voice config mirrors oracle-mobile-api lib/games/blackjack.ts, which today
// reuses the poker "Oliver" voice (en-US-Neural2-D @ 1.08). Credential lookup
// prefers the blackjack service account and falls back to poker's — the voice
// parameters are identical either way.
//
// Android res/raw resource names cannot contain hyphens, so these files use
// underscores where the iOS bundle names use dashes (tutorial_deal.mp3 vs
// tutorial-deal.mp3). The audio content is identical.
const fs = require('fs');
const path = require('path');

const API_REPO = path.resolve(__dirname, '../../../oracle-mobile-api');
module.paths.push(path.join(API_REPO, 'node_modules'));
const { JWT } = require('google-auth-library');

const ENV_PATH = path.join(API_REPO, '.env');
const OUT_DIR = path.resolve(__dirname, '../app/src/main/res/raw');

// Must stay byte-identical to TutorialScript.kt's lines.
const LINES = {
  'tutorial_deal': "Eighteen against a six. Feels safe. It isn't. Ask me, Oliver, your AI Coach!",
  'tutorial_split': 'Split them. A six is one of the weakest cards a dealer can show, and two hands built on a nine beat one static eighteen.',
  'tutorial_stand': 'Nineteen. The split already paid for itself — this hand alone beats the eighteen you started with. Stand.',
  'tutorial_double': 'Eleven against a six — one of the best doubles in the game. This is exactly the spot splitting those nines bought you.',
  'tutorial_closing': "Most players never break an eighteen. That's why they never find hands like this one.",
};

const VOICE = { languageCode: 'en-US', name: 'en-US-Neural2-D' };
const AUDIO = { audioEncoding: 'MP3', speakingRate: 1.08, pitch: 0.0, volumeGainDb: 0.0 };

function credential() {
  const env = fs.readFileSync(ENV_PATH, 'utf8');
  for (const key of ['GOOGLE_TTS_BLACKJACK_SERVICE_ACCOUNT', 'GOOGLE_TTS_POKER_SERVICE_ACCOUNT']) {
    const m = env.match(new RegExp(`^${key}=["']?([^"'\\n]+)["']?$`, 'm'));
    if (m) return JSON.parse(Buffer.from(m[1], 'base64').toString('utf8'));
  }
  throw new Error('No TTS service account found in oracle-mobile-api/.env');
}

async function main() {
  const creds = credential();
  const client = new JWT({
    email: creds.client_email,
    key: creds.private_key,
    scopes: ['https://www.googleapis.com/auth/cloud-platform'],
  });
  const { token } = await client.getAccessToken();
  if (!token) throw new Error('No Google access token');

  fs.mkdirSync(OUT_DIR, { recursive: true });

  const only = process.argv.slice(2);
  const entries = only.length
    ? Object.entries(LINES).filter(([name]) => only.includes(name))
    : Object.entries(LINES);
  for (const name of only) {
    if (!(name in LINES)) throw new Error(`Unknown line: ${name}`);
  }

  for (const [name, text] of entries) {
    const res = await fetch('https://texttospeech.googleapis.com/v1/text:synthesize', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ input: { text }, voice: VOICE, audioConfig: AUDIO }),
    });
    if (!res.ok) throw new Error(`TTS ${name}: ${res.status} ${await res.text()}`);
    const { audioContent } = await res.json();
    const out = path.join(OUT_DIR, `${name}.mp3`);
    fs.writeFileSync(out, Buffer.from(audioContent, 'base64'));
    console.log(`${name}.mp3  ${fs.statSync(out).size} bytes`);
  }
}

main().catch((e) => { console.error(e.message); process.exit(1); });
