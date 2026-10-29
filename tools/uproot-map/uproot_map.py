#!/usr/bin/env python3
"""uproot-map: map 1.21.11 or 26.x mod references to Minecraft 26.3 and report what survives.

Run:  python3 -I uproot_map.py <refdata-dir> <out-dir> <mod.jar> [<mod.jar> ...]

Standard library only. Class files are parsed directly; nothing from any jar is executed.
Pipeline: collect refs (pool, mixins, access wideners) -> intermediary -> official ->
1.21.11 Mojang names -> check against a 26.3 index -> classify -> suggest -> CSV/SUMMARY.
Jars built for 26.x already use Mojang names (no net/minecraft/class_ refs); for those the
mapping step is skipped, and the CSV's intermediary and 1.21.11 columns both hold the source name.
"""
import csv, difflib, io, json, os, re, struct, sys, zipfile
from collections import defaultdict

# --------------------------------------------------------------------------- class files

class Cls:
    __slots__ = ("name", "super", "ifaces", "fields", "methods", "annos", "pool_refs", "access", "desc_classes")


def parse_class(data, want_annos=False, want_refs=False):
    """Parse a class file. fields/methods: list of (name, desc, access, annos)."""
    if data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("bad magic")
    cc = struct.unpack_from(">H", data, 8)[0]
    o = 10
    pool = [None] * cc
    i = 1
    while i < cc:
        t = data[o]
        if t == 1:
            ln = struct.unpack_from(">H", data, o + 1)[0]
            pool[i] = (1, data[o + 3:o + 3 + ln])
            o += 3 + ln
        elif t in (3, 4):
            o += 5
        elif t in (5, 6):
            o += 9
            i += 1
        elif t in (7, 8, 16, 19, 20):
            pool[i] = (t, struct.unpack_from(">H", data, o + 1)[0])
            o += 3
        elif t in (9, 10, 11, 12, 17, 18):
            pool[i] = (t,) + struct.unpack_from(">HH", data, o + 1)
            o += 5
        elif t == 15:
            pool[i] = (15, data[o + 1], struct.unpack_from(">H", data, o + 2)[0])
            o += 4
        else:
            raise ValueError("bad pool tag %d" % t)
        i += 1

    def utf(ix):
        return pool[ix][1].decode("utf-8", "replace")

    def cname(ix):
        return utf(pool[ix][1]) if ix else None

    c = Cls()
    c.access, this, sup, nif = struct.unpack_from(">HHHH", data, o)
    o += 8
    c.name = cname(this)
    c.super = cname(sup)
    c.ifaces = []
    for _ in range(nif):
        c.ifaces.append(cname(struct.unpack_from(">H", data, o)[0]))
        o += 2

    def read_annos(d, p):
        n = struct.unpack_from(">H", d, p)[0]
        p += 2
        out = []
        for _ in range(n):
            a, p = read_anno(d, p)
            out.append(a)
        return out, p

    def read_anno(d, p):
        ti, np_ = struct.unpack_from(">HH", d, p)
        p += 4
        vals = {}
        for _ in range(np_):
            ni = struct.unpack_from(">H", d, p)[0]
            p += 2
            v, p = read_ev(d, p)
            vals[utf(ni)] = v
        return {"type": utf(ti), "vals": vals}, p

    def read_ev(d, p):
        tag = chr(d[p])
        p += 1
        if tag in "BCDFIJSZ":
            return ("n", struct.unpack_from(">H", d, p)[0]), p + 2
        if tag == "s":
            return ("s", utf(struct.unpack_from(">H", d, p)[0])), p + 2
        if tag == "e":
            a, b = struct.unpack_from(">HH", d, p)
            return ("e", utf(a), utf(b)), p + 4
        if tag == "c":
            return ("c", utf(struct.unpack_from(">H", d, p)[0])), p + 2
        if tag == "@":
            a, p = read_anno(d, p)
            return ("@", a), p
        if tag == "[":
            n = struct.unpack_from(">H", d, p)[0]
            p += 2
            lst = []
            for _ in range(n):
                v, p = read_ev(d, p)
                lst.append(v)
            return ("[", lst), p
        raise ValueError("bad element tag " + tag)

    def members(o):
        n = struct.unpack_from(">H", data, o)[0]
        o += 2
        out = []
        for _ in range(n):
            acc, ni, di, ac = struct.unpack_from(">HHHH", data, o)
            o += 8
            annos = []
            for _ in range(ac):
                an, ln = struct.unpack_from(">HI", data, o)
                if want_annos and utf(an) in ("RuntimeVisibleAnnotations", "RuntimeInvisibleAnnotations"):
                    annos += read_annos(data, o + 6)[0]
                o += 6 + ln
            out.append((utf(ni), utf(di), acc, annos))
        return out, o

    c.fields, o = members(o)
    c.methods, o = members(o)
    c.annos = []
    ac = struct.unpack_from(">H", data, o)[0]
    o += 2
    for _ in range(ac):
        an, ln = struct.unpack_from(">HI", data, o)
        if want_annos and utf(an) in ("RuntimeVisibleAnnotations", "RuntimeInvisibleAnnotations"):
            c.annos += read_annos(data, o + 6)[0]
        o += 6 + ln
    c.pool_refs = []
    if want_refs:
        for e in pool:
            if not e:
                continue
            if e[0] == 7:
                c.pool_refs.append(("class", cname_raw(utf(e[1])), None, None))
            elif e[0] in (9, 10, 11):
                cl = pool[e[1]]
                nt = pool[e[2]]
                c.pool_refs.append(("field" if e[0] == 9 else "method", utf(cl[1]), utf(nt[1]), utf(nt[2])))
    return c


def cname_raw(n):
    return n


def anno_str_list(v):
    """element value -> list of python strings."""
    if v is None:
        return []
    if v[0] == "s":
        return [v[1]]
    if v[0] == "[":
        out = []
        for x in v[1]:
            out += anno_str_list(x)
        return out
    return []


def anno_class_list(v):
    if v is None:
        return []
    if v[0] == "c":
        return [v[1]]
    if v[0] == "[":
        out = []
        for x in v[1]:
            out += anno_class_list(x)
        return out
    return []


def anno_list(v):
    if v is None:
        return []
    if v[0] == "@":
        return [v[1]]
    if v[0] == "[":
        out = []
        for x in v[1]:
            out += anno_list(x)
        return out
    return []


# --------------------------------------------------------------------------- jar walking

def walk_jar(zf, label, depth=0):
    """Yield (label, zipfile) for the jar and every nested jar."""
    yield label, zf
    if depth > 4:
        return
    for n in zf.namelist():
        if n.endswith(".jar") and not n.endswith("/"):
            try:
                inner = zipfile.ZipFile(io.BytesIO(zf.read(n)))
            except zipfile.BadZipFile:
                continue
            yield from walk_jar(inner, label + "!" + n, depth + 1)


# --------------------------------------------------------------------------- mappings

DESC_CLS = re.compile(r"L([^;]+);")


class Mappings:
    """intermediary -> official -> 1.21.11 Mojang (ProGuard), with descriptors."""

    def __init__(self, tiny_path, pg_paths):
        self.i2o = {}            # intermediary class -> official class
        self.o2i = {}
        self.ifield = defaultdict(list)   # inter field -> [(offclass, offname, offdesc)]
        self.imeth = defaultdict(list)
        cur = None
        with open(tiny_path, encoding="utf-8") as f:
            f.readline()
            for ln in f:
                p = ln.rstrip("\n").split("\t")
                if p[0] == "c":
                    cur = p[1]
                    self.i2o[p[2]] = p[1]
                    self.o2i[p[1]] = p[2]
                elif p[0] == "" and len(p) >= 5 and p[1] in ("f", "m"):
                    (self.ifield if p[1] == "f" else self.imeth)[p[4]].append((cur, p[3], p[2]))
        self.o2m = {}            # official class (slash) -> mojang class (slash)
        self.m2o = {}
        raw = []                 # (obfclass, kind, mojname, rtype, args, obfname)
        for pg in pg_paths:
            cur = None
            with open(pg, encoding="utf-8") as f:
                for ln in f:
                    if ln.startswith("#") or not ln.strip():
                        continue
                    if not ln.startswith(" "):
                        m, o = ln.rstrip().rstrip(":").split(" -> ")
                        cur = o.replace(".", "/")
                        mj = m.replace(".", "/")
                        self.o2m[cur] = mj
                        self.m2o[mj] = cur
                    else:
                        raw.append((cur, ln.strip()))
        self.pg_field = {}       # (offclass, offname) -> mojang name
        self.pg_meth = {}        # (offclass, offname, offdesc) -> mojang name
        prim = {"int": "I", "long": "J", "float": "F", "double": "D", "boolean": "Z", "byte": "B",
                "char": "C", "short": "S", "void": "V"}

        def to_desc(t):
            dims = 0
            while t.endswith("[]"):
                dims += 1
                t = t[:-2]
            if t in prim:
                d = prim[t]
            else:
                d = "L" + self.m2o.get(t.replace(".", "/"), t.replace(".", "/")) + ";"
            return "[" * dims + d

        for oc, ln in raw:
            body, obf = ln.split(" -> ")
            body = re.sub(r"^\d+:\d+:", "", body)
            body = re.sub(r":\d+:\d+$", "", body)
            if "(" in body:
                head, rest = body.split("(", 1)
                args = rest.rsplit(")", 1)[0]
                rt, nm = head.rsplit(" ", 1)
                ad = "".join(to_desc(a) for a in args.split(",")) if args else ""
                self.pg_meth[(oc, obf, "(" + ad + ")" + to_desc(rt))] = nm
            else:
                t, nm = body.rsplit(" ", 1)
                self.pg_field[(oc, obf)] = nm.split(".")[-1]

    # ---- class / descriptor mapping
    def cls(self, inter):
        off = self.i2o.get(inter)
        if off is None:
            return inter
        return self.o2m.get(off, inter)

    def desc(self, d):
        return DESC_CLS.sub(lambda m: "L" + self.cls(m.group(1)) + ";", d) if d else d

    def class_known(self, inter):
        return inter in self.i2o

    def field(self, owner, name):
        c = self.ifield.get(name)
        if not c:
            return name
        oo = self.i2o.get(owner)
        pick = next((x for x in c if x[0] == oo), c[0])
        return self.pg_field.get((pick[0], pick[1]), pick[1])

    def method(self, owner, name, desc):
        c = self.imeth.get(name)
        if not c:
            return name
        oo = self.i2o.get(owner)
        pick = next((x for x in c if x[0] == oo), None)
        if pick is None:
            pick = c[0]
        return self.pg_meth.get((pick[0], pick[1], pick[2]), pick[1])


# --------------------------------------------------------------------------- 26.3 index

class Index:
    def __init__(self):
        self.cls = {}            # name -> (super, ifaces, {field: {desc}}, {method: {desc}}, origin)
        self.simple = defaultdict(list)
        self.by_member = defaultdict(list)   # member name -> [(class, 'f'/'m', desc)]
        self.jars = []

    def add_jar(self, path, origin):
        zf = zipfile.ZipFile(path)
        n0 = len(self.cls)
        for label, z in walk_jar(zf, origin):
            for n in z.namelist():
                if not n.endswith(".class") or n.startswith("META-INF/") or n.endswith("module-info.class"):
                    continue
                if n.startswith("classes/"):
                    if not origin.startswith("jdk"):
                        continue
                elif origin.startswith("jdk"):
                    continue
                try:
                    c = parse_class(z.read(n))
                except Exception:
                    continue
                fl = defaultdict(set)
                ml = defaultdict(set)
                for nm, d, _, _ in c.fields:
                    fl[nm].add(d)
                for nm, d, _, _ in c.methods:
                    ml[nm].add(d)
                if c.name in self.cls:
                    old = self.cls[c.name]
                    for k, v in fl.items():
                        old[2].setdefault(k, set()).update(v)
                    for k, v in ml.items():
                        old[3].setdefault(k, set()).update(v)
                    continue
                self.cls[c.name] = (c.super, tuple(c.ifaces), dict(fl), dict(ml), origin)
        self.jars.append((origin, len(self.cls) - n0))

    def finish(self):
        for name, (s, i, fl, ml, o) in self.cls.items():
            if name.startswith("java/"):
                continue
            self.simple[name.rsplit("/", 1)[-1]].append(name)
            for k, ds in fl.items():
                for d in ds:
                    self.by_member[k].append((name, "f", d))
            for k, ds in ml.items():
                if k.startswith("<") or k.startswith("lambda$"):
                    continue
                for d in ds:
                    self.by_member[k].append((name, "m", d))

    def chain(self, name):
        """BFS over super + interfaces. Returns (list of classes in index, unknown_flag)."""
        seen, order, unknown = set(), [], False
        todo = [name]
        while todo:
            n = todo.pop(0)
            if n in seen or n is None:
                continue
            seen.add(n)
            c = self.cls.get(n)
            if c is None:
                if n in JDK_BASE:
                    order.append(n)
                elif not n.startswith("java/"):
                    unknown = True
                else:
                    unknown = True
                continue
            order.append(n)
            todo.append(c[0])
            todo.extend(c[1])
        return order, unknown


JDK_BASE = {"java/lang/Object", "java/lang/Enum", "java/lang/Record"}
JDK_METHODS = {
    "java/lang/Object": {"equals": {"(Ljava/lang/Object;)Z"}, "hashCode": {"()I"}, "toString": {"()Ljava/lang/String;"},
                         "getClass": {"()Ljava/lang/Class;"}, "clone": {"()Ljava/lang/Object;"},
                         "notify": {"()V"}, "notifyAll": {"()V"}, "wait": {"()V", "(J)V", "(JI)V"},
                         "finalize": {"()V"}, "<init>": {"()V"}},
    "java/lang/Enum": {"name": {"()Ljava/lang/String;"}, "ordinal": {"()I"}, "compareTo": {"(Ljava/lang/Enum;)I", "(Ljava/lang/Object;)I"},
                       "getDeclaringClass": {"()Ljava/lang/Class;"}, "describeConstable": {"()Ljava/util/Optional;"}},
    "java/lang/Record": {},
}


def members_in(idx, cls, kind):
    c = idx.cls.get(cls)
    if c is not None:
        return c[2] if kind == "field" else c[3]
    if kind == "method":
        return JDK_METHODS.get(cls, {})
    return {}


# --------------------------------------------------------------------------- statuses

OK, DESC_CHANGED, MEMBER_MISSING, CLASS_MOVED, CLASS_MISSING = "OK", "DESC_CHANGED", "MEMBER_MISSING", "CLASS_MOVED", "CLASS_MISSING"
UNVERIFIED, NO_LIB, SKIPPED = "UNVERIFIED", "NO_26_3_LIB", "SKIPPED_PATTERN"
STATUS_ORDER = [OK, DESC_CHANGED, MEMBER_MISSING, CLASS_MOVED, CLASS_MISSING, UNVERIFIED, NO_LIB, SKIPPED]
SEVERITY = {OK: 0, SKIPPED: 1, UNVERIFIED: 2, NO_LIB: 3, DESC_CHANGED: 4, MEMBER_MISSING: 5, CLASS_MOVED: 6, CLASS_MISSING: 7}

CHECKED_PREFIXES = ("net/minecraft/", "com/mojang/", "net/fabricmc/", "terrablender/", "software/bernie/", "dev/corgitaco/ohthetreesyoullgrow/")
# libraries that the brief lists no 26.3 build for (CorgiLib, WTHIT): reported as NO_26_3_LIB, not checked
NO_BUILD_PREFIXES = ("corgitaco/corgilib/", "mcp/mobius/")
IGNORED_PREFIXES = ("java/", "javax/", "jdk/", "sun/", "org/spongepowered/", "com/llamalad7/", "com/google/", "it/unimi/", "org/joml/",
                    "io/netty/", "org/slf4j/", "org/apache/", "org/lwjgl/", "org/jetbrains/", "org/objectweb/", "kotlin/", "org/intellij/",
                    "org/lwjgl", "com/electronwill/", "org/jspecify/", "org/checkerframework/", "net/neoforged/", "org/jetbrains")


def in_scope(owner, own_pkgs):
    o = owner.lstrip("[")
    if o.startswith("L") and o.endswith(";"):
        o = o[1:-1]
    if any(o.startswith(p) for p in own_pkgs):
        return False
    return o.startswith(CHECKED_PREFIXES) or o.startswith(NO_BUILD_PREFIXES)


# --------------------------------------------------------------------------- area

AREA_RULES = [
    ("net/minecraft/world/level/levelgen/", "worldgen"), ("net/minecraft/world/level/biome/", "worldgen"),
    ("net/minecraft/data/worldgen/", "worldgen"), ("net/minecraft/world/level/dimension/", "worldgen"),
    ("net/minecraft/world/level/chunk/", "chunk"), ("net/minecraft/world/level/ChunkPos", "chunk"),
    ("net/minecraft/world/level/block/entity/", "block-entity"), ("net/minecraft/world/level/block/", "block"),
    ("net/minecraft/world/level/material/", "fluid/material"), ("net/minecraft/world/level/storage/", "storage"),
    ("net/minecraft/core/registries/", "registry"), ("net/minecraft/core/Registry", "registry"), ("net/minecraft/core/Holder", "registry"),
    ("net/minecraft/resources/", "registry/resources"), ("net/minecraft/tags/", "tags"), ("net/minecraft/core/", "core"),
    ("net/minecraft/world/entity/", "entity"), ("net/minecraft/world/item/", "item"), ("net/minecraft/world/inventory/", "inventory"),
    ("net/minecraft/world/level/", "level"), ("net/minecraft/world/damagesource/", "entity"), ("net/minecraft/world/effect/", "entity"),
    ("net/minecraft/world/", "world-misc"), ("net/minecraft/nbt/", "nbt"), ("net/minecraft/network/", "network"),
    ("net/minecraft/client/", "client"), ("net/minecraft/server/", "server"), ("net/minecraft/data/", "datagen"),
    ("net/minecraft/sounds/", "sound"), ("net/minecraft/util/", "util"), ("net/minecraft/commands/", "commands"),
    ("net/minecraft/advancements/", "advancements"), ("net/minecraft/", "minecraft-other"),
    ("com/mojang/serialization/", "codec/datafixer"), ("com/mojang/datafixers/", "codec/datafixer"),
    ("com/mojang/brigadier/", "commands"), ("com/mojang/blaze3d/", "render"), ("com/mojang/math/", "math"), ("com/mojang/", "mojang-other"),
    ("net/fabricmc/", "fabric"), ("terrablender/", "terrablender"), ("software/bernie/", "geckolib"),
]


def area_of(owner):
    for p, a in AREA_RULES:
        if owner.startswith(p):
            return a
    return "other"


# --------------------------------------------------------------------------- reference collection

AT_METHOD = re.compile(r"^L([^;]+);([^(:\s]+)(\(.*)$")
AT_FIELD = re.compile(r"^L([^;]+);([^(:\s]+):(.+)$")
AT_OLD = re.compile(r"^([\w/$.]+)\.([\w<>$]+)(\(.*)$")
MIXIN_PKG = "org/spongepowered/asm/mixin/"
MX_PKG = "com/llamalad7/mixinextras/"


def split_target(s):
    """Parse a mixin member string. Returns (owner|None, name, desc|None, is_field)."""
    s = s.strip()
    m = AT_FIELD.match(s)
    if m:
        return m.group(1), m.group(2), m.group(3), True
    m = AT_METHOD.match(s)
    if m:
        return m.group(1), m.group(2), m.group(3), False
    m = AT_OLD.match(s)
    if m:
        return m.group(1).replace(".", "/"), m.group(2), m.group(3), False
    m = re.match(r"^([\w$<>*]+)(\(.*)?$", s)
    if m:
        return None, m.group(1), m.group(2), False
    return None, s, None, False


class Collector:
    def __init__(self, mod_label):
        self.mod = mod_label
        self.refs = defaultdict(set)         # (kind, owner, name, desc) -> {where}
        self.mixins = defaultdict(set)       # mixin class -> {(kind, owner, name, desc, role)}
        self.own = set()                     # internal names of classes shipped in the jar (and nested jars)
        self.own_pkgs = set()
        self.notes = []
        self.stats = defaultdict(int)

    def add(self, kind, owner, name, desc, where):
        self.refs[(kind, owner, name, desc)].add(where)

    def add_mixin(self, mixin, kind, owner, name, desc, role, where):
        self.add(kind, owner, name, desc, where)
        self.mixins[mixin].add((kind, owner, name, desc, role))


def collect_jar(path, mapper_scope):
    col = Collector(os.path.basename(path))
    zf = zipfile.ZipFile(path)
    parts = list(walk_jar(zf, os.path.basename(path)))
    # pass 1: which classes are ours (any jar inside)
    for label, z in parts:
        for n in z.namelist():
            if n.endswith(".class") and not n.startswith("META-INF/versions/"):
                col.own.add(n[:-6])
    for label, z in parts:
        collect_one(col, label, z)
    return col


def read_json(z, name):
    try:
        raw = z.read(name).decode("utf-8", "replace")
    except KeyError:
        return None
    try:
        return json.loads(raw)
    except ValueError:
        raw = re.sub(r"/\*.*?\*/", "", raw, flags=re.S)
        raw = re.sub(r"^\s*//.*$", "", raw, flags=re.M)
        try:
            return json.loads(raw)
        except ValueError:
            return None


def collect_one(col, label, z):
    names = set(z.namelist())
    fmj = read_json(z, "fabric.mod.json") or {}
    mixin_cfgs = []
    mj = fmj.get("mixins", [])
    if isinstance(mj, str):
        mj = [mj]
    for e in mj:
        mixin_cfgs.append(e if isinstance(e, str) else e.get("config"))
    for n in names:
        if "/" not in n and n.endswith(".json") and ("mixins" in n or "mixin" in n.split(".")) and n not in mixin_cfgs:
            mixin_cfgs.append(n)
    aws = []
    aw = fmj.get("accessWidener")
    if isinstance(aw, str):
        aws.append(aw)
    for n in names:
        if n.endswith(".accesswidener") and n not in aws:
            aws.append(n)

    # --- mixin configs
    mixin_classes = {}
    refmaps = {}
    for cfg in mixin_cfgs:
        j = read_json(z, cfg)
        if not isinstance(j, dict):
            continue
        pkg = j.get("package", "").replace(".", "/")
        for key in ("mixins", "client", "server"):
            for m in j.get(key, []) or []:
                mixin_classes[pkg + "/" + m.replace(".", "/")] = cfg
        rm = j.get("refmap")
        if rm:
            r = read_json(z, rm)
            if r:
                refmaps[cfg] = r.get("mappings", {})
    mixin_set = set(mixin_classes)

    # --- classes
    for n in sorted(names):
        if not n.endswith(".class") or n.startswith("META-INF/versions/"):
            continue
        try:
            c = parse_class(z.read(n), want_annos=True, want_refs=True)
        except Exception as e:
            col.notes.append("unparsable class %s in %s: %s" % (n, label, e))
            continue
        col.stats["classes"] += 1
        where = "%s" % c.name
        # pool references
        for kind, owner, nm, d in c.pool_refs:
            if owner.startswith("["):
                # array class constant: strip to element type for class refs; skip members on arrays
                if kind != "class":
                    continue
                el = owner.lstrip("[")
                if el.startswith("L") and el.endswith(";"):
                    owner = el[1:-1]
                else:
                    continue
            if owner in col.own:
                continue
            if in_scope(owner, ()):
                col.add(kind, owner, nm, d, "pool:" + where)
        # own-declaration descriptor classes, supers
        if c.super and in_scope(c.super, ()) and c.super not in col.own:
            col.add("class", c.super, None, None, "extends:" + where)
        for i in c.ifaces:
            if in_scope(i, ()) and i not in col.own:
                col.add("class", i, None, None, "implements:" + where)
        for nm, d, acc, an in c.fields + c.methods:
            for k in DESC_CLS.findall(d):
                if in_scope(k, ()) and k not in col.own:
                    col.add("class", k, None, None, "decl-desc:" + where)
        for a in c.annos:
            t = a["type"][1:-1]
            if in_scope(t, ()) and t not in col.own:
                col.add("class", t, None, None, "annotation:" + where)
        # mixins
        if c.name in mixin_set:
            cfg = mixin_classes[c.name]
            collect_mixin(col, c, refmaps.get(cfg, {}).get(c.name, {}))

    # --- access wideners
    for awn in aws:
        try:
            txt = z.read(awn).decode("utf-8", "replace")
        except KeyError:
            continue
        ns = "?"
        for ln in txt.splitlines():
            ln = ln.split("#")[0].strip()
            if not ln:
                continue
            p = ln.split()
            if p[0] == "accessWidener":
                ns = p[2] if len(p) > 2 else "?"
                col.notes.append("access widener %s namespace=%s" % (awn, ns))
                continue
            if len(p) >= 3 and p[1] == "class":
                col.add("class", p[2], None, None, "aw:" + awn)
            elif len(p) >= 5 and p[1] in ("method", "field"):
                col.add(p[1], p[2], p[3], p[4], "aw:" + awn)
            elif len(p) >= 3 and p[0] in ("accessible", "extendable", "mutable", "transitive-accessible") and len(p) == 3:
                pass


def collect_mixin(col, c, refmap):
    def rm(s):
        return refmap.get(s, s)

    targets = []   # internal names
    mixin_anno = next((a for a in c.annos if a["type"] == "L" + MIXIN_PKG + "Mixin;"), None)
    if not mixin_anno:
        return
    for t in anno_class_list(mixin_anno["vals"].get("value")):
        targets.append(t[1:-1] if t.startswith("L") else t)
    for t in anno_str_list(mixin_anno["vals"].get("targets")):
        targets.append(rm(t).replace(".", "/"))
    scoped = [t for t in targets if in_scope(t, ())]
    where_c = "mixin:" + c.name
    for t in scoped:
        col.add_mixin(c.name, "class", t, None, None, "@Mixin", where_c + "@Mixin")

    def add_member(owner_list, name, desc, is_field, role, kind_override=None):
        for t in owner_list:
            kind = kind_override or ("field" if is_field else "mixin-method")
            col.add_mixin(c.name, kind, t, name, desc, role, where_c + role)

    def handle_target_str(s, owners, role, is_at=False):
        """s may be qualified (Lowner;name(desc)) or a plain method name for the mixin targets."""
        s = rm(s)
        if re.search(r"[*]|^/.*/$", s):
            col.add_mixin(c.name, "mixin-method", owners[0] if owners else "?", s, None, role, where_c + role)
            return
        ow, nm, ds, isf = split_target(s)
        if ow is not None:
            if in_scope(ow, ()) and ow not in col.own:
                kind = "field" if isf else "method"
                col.add_mixin(c.name, kind, ow, nm, ds, role, where_c + role)
        else:
            if is_at:
                return
            if not nm:
                return
            for t in owners:
                col.add_mixin(c.name, "mixin-method", t, nm, ds, role, where_c + role)

    def walk(a, owners, ctx):
        t = a["type"][1:-1]
        short = t.rsplit("/", 1)[-1]
        vals = a["vals"]
        if t == MIXIN_PKG + "injection/At":
            for tg in anno_str_list(vals.get("target")):
                if tg:
                    handle_target_str(tg, owners, ctx + ".at.target", is_at=True)
            return
        if short == "Definition" or t.endswith("/Definition"):
            for tg in anno_str_list(vals.get("method")):
                handle_target_str(tg, owners, ctx + ".definition.method", is_at=True)
            for tg in anno_str_list(vals.get("field")):
                handle_target_str(tg, owners, ctx + ".definition.field", is_at=True)
            for tc in anno_class_list(vals.get("type")):
                k = tc[1:-1] if tc.startswith("L") else tc
                if in_scope(k, ()):
                    col.add_mixin(c.name, "class", k, None, None, ctx + ".definition.type", where_c + ctx + ".definition.type")
            return
        if t.startswith(MIXIN_PKG) or t.startswith(MX_PKG):
            for tg in anno_str_list(vals.get("method")):
                handle_target_str(tg, owners, ctx, False)
            for k, v in vals.items():
                for sub in anno_list(v):
                    walk(sub, owners, ctx)
            if "value" in vals:
                for tc in anno_class_list(vals["value"]):
                    pass

    for f in c.fields:
        for a in f[3]:
            t = a["type"][1:-1]
            if t == MIXIN_PKG + "Shadow":
                nm = rm(f[0])
                pre = (anno_str_list(a["vals"].get("prefix")) or ["shadow$"])[0]
                if nm.startswith(pre):
                    nm = nm[len(pre):]
                add_member(scoped, nm, f[1], True, "@Shadow", "field")
            elif t.startswith(MIXIN_PKG) or t.startswith(MX_PKG):
                walk(a, scoped, "@" + t.rsplit("/", 1)[-1])
    for m in c.methods:
        nm0, d0, acc, annos = m
        for a in annos:
            t = a["type"][1:-1]
            short = t.rsplit("/", 1)[-1]
            if t == MIXIN_PKG + "Shadow":
                nm = rm(nm0)
                pre = (anno_str_list(a["vals"].get("prefix")) or ["shadow$"])[0]
                if nm.startswith(pre):
                    nm = nm[len(pre):]
                add_member(scoped, nm, d0, False, "@Shadow", "mixin-method")
            elif t == MIXIN_PKG + "Overwrite":
                add_member(scoped, nm0, d0, False, "@Overwrite", "mixin-method")
            elif t in (MIXIN_PKG + "gen/Accessor", MIXIN_PKG + "gen/Invoker"):
                role = "@" + short
                val = anno_str_list(a["vals"].get("value"))
                if val:
                    target = rm(val[0])
                else:
                    mm = re.match(r"^(get|set|is|call|invoke)([A-Za-z0-9_$]+)$", nm0)
                    if mm:
                        s = mm.group(2)
                        target = s[0].lower() + s[1:]
                    else:
                        target = nm0
                if short == "Accessor":
                    if nm0.startswith("set") or d0.endswith(")V"):
                        args = re.match(r"\((.*)\)", d0).group(1)
                        fd = args
                    else:
                        fd = d0[d0.index(")") + 1:]
                    add_member(scoped, target, fd, True, role, "field")
                else:
                    add_member(scoped, target, d0, False, role, "mixin-method")
            elif t.startswith(MIXIN_PKG) or t.startswith(MX_PKG):
                walk(a, scoped, "@" + short)


# --------------------------------------------------------------------------- mapping of refs

def map_ref(M, kind, owner, name, desc):
    """intermediary ref -> 1.21.11 Mojang-named ref. Returns (owner, name, desc)."""
    if owner is None:
        return None, name, desc
    mo = M.cls(owner) if not owner.startswith("[") else owner
    if kind == "class":
        return mo, name, desc
    mdesc = M.desc(desc) if desc else desc
    if kind in ("field",):
        if desc is None:
            return mo, M.field(owner, name), mdesc
        return mo, M.field(owner, name), mdesc
    if name in ("<init>", "<clinit>") or name is None:
        return mo, name, mdesc
    if re.search(r"[*]", name or ""):
        return mo, name, mdesc
    return mo, M.method(owner, name, desc), mdesc


# --------------------------------------------------------------------------- checking

class Checker:
    def __init__(self, idx):
        self.idx = idx

    def check(self, kind, owner, name, desc):
        """Return (status, detail_26_3, candidates list)."""
        idx = self.idx
        if kind in ("class", "aw-class"):
            if owner in idx.cls:
                return OK, "", []
            return self.class_missing(owner)
        if kind == "mixin-method" and name and "*" in name:
            return SKIPPED, "wildcard/regex target", []
        if owner not in idx.cls:
            st, det, cands = self.class_missing(owner)
            # see if the member exists on the moved class
            extra = []
            for c in cands:
                if c in idx.cls:
                    ms = members_in(idx, c, "field" if kind == "field" else "method").get(name)
                    if ms is not None:
                        extra.append("%s.%s%s" % (c, name, " " + "|".join(sorted(ms))))
            return st, det, (extra + cands)[:3]
        fk = "field" if kind == "field" else "method"
        chain, unknown = idx.chain(owner)
        if kind == "mixin-method":
            chain_use = [owner]
        else:
            chain_use = chain
            if name == "<init>":
                chain_use = [owner]
        found = []
        for cl in chain_use:
            ms = members_in(idx, cl, fk).get(name)
            if ms:
                found.append((cl, ms))
        if found:
            if desc is None or desc == "":
                return OK, "", []
            for cl, ms in found:
                if desc in ms:
                    return OK, "", []
            alts = ["%s.%s%s" % (cl, name, d) for cl, ms in found for d in sorted(ms)]
            miss = [k for k in DESC_CLS.findall(desc) if k not in idx.cls and k.startswith(CHECKED_PREFIXES)]
            det = " ; ".join(alts[:3])
            if miss:
                det += "  [desc classes absent in 26.3: %s]" % ", ".join(miss[:3])
            return DESC_CHANGED, det, self.suggest_member(owner, name, desc, fk, chain)
        if kind == "mixin-method":
            inh = []
            for cl in chain[1:]:
                if members_in(idx, cl, "method").get(name):
                    inh.append(cl)
            if inh:
                return MEMBER_MISSING, "only inherited (from %s); mixin targets must be declared on the class" % inh[0], \
                    self.suggest_member(owner, name, desc, fk, chain)
        if unknown and not (kind == "mixin-method"):
            return UNVERIFIED, "owner chain leaves the index", []
        return MEMBER_MISSING, "", self.suggest_member(owner, name, desc, fk, chain)

    def class_missing(self, owner):
        idx = self.idx
        simple = owner.rsplit("/", 1)[-1]
        same = [c for c in idx.simple.get(simple, []) if c != owner]
        if same:
            return CLASS_MOVED, "", self.rank_pkg(owner, same)[:3]
        cands = []
        for suf in ("s", "es"):
            cands += [c for c in idx.simple.get(simple + suf, [])]
        if simple.endswith("s"):
            cands += idx.simple.get(simple[:-1], [])
        names = list(idx.simple.keys())
        close = difflib.get_close_matches(simple, names, n=3, cutoff=0.78)
        for cl in close:
            cands += idx.simple[cl]
        cands = self.rank_pkg(owner, list(dict.fromkeys(cands)))
        return CLASS_MISSING, "", cands[:3]

    @staticmethod
    def rank_pkg(owner, cs):
        op = owner.rsplit("/", 1)[0].split("/")

        def score(c):
            cp = c.rsplit("/", 1)[0].split("/")
            n = 0
            for a, b in zip(op, cp):
                if a != b:
                    break
                n += 1
            return -n
        return sorted(cs, key=score)

    def suggest_member(self, owner, name, desc, fk, chain):
        idx = self.idx
        out = []
        tag = "f" if fk == "field" else "m"
        simple = owner.rsplit("/", 1)[-1]
        pkg = owner.rsplit("/", 1)[0]
        # a) plural holder (EntityType.X -> EntityTypes.X) and the moved owner
        holders = []
        for suf in ("s", "es"):
            holders += idx.simple.get(simple + suf, [])
        holders += [c for c in idx.simple.get(simple, []) if c != owner]
        if simple.endswith("s"):
            holders += idx.simple.get(simple[:-1], [])
        for h in self.rank_pkg(owner, holders):
            ms = members_in(idx, h, fk).get(name)
            if ms:
                for d in sorted(ms):
                    out.append("%s.%s %s" % (h, name, d))
                    break
        # b) same name+desc elsewhere, same package first
        if len(name) >= 4 and not name.startswith("<"):
            hits = [(c, d) for c, t, d in idx.by_member.get(name, []) if t == tag and c != owner]
            exact = [c for c, d in hits if d == desc]
            for c in self.rank_pkg(owner, exact)[:2]:
                out.append("%s.%s %s" % (c, name, desc))
        # c) chain: similar names, or same descriptor with a similar name
        pool = []
        for cl in chain:
            for mn, ds in members_in(idx, cl, fk).items():
                if mn.startswith("<") or mn.startswith("lambda$"):
                    continue
                pool.append((cl, mn, ds))
        names = list(dict.fromkeys(p[1] for p in pool))
        for mn in difflib.get_close_matches(name, names, n=3, cutoff=0.6):
            for cl, n2, ds in pool:
                if n2 == mn:
                    out.append("%s.%s %s" % (cl, mn, sorted(ds)[0]))
                    break
        if desc and len(desc) > 8:
            same = [(cl, mn) for cl, mn, ds in pool if desc in ds and mn != name]
            if len(same) <= 2:
                for cl, mn in same:
                    out.append("%s.%s %s" % (cl, mn, desc))
        res = []
        for x in out:
            if x not in res:
                res.append(x)
        return res[:3]


# --------------------------------------------------------------------------- report

def short(s, n=140):
    return s if len(s) <= n else s[:n - 3] + "..."


def fmt_ref(kind, owner, name, desc):
    if kind in ("class", "aw-class"):
        return owner
    sep = " " if kind == "field" else ""
    return "%s.%s%s%s" % (owner, name, sep if desc else "", desc or "")


def main():
    if len(sys.argv) < 4:
        print(__doc__)
        sys.exit(2)
    ref_dir, out_dir, jars = sys.argv[1], sys.argv[2], sys.argv[3:]
    os.makedirs(out_dir, exist_ok=True)
    mc = os.path.join(ref_dir, "mc")
    M = Mappings(os.path.join(mc, "iv2/mappings/mappings.tiny"),
                 [os.path.join(mc, "client_mappings_1.21.11.txt"), os.path.join(mc, "server_mappings_1.21.11.txt")])
    print("mappings: %d inter classes, %d official->mojang classes" % (len(M.i2o), len(M.o2m)))
    idx = Index()
    idx.add_jar(os.path.join(mc, "client-26.3.jar"), "minecraft-client-26.3")
    idx.add_jar(os.path.join(mc, "server-inner-26.3.jar"), "minecraft-server-26.3")
    libdir = os.path.join(ref_dir, "libs")
    for f in sorted(os.listdir(libdir)):
        if f.endswith(".jar"):
            idx.add_jar(os.path.join(libdir, f), f.rsplit(".jar", 1)[0])
    jm = os.path.join(ref_dir, "jdk", "java.base.jmod")
    if os.path.exists(jm):
        idx.add_jar(jm, "jdk-java.base")
    idx.finish()
    print("26.3 index: %d classes" % len(idx.cls))
    for o, n in idx.jars:
        print("   %-28s +%d" % (o, n))
    chk = Checker(idx)
    summary = {}
    for jp in jars:
        col = collect_jar(jp, None)
        mod = re.sub(r"[^A-Za-z0-9]+", "-", os.path.basename(jp).rsplit(".jar", 1)[0]).strip("-")
        # 26.x jars are built against Mojang names already; only 1.21.11 jars carry intermediary.
        intermediary = any(o and o.startswith("net/minecraft/class_") for (_, o, _, _) in col.refs)
        print("%s: %d classes, %d unique raw refs, %s names" % (mod, col.stats["classes"], len(col.refs),
                                                               "intermediary" if intermediary else "mojang"))
        rows = {}
        for (kind, owner, name, desc), wh in col.refs.items():
            if intermediary:
                mo, mn, md = map_ref(M, kind if kind != "mixin-method" else "method", owner, name, desc)
            else:
                mo, mn, md = owner, name, desc
            if owner.startswith(NO_BUILD_PREFIXES):
                st, det, cands = NO_LIB, "library without a 26.3 build", []
            else:
                st, det, cands = chk.check(kind, mo, mn, md)
            rows[(kind, owner, name, desc)] = dict(kind=kind, inter=fmt_ref(kind, owner, name, desc), moj=fmt_ref(kind, mo, mn, md),
                                                    status=st, detail=det, cands=cands, where=sorted(wh), area=area_of(mo),
                                                    mo=mo, mn=mn, md=md)
        # mixin rollup
        mix_rows = []
        for mx, items in sorted(col.mixins.items()):
            worst, cnt = OK, defaultdict(int)
            bad = []
            for (kind, owner, name, desc, role) in items:
                r = rows.get((kind, owner, name, desc))
                if not r:
                    continue
                cnt[r["status"]] += 1
                if SEVERITY[r["status"]] > SEVERITY[worst]:
                    worst = r["status"]
                if r["status"] != OK:
                    bad.append("%s %s (%s)" % (r["status"], r["moj"], role))
            mix_rows.append((mx, worst, dict(cnt), sorted(bad)))
        # csv
        path = os.path.join(out_dir, mod + ".csv")
        with open(path, "w", newline="", encoding="utf-8") as f:
            w = csv.writer(f)
            w.writerow(["kind", "intermediary_name", "mojang_1.21.11_name", "status", "26.3_candidates", "detail_26.3", "area", "used_in"])
            for r in sorted(rows.values(), key=lambda r: (SEVERITY[r["status"]] * -1, r["area"], r["moj"])):
                wh = r["where"]
                used = "; ".join(wh[:6]) + ("; (+%d more)" % (len(wh) - 6) if len(wh) > 6 else "")
                w.writerow([r["kind"], r["inter"], r["moj"], r["status"], " | ".join(r["cands"]), r["detail"], r["area"], used])
        summary[mod] = dict(rows=rows, mixins=mix_rows, notes=col.notes, jar=jp)
        print("   wrote", path)
    write_summary(summary, idx, os.path.join(out_dir, "SUMMARY.md"))


def write_summary(summary, idx, path):
    L = []
    L.append("# uproot-map summary: 1.21.11 mod references checked against Minecraft 26.3\n")
    L.append("Generated by uproot_map.py. Statuses: OK, DESC_CHANGED, MEMBER_MISSING, CLASS_MOVED, CLASS_MISSING; extras: "
             "UNVERIFIED (owner chain leaves the index), NO_26_3_LIB (library with no 26.3 build, not checked), "
             "SKIPPED_PATTERN (wildcard or regex mixin target).\n")
    L.append("26.3 index: " + ", ".join("%s (%d classes)" % (o, n) for o, n in idx.jars) + "\n")
    L.append("## Counts per status\n")
    mods = list(summary)
    L.append("| status | " + " | ".join(mods) + " |")
    L.append("|---|" + "---|" * len(mods))
    for st in STATUS_ORDER:
        L.append("| %s | " % st + " | ".join(str(sum(1 for r in summary[m]["rows"].values() if r["status"] == st)) for m in mods) + " |")
    L.append("| **total** | " + " | ".join(str(len(summary[m]["rows"])) for m in mods) + " |\n")
    L.append("### By kind (non-OK / total)\n")
    kinds = sorted({r["kind"] for m in mods for r in summary[m]["rows"].values()})
    L.append("| kind | " + " | ".join(mods) + " |")
    L.append("|---|" + "---|" * len(mods))
    for k in kinds:
        cells = []
        for m in mods:
            rs = [r for r in summary[m]["rows"].values() if r["kind"] == k]
            cells.append("%d / %d" % (sum(1 for r in rs if r["status"] != OK), len(rs)))
        L.append("| %s | " % k + " | ".join(cells) + " |")
    L.append("")
    for m in mods:
        S = summary[m]
        L.append("## %s\n" % m)
        for n in S["notes"]:
            L.append("- note: " + n)
        L.append("")
        bad = [r for r in S["rows"].values() if r["status"] != OK]
        byarea = defaultdict(list)
        for r in bad:
            byarea[r["area"]].append(r)
        L.append("### Non-OK references by 26.3 area (%d)\n" % len(bad))
        L.append("Area totals: " + ", ".join("%s %d" % (a, len(v)) for a, v in sorted(byarea.items(), key=lambda kv: -len(kv[1]))) + "\n")
        for a, v in sorted(byarea.items(), key=lambda kv: -len(kv[1])):
            L.append("#### %s (%d)\n" % (a, len(v)))
            for r in sorted(v, key=lambda r: (r["mo"], r["moj"])):
                c = " -> " + " | ".join(r["cands"]) if r["cands"] else ""
                d = " [26.3: %s]" % short(r["detail"], 200) if r["detail"] else ""
                L.append("- `%s` %s `%s`%s%s" % (r["status"], r["kind"], r["moj"], d, c))
            L.append("")
        L.append("### Mixins and their status (%d)\n" % len(S["mixins"]))
        L.append("Status is the worst status among the mixin's target class, method targets, @At targets, @Shadow, @Accessor and @Invoker members.\n")
        L.append("| mixin | status | counts | problems |")
        L.append("|---|---|---|---|")
        for mx, worst, cnt, badl in sorted(S["mixins"], key=lambda x: (-SEVERITY[x[1]], x[0])):
            L.append("| %s | %s | %s | %s |" % (mx, worst, ", ".join("%s %d" % kv for kv in sorted(cnt.items())),
                                                 short("<br>".join(badl[:6]) + (" ..." if len(badl) > 6 else ""), 600)))
        L.append("")
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(L))
    print("wrote", path)


if __name__ == "__main__":
    main()
