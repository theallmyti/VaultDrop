const http = require('https');
const data = JSON.stringify({ url: 'https://www.youtube.com/watch?v=aqz-KE-bpKQ', videoQuality: '1080' });
const options = {
  hostname: 'api.cobalt.tools',
  port: 443,
  path: '/',
  method: 'POST',
  headers: {
    'Accept': 'application/json',
    'Content-Type': 'application/json',
    'Content-Length': data.length,
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
  }
};
const req = http.request(options, res => {
  let body = '';
  res.on('data', chunk => body += chunk);
  res.on('end', () => console.log(body));
});
req.on('error', e => console.error(e));
req.write(data);
req.end();
