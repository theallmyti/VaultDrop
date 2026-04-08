const fetch = require('node-fetch');
fetch('https://api.cobalt.tools/', {
    method: 'POST',
    headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
    body: JSON.stringify({ url: 'https://www.youtube.com/watch?v=aqz-KE-bpKQ' })
}).then(res => res.text()).then(console.log);
