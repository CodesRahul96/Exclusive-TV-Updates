import urllib.request, gzip, re
req = urllib.request.Request('https://tsepg.cf/jio.xml.gz', headers={'User-Agent': 'Mozilla/5.0'})
data = gzip.decompress(urllib.request.urlopen(req).read()).decode('utf-8')
dates = re.findall(r'stop="(\d{14})', data)
if dates:
    print('Max Stop Time: ' + max(dates))
else:
    print('No programmes found')
