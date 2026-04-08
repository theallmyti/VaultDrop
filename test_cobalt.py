import urllib.request
import json
import ssl
req = urllib.request.Request('https://cobalt-api.kwiateki.com/', data=json.dumps({'url':'https://www.youtube.com/watch?v=aqz-KE-bpKQ'}).encode('utf-8'), headers={'Accept':'application/json', 'Content-Type':'application/json'})
ctx = ssl._create_unverified_context()
try:
    with urllib.request.urlopen(req, context=ctx) as response:
        print(response.read().decode('utf-8'))
except Exception as e:
    print(e)
