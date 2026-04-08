const https = require('https');
const data = JSON.stringify({ url: 'https://www.youtube.com/watch?v=aqz-KE-bpKQ', videoQuality: '1080' });

const urls = [
    'https://api.cobalt.tools',
    'https://co.wuk.sh/api',
    'https://cobalt.kwiateki.com',
    'https://cobalt-api.kwiateki.com',
    'https://api.cobalt.best',
    'https://cobalt.canine.sc',
    'https://api.cobalt.canine.sc',
    'https://co.pussthecat.org' // Added trailing to prevent malformed but anyway
];

function testUrl(baseUrl) {
    const parsed = new URL(baseUrl);
    const options = {
        hostname: parsed.hostname,
        port: 443,
        path: parsed.pathname === '/' ? '/api/json' : (parsed.pathname.endsWith('/api') ? '/json' : '/api/json'),
        method: 'POST',
        headers: {
            'Accept': 'application/json',
            'Content-Type': 'application/json',
            'Content-Length': data.length,
            'User-Agent': 'Mozilla/5.0'
        }
    };
    
    const req = https.request(options, res => {
        let body = '';
        res.on('data', chunk => body+=chunk);
        res.on('end', () => console.log(baseUrl, res.statusCode, body.substring(0, 100)));
    });
    req.on('error', e => console.log(baseUrl, 'ERROR:', e.message));
    req.setTimeout(5000, () => {
        console.log(baseUrl, 'TIMEOUT');
        req.destroy();
    });
    req.write(data);
    req.end();
}

urls.forEach(testUrl);
