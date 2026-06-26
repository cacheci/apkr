use crate::{arsc::ResourceTable, axml::{parse_binary_manifest, XmlNode}, utils::human_size};
use flate2::read::DeflateDecoder;
use regex::Regex;
use serde::Serialize;
use std::{collections::BTreeSet, fs, io::Read, path::Path};

const EOCD_SIGNATURE: u32 = 0x0605_4b50;
const ZIP64_EOCD_SIGNATURE: u32 = 0x0606_4b50;
const ZIP64_EOCD_LOCATOR_SIGNATURE: u32 = 0x0706_4b50;
const CENTRAL_DIRECTORY_SIGNATURE: u32 = 0x0201_4b50;
const LOCAL_FILE_SIGNATURE: u32 = 0x0403_4b50;
const ZIP64_EXTRA_ID: u16 = 0x0001;
const METHOD_STORED: u16 = 0;
const METHOD_DEFLATED: u16 = 8;

#[derive(Debug, Serialize)]
pub struct ApkInfo {
    path: String,
    file_name: String,
    size: String,
    package_name: String,
    version_name: String,
    version_code: String,
    min_sdk: String,
    target_sdk: String,
    compile_sdk: String,
    app_label: String,
    resolved_app_label: String,
    app_icon: String,
    resolved_app_icon: String,
    app_icon_data_url: String,
    supported_languages: Vec<String>,
    debuggable: String,
    permissions: Vec<String>,
    activities: Vec<String>,
    services: Vec<String>,
    receivers: Vec<String>,
    providers: Vec<String>,
    native_libs: Vec<String>,
    abis: Vec<String>,
    signatures: Vec<String>,
    file_count: usize,
    tech_features: Vec<TechFeature>,
}

#[derive(Debug, Serialize)]
pub struct TechFeature {
    name: String,
    icon: String,
}

const SIMPLE_ICONS_CDN: &str = "https://cdn.simpleicons.org";

#[derive(Debug, Clone)]
struct ZipEntry {
    name: String,
    compression_method: u16,
    compressed_size: usize,
    uncompressed_size: usize,
    local_header_offset: usize,
}

pub fn parse_apk_file(path: &str) -> Result<ApkInfo, Box<dyn std::error::Error>> {
    let preferred_locale = std::env::var("LANG").unwrap_or_default();
    parse_apk_file_with_locale(path, &preferred_locale)
}

pub fn parse_apk_file_with_locale(
    path: &str,
    preferred_locale: &str,
) -> Result<ApkInfo, Box<dyn std::error::Error>> {
    let apk_path = Path::new(path);
    let bytes = fs::read(apk_path)?;
    let file_size = bytes.len() as u64;

    let (manifest_data, resource_table, names, entries) = if looks_like_binary_manifest(&bytes) {
        (bytes.as_slice().to_vec(), None, Vec::new(), None)
    } else {
        let entries = read_zip_entries(&bytes)?;
        let manifest_data = read_manifest_data(&bytes, &entries)?;
        let resource_table = entries
            .iter()
            .find(|entry| entry.name == "resources.arsc")
            .and_then(|entry| read_zip_entry_data(&bytes, entry).ok())
            .and_then(|data| ResourceTable::parse(&data).ok());
        let names = entries.iter().map(|entry| entry.name.clone()).collect();
        (manifest_data, resource_table, names, Some(entries))
    };

    let manifest = parse_binary_manifest(&manifest_data)?;
    let native_libs = collect_native_libs(&names);
    let abis = collect_abis(&native_libs);
    let signatures = collect_signatures(&names)?;
    let uses_sdk = manifest.children_named("uses-sdk").into_iter().next();
    let application = manifest.children_named("application").into_iter().next();
    let app_label = application
        .as_ref()
        .map(|node| node.android_attr("label"))
        .unwrap_or_default();
    let app_icon = application
        .as_ref()
        .map(|node| node.android_attr("icon"))
        .unwrap_or_default();
    let resolved_app_label = resource_table
        .as_ref()
        .and_then(|table| table.resolve_string(&app_label, &preferred_locale))
        .unwrap_or_default();
    let resolved_app_icon = resource_table
        .as_ref()
        .and_then(|table| table.resolve_icon_file(&app_icon, &preferred_locale))
        .unwrap_or_default();
    let app_icon_data_url = entries
        .as_ref()
        .and_then(|entries| icon_data_url(&bytes, entries, resource_table.as_ref(), &resolved_app_icon, &preferred_locale))
        .unwrap_or_default();
    let supported_languages = resource_table
        .as_ref()
        .map(ResourceTable::supported_languages)
        .unwrap_or_default();
    let tech_features = entries
        .as_ref()
        .map(|entries| detect_tech_features(&bytes, entries, &names, &native_libs))
        .unwrap_or_default();

    Ok(ApkInfo {
        path: path.to_owned(),
        file_name: apk_path
            .file_name()
            .and_then(|name| name.to_str())
            .unwrap_or(path)
            .to_owned(),
        size: human_size(file_size),
        package_name: manifest.attr("package"),
        version_name: manifest.android_attr("versionName"),
        version_code: manifest.android_attr("versionCode"),
        min_sdk: uses_sdk
            .as_ref()
            .map(|node| node.android_attr("minSdkVersion"))
            .unwrap_or_default(),
        target_sdk: uses_sdk
            .as_ref()
            .map(|node| node.android_attr("targetSdkVersion"))
            .unwrap_or_default(),
        compile_sdk: manifest.android_attr("compileSdkVersion"),
        app_label,
        resolved_app_label,
        app_icon,
        resolved_app_icon,
        app_icon_data_url,
        supported_languages,
        debuggable: application
            .as_ref()
            .map(|node| node.android_attr("debuggable"))
            .unwrap_or_default(),
        permissions: collect_permissions(&manifest),
        activities: collect_components(application, "activity"),
        services: collect_components(application, "service"),
        receivers: collect_components(application, "receiver"),
        providers: collect_components(application, "provider"),
        native_libs,
        abis,
        signatures,
        file_count: names.len(),
        tech_features,
    })
}

fn read_manifest_data(bytes: &[u8], entries: &[ZipEntry]) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
    if let Some(entry) = entries.iter().find(|entry| entry.name == "AndroidManifest.xml") {
        let data = read_zip_entry_data(bytes, entry)?;
        if parse_binary_manifest(&data)
            .map(|node| node.name == "manifest")
            .unwrap_or(false)
        {
            return Ok(data);
        }
    }

    entries
        .iter()
        .filter(|entry| entry.name.ends_with(".xml"))
        .filter_map(|entry| read_zip_entry_data(bytes, entry).ok())
        .find(|data| {
            parse_binary_manifest(data)
                .map(|node| node.name == "manifest")
                .unwrap_or(false)
        })
        .ok_or_else(|| "APK 中没有可识别的 AndroidManifest.xml".into())
}

fn looks_like_binary_manifest(bytes: &[u8]) -> bool {
    bytes.len() >= 8 && u16::from_le_bytes([bytes[0], bytes[1]]) == 0x0003
}

fn read_zip_entries(bytes: &[u8]) -> Result<Vec<ZipEntry>, Box<dyn std::error::Error>> {
    let eocd_offset = find_eocd(bytes).ok_or("不是有效 APK/ZIP：找不到中央目录")?;
    let (entry_count, central_dir_offset) = read_central_directory_info(bytes, eocd_offset)?;
    let mut offset = central_dir_offset;
    let mut entries = Vec::with_capacity(entry_count);

    for _ in 0..entry_count {
        if read_u32(bytes, offset)? != CENTRAL_DIRECTORY_SIGNATURE {
            return Err("ZIP 中央目录结构异常".into());
        }

        let compression_method = read_u16(bytes, offset + 10)?;
        let compressed_size = read_u32(bytes, offset + 20)? as usize;
        let uncompressed_size = read_u32(bytes, offset + 24)? as usize;
        let name_len = read_u16(bytes, offset + 28)? as usize;
        let extra_len = read_u16(bytes, offset + 30)? as usize;
        let comment_len = read_u16(bytes, offset + 32)? as usize;
        let local_header_offset = read_u32(bytes, offset + 42)? as usize;
        let name_start = offset + 46;
        let name_end = name_start + name_len;
        let name = String::from_utf8_lossy(get_range(bytes, name_start, name_end)?).to_string();
        let extra = get_range(bytes, name_end, name_end + extra_len)?;
        let (compressed_size, uncompressed_size, local_header_offset) =
            apply_zip64_extra(compressed_size, uncompressed_size, local_header_offset, extra)?;

        entries.push(ZipEntry { name, compression_method, compressed_size, uncompressed_size, local_header_offset });
        offset = name_end + extra_len + comment_len;
    }

    Ok(entries)
}

fn read_central_directory_info(
    bytes: &[u8],
    eocd_offset: usize,
) -> Result<(usize, usize), Box<dyn std::error::Error>> {
    let entry_count_16 = read_u16(bytes, eocd_offset + 10)?;
    let central_dir_offset_32 = read_u32(bytes, eocd_offset + 16)?;
    if entry_count_16 != u16::MAX && central_dir_offset_32 != u32::MAX {
        return Ok((entry_count_16 as usize, central_dir_offset_32 as usize));
    }

    let locator_offset = eocd_offset
        .checked_sub(20)
        .ok_or("ZIP64 EOCD locator 缺失")?;
    if read_u32(bytes, locator_offset)? != ZIP64_EOCD_LOCATOR_SIGNATURE {
        return Err("ZIP64 EOCD locator 缺失".into());
    }

    let zip64_eocd_offset = read_u64(bytes, locator_offset + 8)? as usize;
    if read_u32(bytes, zip64_eocd_offset)? != ZIP64_EOCD_SIGNATURE {
        return Err("ZIP64 EOCD 结构异常".into());
    }

    let entry_count = read_u64(bytes, zip64_eocd_offset + 32)? as usize;
    let central_dir_offset = read_u64(bytes, zip64_eocd_offset + 48)? as usize;
    Ok((entry_count, central_dir_offset))
}

fn apply_zip64_extra(
    compressed_size: usize,
    uncompressed_size: usize,
    local_header_offset: usize,
    extra: &[u8],
) -> Result<(usize, usize, usize), Box<dyn std::error::Error>> {
    let mut compressed_size = compressed_size;
    let mut uncompressed_size = uncompressed_size;
    let mut local_header_offset = local_header_offset;
    let mut offset = 0;

    while offset + 4 <= extra.len() {
        let header_id = u16::from_le_bytes([extra[offset], extra[offset + 1]]);
        let data_size = u16::from_le_bytes([extra[offset + 2], extra[offset + 3]]) as usize;
        let data_start = offset + 4;
        let data_end = data_start + data_size;
        if data_end > extra.len() {
            break;
        }

        if header_id == ZIP64_EXTRA_ID {
            let mut cursor = data_start;
            if uncompressed_size == u32::MAX as usize && cursor + 8 <= data_end {
                uncompressed_size = read_u64(extra, cursor)? as usize;
                cursor += 8;
            }
            if compressed_size == u32::MAX as usize && cursor + 8 <= data_end {
                compressed_size = read_u64(extra, cursor)? as usize;
                cursor += 8;
            }
            if local_header_offset == u32::MAX as usize && cursor + 8 <= data_end {
                local_header_offset = read_u64(extra, cursor)? as usize;
            }
        }

        offset = data_end;
    }

    Ok((compressed_size, uncompressed_size, local_header_offset))
}

fn read_zip_entry_data(bytes: &[u8], entry: &ZipEntry) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
    let offset = entry.local_header_offset;
    if read_u32(bytes, offset)? != LOCAL_FILE_SIGNATURE {
        return Err(format!("ZIP 本地文件头异常：{}", entry.name).into());
    }

    let name_len = read_u16(bytes, offset + 26)? as usize;
    let extra_len = read_u16(bytes, offset + 28)? as usize;
    let data_start = offset + 30 + name_len + extra_len;
    let stored_size = if entry.compression_method == METHOD_STORED && entry.uncompressed_size > entry.compressed_size {
        entry.uncompressed_size
    } else {
        entry.compressed_size
    };
    let data_end = data_start + stored_size;
    let compressed = get_range(bytes, data_start, data_end)?;

    match entry.compression_method {
        METHOD_STORED => Ok(compressed.to_vec()),
        METHOD_DEFLATED => {
            let mut decoder = DeflateDecoder::new(compressed);
            let mut out = Vec::new();
            decoder.read_to_end(&mut out)?;
            Ok(out)
        }
        method => Err(format!("不支持的 ZIP 压缩方式 {method}：{}", entry.name).into()),
    }
}

fn icon_data_url(
    bytes: &[u8],
    entries: &[ZipEntry],
    resource_table: Option<&ResourceTable>,
    icon_path: &str,
    preferred_locale: &str,
) -> Option<String> {
    if icon_path.is_empty() {
        return None;
    }

    let entry = entries.iter().find(|entry| entry.name == icon_path)?;
    let data = read_zip_entry_data(bytes, entry).ok()?;
    if let Some(mime) = image_mime_type(icon_path) {
        return Some(format!("data:{mime};base64,{}", base64_encode(&data)));
    }

    if !icon_path.ends_with(".xml") {
        return None;
    }

    let xml = parse_binary_manifest(&data).ok()?;
    if let Some(svg) = adaptive_icon_to_svg_data_url(bytes, entries, resource_table, &xml, preferred_locale) {
        return Some(svg);
    }

    if let Some(svg) = vector_to_svg_data_url(&xml, resource_table, preferred_locale) {
        return Some(svg);
    }

    let table = resource_table?;
    icon_resource_refs(&xml)
        .into_iter()
        .filter_map(|resource_ref| table.resolve_file(&resource_ref, preferred_locale))
        .filter(|path| path != icon_path)
        .find_map(|path| icon_data_url(bytes, entries, Some(table), &path, preferred_locale))
}

fn adaptive_icon_to_svg_data_url(
    bytes: &[u8],
    entries: &[ZipEntry],
    resource_table: Option<&ResourceTable>,
    node: &XmlNode,
    preferred_locale: &str,
) -> Option<String> {
    if node.name != "adaptive-icon" {
        return None;
    }

    let table = resource_table?;
    let background = adaptive_icon_layer(node, "background", table, preferred_locale);
    let background_color = adaptive_icon_layer_color(node, "background", table, preferred_locale);
    let foreground = adaptive_icon_layer(node, "foreground", table, preferred_locale)
        .or_else(|| adaptive_icon_layer(node, "monochrome", table, preferred_locale));

    let mut body = String::new();
    if let Some(color) = background_color {
        body.push_str(&format!(r#"<rect width="108" height="108" fill="{color}"/>"#));
    } else {
        body.push_str(r##"<rect width="108" height="108" fill="#f0f0f0"/>"##);
    }

    if let Some(layer) = background {
        body.push_str(&render_adaptive_layer(bytes, entries, table, &layer, preferred_locale, false).unwrap_or_default());
    }

    if let Some(layer) = foreground {
        let foreground_svg = render_adaptive_layer(bytes, entries, table, &layer, preferred_locale, true).unwrap_or_default();
        body.push_str(&foreground_svg);
    }

    let svg = format!(
        r#"<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108">{body}</svg>"#
    );
    Some(format!("data:image/svg+xml;base64,{}", base64_encode(svg.as_bytes())))
}

fn adaptive_icon_layer(
    node: &XmlNode,
    layer_name: &str,
    table: &ResourceTable,
    preferred_locale: &str,
) -> Option<String> {
    let child = node.children.iter().find(|child| child.name == layer_name)?;
    let drawable = child.android_attr("drawable");
    if drawable.is_empty() {
        return None;
    }

    table.resolve_file(&drawable, preferred_locale)
}

fn adaptive_icon_layer_color(
    node: &XmlNode,
    layer_name: &str,
    table: &ResourceTable,
    preferred_locale: &str,
) -> Option<String> {
    let child = node.children.iter().find(|child| child.name == layer_name)?;
    let drawable = child.android_attr("drawable");
    if drawable.is_empty() {
        return None;
    }

    svg_color(&drawable, Some(table), preferred_locale)
}

fn render_adaptive_layer(
    bytes: &[u8],
    entries: &[ZipEntry],
    table: &ResourceTable,
    path: &str,
    preferred_locale: &str,
    is_foreground: bool,
) -> Option<String> {
    let entry = entries.iter().find(|entry| entry.name == path)?;
    let data = read_zip_entry_data(bytes, entry).ok()?;

    if let Some(mime) = image_mime_type(path) {
        let href = format!("data:{mime};base64,{}", base64_encode(&data));
        return Some(format!(
            r#"<image href="{href}" x="0" y="0" width="108" height="108" preserveAspectRatio="xMidYMid meet"/>"#
        ));
    }

    if !path.ends_with(".xml") {
        return None;
    }

    let xml = parse_binary_manifest(&data).ok()?;
    if let Some(inset) = inset_drawable_to_svg(bytes, entries, table, &xml, preferred_locale, is_foreground) {
        return Some(inset);
    }

    if let Some(drawable) = shape_drawable_to_svg(&xml, table, preferred_locale) {
        return Some(drawable);
    }

    if xml.name == "vector" {
        let viewport = vector_viewport(&xml);
        let paths = vector_svg_markup(&xml, Some(table), preferred_locale)?;
        return Some(format!(
            r#"<svg x="0" y="0" width="108" height="108" viewBox="0 0 {} {}">{paths}</svg>"#,
            viewport.width, viewport.height
        ));
    }

    icon_resource_refs(&xml)
        .into_iter()
        .filter_map(|resource_ref| table.resolve_file(&resource_ref, preferred_locale))
        .filter(|resolved_path| resolved_path != path)
        .find_map(|resolved_path| render_adaptive_layer(bytes, entries, table, &resolved_path, preferred_locale, is_foreground))
}

fn inset_drawable_to_svg(
    bytes: &[u8],
    entries: &[ZipEntry],
    table: &ResourceTable,
    node: &XmlNode,
    preferred_locale: &str,
    is_foreground: bool,
) -> Option<String> {
    if node.name != "inset" {
        return None;
    }

    let drawable = node.android_attr("drawable");
    let path = table.resolve_file(&drawable, preferred_locale)?;
    let layer = render_adaptive_layer(bytes, entries, table, &path, preferred_locale, is_foreground)?;
    let left = parse_float_attr(node, "android:insetLeft").unwrap_or(0.0);
    let right = parse_float_attr(node, "android:insetRight").unwrap_or(0.0);
    let top = parse_float_attr(node, "android:insetTop").unwrap_or(0.0);
    let bottom = parse_float_attr(node, "android:insetBottom").unwrap_or(0.0);
    let width = (108.0 - left - right).max(0.0);
    let height = (108.0 - top - bottom).max(0.0);

    Some(format!(
        r#"<svg x="{left}" y="{top}" width="{width}" height="{height}" viewBox="0 0 108 108">{layer}</svg>"#
    ))
}

fn shape_drawable_to_svg(node: &XmlNode, table: &ResourceTable, preferred_locale: &str) -> Option<String> {
    if node.name == "shape" {
        if let Some(gradient) = node.children.iter().find(|child| child.name == "gradient") {
            return gradient_to_svg(gradient, table, preferred_locale);
        }

        if let Some(solid) = node.children.iter().find(|child| child.name == "solid") {
            let color = svg_color(&solid.android_attr("color"), Some(table), preferred_locale)?;
            return Some(format!(r#"<rect width="108" height="108" fill="{color}"/>"#));
        }
    }

    if node.name == "gradient" {
        return gradient_to_svg(node, table, preferred_locale);
    }

    node.children
        .iter()
        .find_map(|child| shape_drawable_to_svg(child, table, preferred_locale))
}

fn gradient_to_svg(node: &XmlNode, table: &ResourceTable, preferred_locale: &str) -> Option<String> {
    let id = "bg-gradient";
    let stops = gradient_stops(node, table, preferred_locale)?;
    let angle = parse_typed_float(&node.android_attr("angle")).unwrap_or(270.0);
    let (x1, y1, x2, y2) = gradient_vector(angle);
    let stop_markup = stops
        .into_iter()
        .map(|(offset, color)| format!(r#"<stop offset="{offset}%" stop-color="{color}"/>"#))
        .collect::<String>();

    Some(format!(
        r#"<defs><linearGradient id="{id}" x1="{x1}%" y1="{y1}%" x2="{x2}%" y2="{y2}%">{stop_markup}</linearGradient></defs><rect width="108" height="108" fill="url(#{id})"/>"#
    ))
}

fn gradient_stops(node: &XmlNode, table: &ResourceTable, preferred_locale: &str) -> Option<Vec<(f32, String)>> {
    let item_stops = node
        .children
        .iter()
        .filter(|child| child.name == "item")
        .filter_map(|child| {
            let color = svg_color(&child.android_attr("color"), Some(table), preferred_locale)?;
            let offset = parse_typed_float(&child.android_attr("offset")).unwrap_or(0.0) * 100.0;
            Some((offset, color))
        })
        .collect::<Vec<_>>();

    if !item_stops.is_empty() {
        return Some(item_stops);
    }

    let start = svg_color(&node.android_attr("startColor"), Some(table), preferred_locale)?;
    let end = svg_color(&node.android_attr("endColor"), Some(table), preferred_locale)?;
    let mut stops = vec![(0.0, start)];

    if let Some(center) = svg_color(&node.android_attr("centerColor"), Some(table), preferred_locale) {
        stops.push((50.0, center));
    }

    stops.push((100.0, end));
    Some(stops)
}

fn gradient_vector(angle: f32) -> (f32, f32, f32, f32) {
    match ((angle.round() as i32) % 360 + 360) % 360 {
        0 => (0.0, 50.0, 100.0, 50.0),
        45 => (0.0, 100.0, 100.0, 0.0),
        90 => (50.0, 100.0, 50.0, 0.0),
        135 => (100.0, 100.0, 0.0, 0.0),
        180 => (100.0, 50.0, 0.0, 50.0),
        225 => (100.0, 0.0, 0.0, 100.0),
        270 => (50.0, 0.0, 50.0, 100.0),
        315 => (0.0, 0.0, 100.0, 100.0),
        _ => (50.0, 0.0, 50.0, 100.0),
    }
}

fn vector_to_svg_data_url(
    node: &XmlNode,
    resource_table: Option<&ResourceTable>,
    preferred_locale: &str,
) -> Option<String> {
    if node.name != "vector" {
        return None;
    }

    let viewport = vector_viewport(node);
    let body = vector_svg_markup(node, resource_table, preferred_locale)?;

    if body.is_empty() {
        return None;
    }

    let svg = format!(
        r#"<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {} {}">{body}</svg>"#,
        viewport.width, viewport.height
    );
    Some(format!("data:image/svg+xml;base64,{}", base64_encode(svg.as_bytes())))
}

struct SvgViewport {
    width: f32,
    height: f32,
}

fn vector_viewport(node: &XmlNode) -> SvgViewport {
    let width = parse_float_attr(node, "android:viewportWidth")
        .or_else(|| parse_float_attr(node, "android:width"))
        .unwrap_or(24.0);
    let height = parse_float_attr(node, "android:viewportHeight")
        .or_else(|| parse_float_attr(node, "android:height"))
        .unwrap_or(width);

    SvgViewport { width, height }
}

#[derive(Default)]
struct SvgContext {
    clip_index: usize,
    defs: String,
}

fn vector_svg_markup(
    node: &XmlNode,
    resource_table: Option<&ResourceTable>,
    preferred_locale: &str,
) -> Option<String> {
    let mut context = SvgContext::default();
    let body = node
        .children
        .iter()
        .map(|child| render_vector_node(child, resource_table, preferred_locale, &mut context))
        .collect::<String>();

    if body.is_empty() {
        return None;
    }

    if context.defs.is_empty() {
        Some(body)
    } else {
        Some(format!("<defs>{}</defs>{body}", context.defs))
    }
}

fn render_vector_node(
    node: &XmlNode,
    resource_table: Option<&ResourceTable>,
    preferred_locale: &str,
    context: &mut SvgContext,
) -> String {
    if node.name == "path" {
        return render_svg_path(node, resource_table, preferred_locale);
    }

    if node.name == "clip-path" {
        return String::new();
    }

    let mut content = node
        .children
        .iter()
        .filter(|child| child.name != "clip-path")
        .map(|child| render_vector_node(child, resource_table, preferred_locale, context))
        .collect::<String>();

    if content.is_empty() {
        return String::new();
    }

    let clip_paths = node
        .children
        .iter()
        .filter(|child| child.name == "clip-path")
        .map(|clip| clip.android_attr("pathData"))
        .filter(|path_data| !path_data.is_empty())
        .collect::<Vec<_>>();

    if !clip_paths.is_empty() {
        let clip_id = format!("clip-{}", context.clip_index);
        context.clip_index += 1;
        let paths = clip_paths
            .into_iter()
            .map(|path_data| format!(r#"<path d="{}"/>"#, escape_xml(&path_data)))
            .collect::<String>();
        context.defs.push_str(&format!(r#"<clipPath id="{clip_id}">{paths}</clipPath>"#));
        content = format!(r#"<g clip-path="url(#{clip_id})">{content}</g>"#);
    }

    let transform = svg_transform(node);
    if transform.is_empty() {
        content
    } else {
        format!(r#"<g transform="{}">{content}</g>"#, escape_xml(&transform))
    }
}

fn render_svg_path(node: &XmlNode, resource_table: Option<&ResourceTable>, preferred_locale: &str) -> String {
    let path_data = node.android_attr("pathData");
    if path_data.is_empty() {
        return String::new();
    }

    let fill = svg_color(&node.android_attr("fillColor"), resource_table, preferred_locale)
        .unwrap_or_else(|| "none".to_owned());
    let stroke = svg_color(&node.android_attr("strokeColor"), resource_table, preferred_locale);
    let stroke_width = parse_float_attr(node, "android:strokeWidth");
    let mut out = format!(r#"<path d="{}" fill="{fill}""#, escape_xml(&path_data));

    if let Some(stroke) = stroke {
        out.push_str(&format!(r#" stroke="{stroke}""#));
    }

    if let Some(width) = stroke_width {
        out.push_str(&format!(r#" stroke-width="{width}""#));
    }

    if node.android_attr("fillType") == "1" {
        out.push_str(r#" fill-rule="evenodd" clip-rule="evenodd""#);
    }

    out.push_str("/>");
    out
}

fn svg_transform(node: &XmlNode) -> String {
    let translate_x = parse_float_attr(node, "android:translateX").unwrap_or(0.0);
    let translate_y = parse_float_attr(node, "android:translateY").unwrap_or(0.0);
    let pivot_x = parse_float_attr(node, "android:pivotX").unwrap_or(0.0);
    let pivot_y = parse_float_attr(node, "android:pivotY").unwrap_or(0.0);
    let rotation = parse_float_attr(node, "android:rotation").unwrap_or(0.0);
    let scale_x = parse_float_attr(node, "android:scaleX").unwrap_or(1.0);
    let scale_y = parse_float_attr(node, "android:scaleY").unwrap_or(1.0);
    let mut transforms = Vec::new();

    if translate_x != 0.0 || translate_y != 0.0 {
        transforms.push(format!("translate({translate_x} {translate_y})"));
    }

    if pivot_x != 0.0 || pivot_y != 0.0 {
        transforms.push(format!("translate({pivot_x} {pivot_y})"));
    }

    if rotation != 0.0 {
        transforms.push(format!("rotate({rotation})"));
    }

    if scale_x != 1.0 || scale_y != 1.0 {
        transforms.push(format!("scale({scale_x} {scale_y})"));
    }

    if pivot_x != 0.0 || pivot_y != 0.0 {
        transforms.push(format!("translate({} {})", -pivot_x, -pivot_y));
    }

    transforms.join(" ")
}

fn parse_float_attr(node: &XmlNode, name: &str) -> Option<f32> {
    parse_typed_float(&node.attr(name))
}

fn parse_typed_float(value: &str) -> Option<f32> {
    if let Some(hex) = value.strip_prefix("@0x") {
        let raw = u32::from_str_radix(hex, 16).ok()?;
        if raw & 0xff == 0x01 {
            return Some((raw >> 8) as f32);
        }
        let float = f32::from_bits(raw);
        if float.is_finite() && float.abs() < 100_000.0 {
            return Some(float);
        }
        return None;
    }

    value.parse().ok()
}

fn svg_color(value: &str, resource_table: Option<&ResourceTable>, preferred_locale: &str) -> Option<String> {
    if value.starts_with("@0x7f") {
        let resolved = resource_table?.resolve_color(value, preferred_locale)?;
        return svg_color(&resolved, resource_table, preferred_locale);
    }

    let hex = value.strip_prefix("@0x")?;
    let raw = u32::from_str_radix(hex, 16).ok()?;
    if raw >> 24 == 0 {
        return None;
    }
    Some(format!("#{:06x}", raw & 0x00ff_ffff))
}

fn escape_xml(value: &str) -> String {
    value
        .replace('&', "&amp;")
        .replace('"', "&quot;")
        .replace('<', "&lt;")
        .replace('>', "&gt;")
}

fn icon_resource_refs(node: &XmlNode) -> Vec<String> {
    let mut refs = node
        .attrs
        .iter()
        .map(|(_, value)| value)
        .filter(|value| value.starts_with("@0x"))
        .cloned()
        .collect::<Vec<_>>();

    for child in &node.children {
        refs.extend(icon_resource_refs(child));
    }

    refs
}

fn image_mime_type(path: &str) -> Option<&'static str> {
    let extension = Path::new(path)
        .extension()
        .and_then(|extension| extension.to_str())?
        .to_ascii_lowercase();

    match extension.as_str() {
        "png" => Some("image/png"),
        "webp" => Some("image/webp"),
        "jpg" | "jpeg" => Some("image/jpeg"),
        "svg" => Some("image/svg+xml"),
        _ => None,
    }
}

fn base64_encode(bytes: &[u8]) -> String {
    const TABLE: &[u8; 64] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    let mut encoded = String::with_capacity(bytes.len().div_ceil(3) * 4);

    for chunk in bytes.chunks(3) {
        let first = chunk[0];
        let second = *chunk.get(1).unwrap_or(&0);
        let third = *chunk.get(2).unwrap_or(&0);

        encoded.push(TABLE[(first >> 2) as usize] as char);
        encoded.push(TABLE[(((first & 0b0000_0011) << 4) | (second >> 4)) as usize] as char);

        if chunk.len() > 1 {
            encoded.push(TABLE[(((second & 0b0000_1111) << 2) | (third >> 6)) as usize] as char);
        } else {
            encoded.push('=');
        }

        if chunk.len() > 2 {
            encoded.push(TABLE[(third & 0b0011_1111) as usize] as char);
        } else {
            encoded.push('=');
        }
    }

    encoded
}

fn find_eocd(bytes: &[u8]) -> Option<usize> {
    let min = bytes.len().saturating_sub(22 + u16::MAX as usize);
    let max = bytes.len().saturating_sub(22);
    (min..=max)
        .rev()
        .find(|&offset| read_u32(bytes, offset).ok() == Some(EOCD_SIGNATURE))
}

fn get_range(bytes: &[u8], start: usize, end: usize) -> Result<&[u8], Box<dyn std::error::Error>> {
    bytes.get(start..end).ok_or_else(|| "ZIP 数据越界".into())
}

fn read_u16(bytes: &[u8], offset: usize) -> Result<u16, Box<dyn std::error::Error>> {
    let range = get_range(bytes, offset, offset + 2)?;
    Ok(u16::from_le_bytes([range[0], range[1]]))
}

fn read_u32(bytes: &[u8], offset: usize) -> Result<u32, Box<dyn std::error::Error>> {
    let range = get_range(bytes, offset, offset + 4)?;
    Ok(u32::from_le_bytes([range[0], range[1], range[2], range[3]]))
}

fn read_u64(bytes: &[u8], offset: usize) -> Result<u64, Box<dyn std::error::Error>> {
    let range = get_range(bytes, offset, offset + 8)?;
    Ok(u64::from_le_bytes([
        range[0], range[1], range[2], range[3], range[4], range[5], range[6], range[7],
    ]))
}

fn collect_permissions(manifest: &crate::axml::XmlNode) -> Vec<String> {
    manifest
        .children_named("uses-permission")
        .into_iter()
        .map(|node| node.android_attr("name"))
        .filter(|value| !value.is_empty())
        .collect::<BTreeSet<_>>()
        .into_iter()
        .collect()
}

fn collect_components(application: Option<&crate::axml::XmlNode>, tag: &str) -> Vec<String> {
    application
        .map(|app| {
            app.children_named(tag)
                .into_iter()
                .map(|node| node.android_attr("name"))
                .filter(|value| !value.is_empty())
                .collect()
        })
        .unwrap_or_default()
}

fn collect_native_libs(names: &[String]) -> Vec<String> {
    names
        .iter()
        .filter(|name| name.starts_with("lib/") && name.ends_with(".so"))
        .cloned()
        .collect()
}

fn collect_abis(native_libs: &[String]) -> Vec<String> {
    native_libs
        .iter()
        .filter_map(|name| name.split('/').nth(1).map(str::to_owned))
        .collect::<BTreeSet<_>>()
        .into_iter()
        .collect()
}

fn detect_tech_features(bytes: &[u8], entries: &[ZipEntry], names: &[String], native_libs: &[String]) -> Vec<TechFeature> {
    let mut features = Vec::new();
    let dex_entries: Vec<&ZipEntry> = entries
        .iter()
        .filter(|entry| entry.name.ends_with(".dex"))
        .collect();

    if names.iter().any(|name| name.ends_with(".kotlin_module"))
        || dex_entries.iter().any(|entry| dex_contains(bytes, entry, &[b"Lkotlin/Metadata;", b"kotlin/"]))
    {
        features.push(tech_feature("Kotlin", "kotlin"));
    }

    if dex_entries
        .iter()
        .any(|entry| dex_contains(bytes, entry, &[b"androidx/compose/", b"androidx.compose.", b"ComposerKt"]))
    {
        features.push(tech_feature("Compose", "jetpackcompose"));
    }

    if names.iter().any(|name| name.to_ascii_lowercase().contains("gradle"))
        || dex_entries
            .iter()
            .any(|entry| dex_contains(bytes, entry, &[b"com.android.tools.build", b"gradle", b"Gradle"]))
    {
        features.push(tech_feature("Gradle", "gradle"));
    }

    if dex_entries
        .iter()
        .any(|entry| dex_contains(bytes, entry, &[b"kotlinx/coroutines/", b"kotlinx.coroutines."]))
    {
        features.push(tech_feature("Coroutines", "kotlin"));
    }

    if dex_entries
        .iter()
        .any(|entry| dex_contains(bytes, entry, &[b"androidx/room/", b"androidx.room."]))
    {
        features.push(tech_feature("Room", "android"));
    }

    if !native_libs.is_empty() {
        features.push(tech_feature("Native", "android"));
    }

    features
}

fn tech_feature(name: &str, icon: &str) -> TechFeature {
    TechFeature {
        name: name.to_owned(),
        icon: format!("{SIMPLE_ICONS_CDN}/{icon}?viewbox=auto"),
    }
}

fn dex_contains(bytes: &[u8], entry: &ZipEntry, patterns: &[&[u8]]) -> bool {
    let Ok(data) = read_zip_entry_data(bytes, entry) else {
        return false;
    };

    patterns.iter().any(|pattern| contains_bytes(&data, pattern))
}

fn contains_bytes(haystack: &[u8], needle: &[u8]) -> bool {
    !needle.is_empty() && haystack.windows(needle.len()).any(|window| window == needle)
}

fn collect_signatures(names: &[String]) -> Result<Vec<String>, regex::Error> {
    let signature_re = Regex::new(r"(?i)^META-INF/[^/]+\.(RSA|DSA|EC)$")?;
    Ok(names
        .iter()
        .filter(|name| signature_re.is_match(name))
        .cloned()
        .collect())
}

#[cfg(test)]
mod tests {
    use super::*;
    use flate2::{write::DeflateEncoder, Compression};
    use std::{env, fs, io::Write};

    #[test]
    fn parses_standalone_binary_manifest() {
        let manifest_path = Path::new("../AndroidManifest.xxxxml");
        if !manifest_path.exists() {
            return;
        }

        let info = parse_apk_file(manifest_path.to_str().unwrap()).expect("manifest should parse");
        assert!(!info.package_name.is_empty());
    }

    #[test]
    fn parses_apk_with_deflated_manifest() {
        let manifest_path = Path::new("../AndroidManifest.xxxxml");
        if !manifest_path.exists() {
            return;
        }

        let manifest = fs::read(manifest_path).expect("read manifest");
        let apk_path = env::temp_dir().join("apk_info_viewer_manifest_test.apk");
        write_minimal_zip_with_deflated_manifest(&apk_path, &manifest).expect("write apk");

        let info = parse_apk_file(apk_path.to_str().unwrap()).expect("apk should parse");
        assert!(!info.package_name.is_empty());
        assert_eq!(info.file_count, 1);

        let _ = fs::remove_file(apk_path);
    }

    fn write_minimal_zip_with_deflated_manifest(
        path: &Path,
        manifest: &[u8],
    ) -> Result<(), Box<dyn std::error::Error>> {
        let name = b"AndroidManifest.xml";
        let mut encoder = DeflateEncoder::new(Vec::new(), Compression::default());
        encoder.write_all(manifest)?;
        let compressed = encoder.finish()?;
        let crc = crc32(manifest);
        let mut out = Vec::new();

        let local_header_offset = out.len() as u32;
        write_u32(&mut out, LOCAL_FILE_SIGNATURE);
        write_u16(&mut out, 20);
        write_u16(&mut out, 0);
        write_u16(&mut out, METHOD_DEFLATED);
        write_u16(&mut out, 0);
        write_u16(&mut out, 0);
        write_u32(&mut out, crc);
        write_u32(&mut out, compressed.len() as u32);
        write_u32(&mut out, manifest.len() as u32);
        write_u16(&mut out, name.len() as u16);
        write_u16(&mut out, 0);
        out.extend_from_slice(name);
        out.extend_from_slice(&compressed);

        let central_dir_offset = out.len() as u32;
        write_u32(&mut out, CENTRAL_DIRECTORY_SIGNATURE);
        write_u16(&mut out, 20);
        write_u16(&mut out, 20);
        write_u16(&mut out, 0);
        write_u16(&mut out, METHOD_DEFLATED);
        write_u16(&mut out, 0);
        write_u16(&mut out, 0);
        write_u32(&mut out, crc);
        write_u32(&mut out, compressed.len() as u32);
        write_u32(&mut out, manifest.len() as u32);
        write_u16(&mut out, name.len() as u16);
        write_u16(&mut out, 0);
        write_u16(&mut out, 0);
        write_u16(&mut out, 0);
        write_u16(&mut out, 0);
        write_u32(&mut out, 0);
        write_u32(&mut out, local_header_offset);
        out.extend_from_slice(name);

        let central_dir_size = out.len() as u32 - central_dir_offset;
        write_u32(&mut out, EOCD_SIGNATURE);
        write_u16(&mut out, 0);
        write_u16(&mut out, 0);
        write_u16(&mut out, 1);
        write_u16(&mut out, 1);
        write_u32(&mut out, central_dir_size);
        write_u32(&mut out, central_dir_offset);
        write_u16(&mut out, 0);

        fs::write(path, out)?;
        Ok(())
    }

    fn write_u16(out: &mut Vec<u8>, value: u16) {
        out.extend_from_slice(&value.to_le_bytes());
    }

    fn write_u32(out: &mut Vec<u8>, value: u32) {
        out.extend_from_slice(&value.to_le_bytes());
    }

    fn crc32(bytes: &[u8]) -> u32 {
        let mut crc = 0xffff_ffffu32;
        for &byte in bytes {
            crc ^= byte as u32;
            for _ in 0..8 {
                let mask = (crc & 1).wrapping_neg();
                crc = (crc >> 1) ^ (0xedb8_8320 & mask);
            }
        }
        !crc
    }
}


#[cfg(test)]
mod real_apk_tests {
    use super::*;
    use std::path::Path;

    #[test]
    fn parses_real_apk_manifest_fields() {
        let apk_path = Path::new("../11.2.0-alpha01-2026032801-moriafly-arm64-v8a.apk");
        if !apk_path.exists() {
            return;
        }

        let info = parse_apk_file(apk_path.to_str().unwrap()).expect("real apk should parse");
        assert_eq!(info.package_name, "com.salt.music");
        assert_eq!(info.version_name, "11.2.0-alpha01");
        assert_eq!(info.version_code, "2026032801");
        assert_eq!(info.min_sdk, "23");
        assert_eq!(info.target_sdk, "36");
        assert!(!info.resolved_app_label.is_empty());
        assert!(!info.resolved_app_icon.is_empty());
        assert!(!info.supported_languages.is_empty());
        assert!(info.permissions.iter().any(|item| item == "android.permission.INTERNET"));
        assert!(info.activities.iter().any(|item| item == "com.salt.music.ui.MainActivity"));
    }

    #[test]
    fn parses_keios_vector_icon() {
        let apk_path = Path::new("/Users/cacheci/Downloads/Yukigram/KeiOS-v1.4.0.apk");
        if !apk_path.exists() {
            return;
        }

        let info = parse_apk_file(apk_path.to_str().unwrap()).expect("keios apk should parse");
        assert!(info.app_icon_data_url.starts_with("data:image/svg+xml;base64,"));
        let svg_base64 = info.app_icon_data_url.trim_start_matches("data:image/svg+xml;base64,");
        let svg_bytes = base64_decode_for_test(svg_base64);
        let svg = String::from_utf8_lossy(&svg_bytes);
        assert!(svg.contains("<linearGradient"));
        assert!(svg.contains("<clipPath"));
        assert!(svg.contains("fill=\"#"));
    }

    #[test]
    fn prefers_anydpi_v26_icon() {
        let apk_path = Path::new("/Users/cacheci/Downloads/Yukigram/11.2.0-alpha01-2026032801-moriafly-arm64-v8a.apk");
        if !apk_path.exists() {
            return;
        }

        let info = parse_apk_file(apk_path.to_str().unwrap()).expect("salt apk should parse");
        assert_eq!(info.resolved_app_icon, "res/BW.xml");
        assert!(!info.app_icon_data_url.is_empty());
        let svg_base64 = info.app_icon_data_url.trim_start_matches("data:image/svg+xml;base64,");
        let svg_bytes = base64_decode_for_test(svg_base64);
        let svg = String::from_utf8_lossy(&svg_bytes);
        assert!(svg.contains("scale(0.13034482 0.13034482)"));
    }

    #[test]
    fn parses_np_manager_protected_manifest() {
        let apk_path = Path::new("/Users/cacheci/Downloads/Yukigram/NP管理器_3.1.36.apk");
        if !apk_path.exists() {
            return;
        }

        let info = parse_apk_file(apk_path.to_str().unwrap()).expect("protected apk should parse");
        assert_eq!(info.package_name, "com.wn.app.np");
        assert_eq!(info.version_name, "3.1.36");
        assert_eq!(info.version_code, "20251014");
        assert_eq!(info.app_icon, "@0x7f080124");
    }

    #[test]
    fn parses_moonbox_max_manifest() {
        let apk_path = Path::new("/Users/cacheci/Downloads/Yukigram/月光宝盒MAX302.apk");
        if !apk_path.exists() {
            return;
        }

        let info = parse_apk_file_with_locale(apk_path.to_str().unwrap(), "zh-CN")
            .expect("moonbox apk should parse");
        assert_eq!(info.package_name, "com.github.tv0302");
        assert_eq!(info.version_name, "1.0.20230301_1832");
        assert_eq!(info.version_code, "1");
        assert_eq!(info.app_label, "@0x7f10003d");
        assert_eq!(info.resolved_app_label, "月光宝盒MAX");
        assert_eq!(info.app_icon, "@0x7f070058");
    }

    #[test]
    fn parses_zip64_record_apk_best_effort() {
        let apk_path = Path::new("/Users/cacheci/Downloads/Yukigram/v1v2v3-with-zip64-records.apk");
        if !apk_path.exists() {
            return;
        }

        let info = parse_apk_file(apk_path.to_str().unwrap()).expect("zip64 record apk should parse");
        assert_eq!(info.package_name, "android.appsecurity.cts.tinyapp");
        assert_eq!(info.version_name, "1.0");
        assert_eq!(info.version_code, "10");
        assert_eq!(info.app_label, "@0x7f020000");
        assert_eq!(info.file_count, 11);
    }

    fn base64_decode_for_test(value: &str) -> Vec<u8> {
        let mut out = Vec::new();
        let mut buffer = 0u32;
        let mut bits = 0u8;

        for byte in value.bytes() {
            let Some(decoded) = base64_value(byte) else {
                continue;
            };
            buffer = (buffer << 6) | decoded as u32;
            bits += 6;

            if bits >= 8 {
                bits -= 8;
                out.push((buffer >> bits) as u8);
                buffer &= (1 << bits) - 1;
            }
        }

        out
    }

    fn base64_value(byte: u8) -> Option<u8> {
        match byte {
            b'A'..=b'Z' => Some(byte - b'A'),
            b'a'..=b'z' => Some(byte - b'a' + 26),
            b'0'..=b'9' => Some(byte - b'0' + 52),
            b'+' => Some(62),
            b'/' => Some(63),
            _ => None,
        }
    }
}
