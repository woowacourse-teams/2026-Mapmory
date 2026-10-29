# Builds src/data/tech-korea-map.json for the tech section's map scene from the app's district outlines:
#   python3 scripts/generate-tech-korea-map.py ../client/shared/src/commonMain/composeResources/files
# Needs shapely. Paths fit a 200-wide box: kr = every 시·군·구 (fill and thin lines), sd = 시·도 borders (thick lines,
# like the app's map), gb = 경상북도, gj = 경주시; gjc/gbc/krc are label anchors; pv = the 시·도 the example records
# visited (TECH_PLACES in src/techStory.js), filled one by one in the map scene.
import json, math, sys, glob, os
from shapely.geometry import Polygon, MultiPolygon
from shapely.ops import unary_union
from shapely import affinity
src = sys.argv[1]
polys = {}  # code -> list of (district code, polygon)
for f in sorted(glob.glob(os.path.join(src, 'korea-districts-*.json'))):
    d = json.load(open(f)); pc = d['provinceCode']
    for dist in d['districts']:
        if dist['code'] == '47940': continue  # 울릉군: too far east for the phone frame
        ps = []
        for r in dist['rings']:
            if len(r) < 4: continue
            p = Polygon(r).buffer(0)
            if not p.is_empty: ps.append(p)
        g = unary_union(ps)
        if not g.is_empty: polys.setdefault(pc, []).append((dist['code'], g))
allg = unary_union([g for v in polys.values() for _, g in v])
minx, miny, maxx, maxy = allg.bounds
k = math.cos(math.radians((miny + maxy) / 2))
W = 200.0; s = W / ((maxx - minx) * k)
def proj(g):
    g = affinity.translate(g, -minx, -maxy)
    return affinity.scale(g, xfact=k * s, yfact=-s, origin=(0, 0))
H = round((maxy - miny) * s, 1)
TINY = 0.6  # drop islands smaller than this (units²)
def parts(g):
    return list(g.geoms) if isinstance(g, MultiPolygon) else [g]
def path(g, tol):
    out = []
    for p in parts(g):
        if p.area < TINY: continue
        p = p.simplify(tol, preserve_topology=True)
        for ring in [p.exterior, *p.interiors]:
            c = list(ring.coords)[:-1]
            if len(c) < 3: continue
            out.append('M' + 'L'.join(f'{x:.1f},{y:.1f}' for x, y in c) + 'Z')
    return ''.join(out)
dist_p = {c: proj(g) for v in polys.values() for c, g in v}
prov_p = {pc: unary_union([dist_p[c] for c, _ in v]).buffer(0.05).buffer(-0.05) for pc, v in polys.items()}
kr = ''.join(path(g, .45) for g in dist_p.values())
sd = ''.join(path(g, .45) for g in prov_p.values())
gb = path(prov_p['KR-47'], .3)
gjg = dist_p['47130']; gj = path(gjg, .3)
seoul = prov_p['KR-11'].centroid
# 여수 전남, 경주 경북, 전주 전북, 서울, 속초·강릉 강원, 부산, 제주 (the app's province codes)
VISITED = {'KR-46': '전남', 'KR-47': '경북', 'KR-45': '전북', 'KR-11': '서울', 'KR-42': '강원', 'KR-26': '부산', 'KR-50': '제주'}
pv = {name: path(prov_p[code], .45) for code, name in VISITED.items()}
json.dump({'w': W, 'h': H, 'kr': kr, 'sd': sd, 'gb': gb, 'gj': gj,
           'gjc': [round(gjg.centroid.x, 1), round(gjg.centroid.y, 1)],
           'gbc': [round(prov_p['KR-47'].centroid.x, 1), round(prov_p['KR-47'].centroid.y, 1)],
           'krc': [round(seoul.x - 16, 1), round(seoul.y - 10, 1)], 'pv': pv},
          open(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'data', 'tech-korea-map.json'), 'w'), separators=(',', ':'))
print(W, H, len(kr), len(sd), len(gb), len(gj))
