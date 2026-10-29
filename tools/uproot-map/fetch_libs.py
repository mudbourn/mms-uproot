import json,sys,urllib.request,urllib.parse,os
R=sys.argv[1]+'/libs'
def api(slug):
    q='https://api.modrinth.com/v2/project/%s/version?loaders=%s&game_versions=%s'%(slug,urllib.parse.quote('["fabric"]'),urllib.parse.quote('["26.3"]'))
    req=urllib.request.Request(q,headers={'User-Agent':'mms-uproot-map/0.1 (local)'})
    return json.load(urllib.request.urlopen(req))
for slug in ['P7dR8mSH','terrablender','geckolib','oh-the-trees-youll-grow','corgilib','wthit']:
    try: vs=api(slug)
    except Exception as e: print(slug,'ERR',e); continue
    if not vs: print(slug,'NO 26.3 BUILD'); continue
    v=vs[0]
    f=[x for x in v['files'] if x.get('primary')] or v['files']
    f=f[0]
    dst=os.path.join(R,slug+'__'+f['filename'])
    if not os.path.exists(dst):
        req=urllib.request.Request(f['url'],headers={'User-Agent':'mms-uproot-map/0.1 (local)'})
        open(dst,'wb').write(urllib.request.urlopen(req).read())
    print(slug,v['version_number'],v['game_versions'],f['filename'],f['size'])
