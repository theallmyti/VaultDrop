import urllib.request
import json
import ssl

video_id = 'aqz-KE-bpKQ'
url = 'https://www.youtube.com/youtubei/v1/player'
headers = {'Content-Type': 'application/json'}
payload = {
    'context': {
        'client': {
            'clientName': 'ANDROID',
            'clientVersion': '19.01.32',
            'androidSdkVersion': 30
        }
    },
    'videoId': video_id,
    'playbackContext': {
        'contentPlaybackContext': {
            'signatureTimestamp': 20000
        }
    }
}
req = urllib.request.Request(url, data=json.dumps(payload).encode('utf-8'), headers=headers)
context = ssl._create_unverified_context()
try:
    with urllib.request.urlopen(req, context=context) as response:
        data = json.loads(response.read().decode('utf-8'))
        print("Keys:", data.keys())
        streamingData = data.get('streamingData', {})
        print("Formats length:", len(streamingData.get('formats', [])))
        print("AdaptiveFormats length:", len(streamingData.get('adaptiveFormats', [])))
        
        for f in streamingData.get('formats', []):
           print(f"Format: {f.get('qualityLabel')} - has url: {'url' in f}, has signature: {'signatureCipher' in f}")
except Exception as e:
    print(e)
