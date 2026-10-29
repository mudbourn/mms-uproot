import json,sys,urllib.request,os
R=sys.argv[1]
m=json.load(open(R+'/manifest.json'))
u={v['id']:v['url'] for v in m['versions']}
def get(url,dst):
    if os.path.exists(dst): return
    urllib.request.urlretrieve(url,dst)
for ver in ('1.21.11','26.3'):
    j=json.load(urllib.request.urlopen(u[ver]))
    d=j['downloads']
    for k,v in d.items():
        print(ver,k,v['size'])
    if ver=='1.21.11':
        get(d['client_mappings']['url'],R+'/mc/client_mappings_1.21.11.txt')
        get(d['server_mappings']['url'],R+'/mc/server_mappings_1.21.11.txt')
    else:
        get(d['client']['url'],R+'/mc/client-26.3.jar')
        get(d['server']['url'],R+'/mc/server-26.3.jar')
get('https://maven.fabricmc.net/net/fabricmc/intermediary/1.21.11/intermediary-1.21.11-v2.jar',R+'/mc/intermediary-1.21.11-v2.jar')

# Mojang-owned libraries (DFU, Brigadier, ...) and Fabric Loader, for the 26.3 index.
os.makedirs(R+'/libs',exist_ok=True)
j=json.load(urllib.request.urlopen(u['26.3']))
for lib in j['libraries']:
    art=lib.get('downloads',{}).get('artifact')
    if art and art['path'].startswith('com/mojang/') and 'natives' not in lib['name']:
        get(art['url'],R+'/libs/mojang__'+os.path.basename(art['path']))
        print('lib',lib['name'])
get('https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar',R+'/libs/loader__fabric-loader-0.19.5.jar')
