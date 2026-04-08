import urllib.request
import json
import ssl

video_id = 'aqz-KE-bpKQ'
url = 'https://www.youtube.com/youtubei/v1/player'
headers = {'Content-Type': 'application/json'}
payload = {
    'context': {
        'client': {
            'clientName': 'WEB',
            'clientVersion': '2.20210210.08.00'
        }
    },
    'videoId': video_id
}

req = urllib.request.Request(url, data=json.dumps(payload).encode('utf-8'), headers=headers)
context = ssl._create_unverified_context()
try:
    with urllib.request.urlopen(req, context=context) as response:
        data = json.loads(response.read().decode('utf-8'))
        streamingData = data.get('streamingData', {})
        formats = streamingData.get('formats', [])
        for f in formats:
            print(f"{f.get('qualityLabel')} - {f.get('url', 'No URL (Signature required)')[:50]}")
except Exception as e:
    print(e)
