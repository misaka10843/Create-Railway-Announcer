import fs from "node:fs";
import path from "node:path";

const roots = ["src/main/resources", "src/generated/resources"];
const files = [];

function walk(directory) {
    if (!fs.existsSync(directory)) return;
    for (const entry of fs.readdirSync(directory, {withFileTypes: true})) {
        const target = path.join(directory, entry.name);
        if (entry.isDirectory()) walk(target);
        else files.push(target);
    }
}

roots.forEach(walk);

const jsonFiles = files.filter(file => file.endsWith(".json"));
const documents = new Map();
const parseErrors = [];
for (const file of jsonFiles) {
    try {
        documents.set(file, JSON.parse(fs.readFileSync(file, "utf8")));
    } catch (error) {
        parseErrors.push(`${file}: ${error.message}`);
    }
}

const models = new Set();
const textures = new Set();
for (const file of files) {
    const normalized = file.replaceAll("\\", "/");
    let match = normalized.match(/\/assets\/([^/]+)\/models\/(.+)\.json$/);
    if (match) models.add(`${match[1]}:${match[2]}`);
    match = normalized.match(/\/assets\/([^/]+)\/textures\/(.+)\.png$/);
    if (match) textures.add(`${match[1]}:${match[2]}`);
}

function valuesForKey(value, key, output = []) {
    if (!value || typeof value !== "object") return output;
    if (Array.isArray(value)) {
        value.forEach(item => valuesForKey(item, key, output));
        return output;
    }
    for (const [entryKey, entryValue] of Object.entries(value)) {
        if (entryKey === key && typeof entryValue === "string") output.push(entryValue);
        valuesForKey(entryValue, key, output);
    }
    return output;
}

const missingReferences = [];
for (const [file, document] of documents) {
    const normalized = file.replaceAll("\\", "/");
    if (normalized.includes("/assets/create_railway_announcer/blockstates/")) {
        for (const reference of valuesForKey(document, "model")) {
            const id = reference.includes(":") ? reference : `create_railway_announcer:${reference}`;
            if (id.startsWith("create_railway_announcer:") && !models.has(id)) {
                missingReferences.push(`model ${id} <- ${file}`);
            }
        }
    }
    if (!normalized.includes("/assets/create_railway_announcer/models/")) continue;

    if (typeof document.parent === "string") {
        const id = document.parent.includes(":") ? document.parent : `minecraft:${document.parent}`;
        if (id.startsWith("create_railway_announcer:") && !models.has(id)) {
            missingReferences.push(`parent ${id} <- ${file}`);
        }
    }
    if (!document.textures || typeof document.textures !== "object") continue;
    for (const reference of Object.values(document.textures)) {
        if (typeof reference !== "string" || reference.startsWith("#")) continue;
        const id = reference.includes(":") ? reference : `minecraft:${reference}`;
        if (id.startsWith("create_railway_announcer:") && !textures.has(id)) {
            missingReferences.push(`texture ${id} <- ${file}`);
        }
    }
}

/**
 * 面的 texture 只能是 textures 映射里的变量引用，且必须能解析到。
 *
 * 原版 BlockModel.getMaterial(name) 的规则是：以 `#` 开头才剥掉井号，然后拿结果去 textures
 * 映射里查键；查不到就返回 MissingTextureAtlasSprite。也就是说写纹理路径字面量不会报错——
 * 它只是查不到那个「键」，于是那些面全部变成紫黑缺失纹理，模型照样「烤成功」，日志里一个字
 * 都没有。这条是静态可查的，所以放在这里而不是留给眼睛。
 */
const modelEntries = new Map();
for (const [file, document] of documents) {
    const normalized = file.replaceAll("\\", "/");
    const match = normalized.match(/\/assets\/([^/]+)\/models\/(.+)\.json$/);
    if (!match) continue;
    const declared = new Map();
    if (document.textures && typeof document.textures === "object") {
        for (const [key, value] of Object.entries(document.textures)) {
            if (typeof value === "string") declared.set(key, value);
        }
    }
    modelEntries.set(`${match[1]}:${match[2]}`, {
        parent: typeof document.parent === "string" ? document.parent : null,
        declared,
        faces: valuesForKey(document, "texture"),
    });
}

const badFaceTextures = [];
const unresolvedFaceTextures = [];

/**
 * 变量由「本模型或它的某个后代」声明即可，不是往上找。
 *
 * 本仓库的牌面模型是碎片式的：`sign/block/body` 只写 elements 用 `#frame`，本身既不声明
 * textures 也不带 parent，靠 andesite_alloy/body 这类材质子模型 parent 到它、在子模型里声明
 * `frame`。烘焙发生在子模型上，而 BlockModel.getMaterial 是从被烘焙的那个模型往**上**走的，
 * 所以碎片里的变量在那一刻是能查到的。反过来说，一个模型的面变量如果在本模型和所有后代里
 * 都没人声明，那就真的没有哪个模型能把它烤出来。
 */
function declaredClosure(id, memo = new Map(), seen = new Set()) {
    if (memo.has(id)) return memo.get(id);
    if (seen.has(id)) return new Set();
    seen.add(id);
    const entry = modelEntries.get(id);
    const names = new Set(entry ? entry.declared.keys() : []);
    for (const [childId, child] of modelEntries) {
        if (child.parent === null) continue;
        const parentId = child.parent.includes(":") ? child.parent : `minecraft:${child.parent}`;
        if (parentId !== id) continue;
        for (const name of declaredClosure(childId, memo, seen)) names.add(name);
    }
    seen.delete(id);
    memo.set(id, names);
    return names;
}

for (const [id, entry] of modelEntries) {
    const available = declaredClosure(id);
    for (const value of entry.faces) {
        if (!value.startsWith("#")) {
            badFaceTextures.push(`face texture "${value}" is a literal path, not a #variable <- ${id}`);
            continue;
        }
        if (!available.has(value.slice(1))) {
            unresolvedFaceTextures.push(
                `face texture ${value} is declared by neither this model nor any of its children <- ${id}`);
        }
    }
}

/**
 * 元素的 from / to 不许越过原版的硬边界。
 *
 * `BlockElement` 里 `MIN_EXTENT = -16`、`MAX_EXTENT = 32`，越了就直接抛 JsonParseException，
 * 而且抛在**整份模型**那一层：模型不是画歪，是整份加载失败，方块变成紫黑缺失纹理。日志里只有
 * 一行 "Failed to load model ..." 和一句 "'to' specifier exceeds the allowed boundaries"，不
 * 进游戏根本看不到。
 *
 * 本仓库踩过一次：售票机源模型是一个整机（y 0..35），拆成上下两半时上半没有减 16，于是 y 到
 * 35，上半和物品模型一起整份失败。这条是静态可查的，所以放在这里——比进游戏发现一块紫方块快。
 *
 * 注意边界是 32 而不是 16：模型本来就可以探出方块（本仓库的信号机柜体就到 z=26.5），
 * 这里只拦原版真正会拒掉的那些。
 */
const MAX_EXTENT = 32.0;
const MIN_EXTENT = -16.0;
const outOfBounds = [];
const invalidRotations = [];
const invalidUvs = [];
for (const [file, document] of documents) {
    if (!Array.isArray(document.elements)) continue;
    const normalized = file.replaceAll("\\", "/");
    for (const [index, element] of document.elements.entries()) {
        for (const [side, face] of Object.entries(element.faces ?? {})) {
            if (face.uv && (face.uv.length !== 4 || face.uv.some(value => !Number.isFinite(value) || value < 0 || value > 16))) {
                invalidUvs.push(`UV outside sprite 0..16 at element ${index}, ${side}; samples adjacent atlas sprites <- ${normalized}`);
            }
        }
        const rotation = element.rotation;
        if (rotation && (!['x', 'y', 'z'].includes(rotation.axis)
                || ![-45, -22.5, 0, 22.5, 45].includes(rotation.angle)
                || ['x', 'y', 'z'].some(axis => axis in rotation)
                || !Array.isArray(rotation.origin) || rotation.origin.length !== 3
                || rotation.origin.some(value => !Number.isFinite(value)))) {
            invalidRotations.push(`invalid 1.21.1 element rotation at ${index} (${element.name ?? 'unnamed'}); `
                    + `use axis/angle and bake quarter turns into geometry <- ${normalized}`);
        }
        for (const key of ["from", "to"]) {
            const vector = element[key];
            if (!Array.isArray(vector)) continue;
            for (const [axis, value] of vector.entries()) {
                if (typeof value !== "number") continue;
                if (value > MAX_EXTENT || value < MIN_EXTENT) {
                    outOfBounds.push(
                        `element ${index} (${element.name ?? "unnamed"}) ${key}[${axis}] = ${value} `
                        + `is outside ${MIN_EXTENT}..${MAX_EXTENT}; vanilla rejects the whole model <- ${normalized}`);
                }
            }
        }
    }
}

console.log([
    `json=${jsonFiles.length}`,
    `parse_errors=${parseErrors.length}`,
    `local_models=${models.size}`,
    `local_textures=${textures.size}`,
    `missing_local_refs=${missingReferences.length}`,
    `literal_face_textures=${badFaceTextures.length}`,
    `unresolved_face_textures=${unresolvedFaceTextures.length}`,
    `out_of_bounds_elements=${outOfBounds.length}`,
    `invalid_element_rotations=${invalidRotations.length}`,
    `invalid_face_uvs=${invalidUvs.length}`
].join(" "));

const failures = [...parseErrors, ...missingReferences, ...badFaceTextures, ...unresolvedFaceTextures,
    ...outOfBounds, ...invalidRotations, ...invalidUvs];
for (const error of failures) console.error(error);
if (failures.length) process.exitCode = 1;
