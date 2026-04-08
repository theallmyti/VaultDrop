import urllib.request
import json
import ssl

video_id = 'aqz-KE-bpKQ'
instances = [
    'https://api.piped.projectsegfau.lt',
    'https://pipedapi.smnz.de',
    'https://pipedapi.in.projectsegfau.lt',
    'https://ytapi.drgns.space'
]

context = ssl._create_unverified_context()
for base in instances:
    url = f"{base}/streams/{video_id}"
    try:
        req = urllib.request.Request(url, headers={'User-Agent':'Mozilla/5.0'})
        with urllib.request.urlopen(req, context=context, timeout=5) as response:
            data = json.loads(response.read().decode('utf-8'))
            streams = data.get('videoStreams', [])
            print(f"Success on {base}: {data.get('title')} - Found {len(streams)} streams")
            qualities = [s.get('quality') for s in streams if not s.get('videoOnly')]
            print(f"Qualities (Muxed): {qualities}")
            break
    except Exception as e:
        print(f"Failed on {base}: {e}")
