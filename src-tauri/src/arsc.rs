use crate::utils::{byte_at, u16_at, u32_at};
use std::collections::{BTreeMap, BTreeSet};

const RES_STRING_POOL_TYPE: u16 = 0x0001;
const RES_TABLE_TYPE: u16 = 0x0002;
const RES_TABLE_PACKAGE_TYPE: u16 = 0x0200;
const RES_TABLE_TYPE_TYPE: u16 = 0x0201;
const TYPE_STRING: u8 = 0x03;
const TYPE_FIRST_COLOR_INT: u8 = 0x1c;
const TYPE_LAST_COLOR_INT: u8 = 0x1f;

#[derive(Debug, Default)]
pub struct ResourceTable {
    strings: BTreeMap<u32, Vec<ResourceValue>>,
    files: BTreeMap<u32, Vec<ResourceValue>>,
    colors: BTreeMap<u32, Vec<ResourceValue>>,
    languages: BTreeSet<String>,
}

#[derive(Debug, Clone)]
struct ResourceValue {
    locale: String,
    density: u16,
    value: String,
}

impl ResourceTable {
    pub fn parse(data: &[u8]) -> Result<Self, String> {
        if u16_at(data, 0)? != RES_TABLE_TYPE {
            return Err("resources.arsc 不是 Android Resource Table".to_owned());
        }

        let table_size = u32_at(data, 4)? as usize;
        let mut table = ResourceTable::default();
        let mut global_strings = Vec::new();
        let mut offset = u16_at(data, 2)? as usize;

        while offset + 8 <= data.len() && offset < table_size {
            let chunk_type = u16_at(data, offset)?;
            let chunk_size = u32_at(data, offset + 4)? as usize;
            if chunk_size == 0 || offset + chunk_size > data.len() {
                break;
            }

            match chunk_type {
                RES_STRING_POOL_TYPE => global_strings = parse_string_pool(data, offset)?,
                RES_TABLE_PACKAGE_TYPE => parse_package(data, offset, &global_strings, &mut table)?,
                _ => {}
            }

            offset += chunk_size;
        }

        Ok(table)
    }

    pub fn resolve_string(&self, resource_ref: &str, preferred_locale: &str) -> Option<String> {
        let id = parse_resource_ref(resource_ref)?;
        self.strings
            .get(&id)
            .and_then(|values| choose_locale_value(values, preferred_locale))
    }

    pub fn resolve_file(&self, resource_ref: &str, preferred_locale: &str) -> Option<String> {
        let id = parse_resource_ref(resource_ref)?;
        self.files
            .get(&id)
            .and_then(|values| choose_locale_value(values, preferred_locale))
    }

    pub fn resolve_icon_file(&self, resource_ref: &str, preferred_locale: &str) -> Option<String> {
        let id = parse_resource_ref(resource_ref)?;
        self.files
            .get(&id)
            .and_then(|values| choose_icon_value(values, preferred_locale))
    }

    pub fn resolve_color(&self, resource_ref: &str, preferred_locale: &str) -> Option<String> {
        let id = parse_resource_ref(resource_ref)?;
        self.colors
            .get(&id)
            .and_then(|values| choose_locale_value(values, preferred_locale))
    }

    pub fn supported_languages(&self) -> Vec<String> {
        self.languages.iter().cloned().collect()
    }

}

fn parse_package(
    data: &[u8],
    package_offset: usize,
    global_strings: &[String],
    table: &mut ResourceTable,
) -> Result<(), String> {
    let package_id = u32_at(data, package_offset + 8)?;
    let package_size = u32_at(data, package_offset + 4)? as usize;
    let type_strings_offset = u32_at(data, package_offset + 268)? as usize;
    let key_strings_offset = u32_at(data, package_offset + 276)? as usize;
    let type_strings = parse_string_pool(data, package_offset + type_strings_offset)?;
    let _key_strings = parse_string_pool(data, package_offset + key_strings_offset)?;
    let mut offset = package_offset + u16_at(data, package_offset + 2)? as usize;

    while offset + 8 <= data.len() && offset < package_offset + package_size {
        let chunk_type = u16_at(data, offset)?;
        let chunk_size = u32_at(data, offset + 4)? as usize;
        if chunk_size == 0 || offset + chunk_size > data.len() {
            break;
        }

        if chunk_type == RES_TABLE_TYPE_TYPE {
            parse_type_chunk(data, offset, package_id, &type_strings, global_strings, table)?;
        }

        offset += chunk_size;
    }

    Ok(())
}

fn parse_type_chunk(
    data: &[u8],
    offset: usize,
    package_id: u32,
    type_strings: &[String],
    global_strings: &[String],
    table: &mut ResourceTable,
) -> Result<(), String> {
    let header_size = u16_at(data, offset + 2)? as usize;
    let chunk_size = u32_at(data, offset + 4)? as usize;
    let type_id = byte_at(data, offset + 8)? as u32;
    let entry_count = u32_at(data, offset + 12)? as usize;
    let entries_start = u32_at(data, offset + 16)? as usize;
    let config_offset = offset + 20;
    let locale = parse_config_locale(data, config_offset)?;
    let density = parse_config_density(data, config_offset)?;
    let type_name = type_strings.get(type_id.saturating_sub(1) as usize).cloned().unwrap_or_default();

    if !locale.is_empty() {
        table.languages.insert(locale.clone());
    }

    let entry_offsets_base = offset + header_size;
    let entries_base = offset + entries_start;

    for entry_index in 0..entry_count {
        let entry_rel = u32_at(data, entry_offsets_base + entry_index * 4)?;
        if entry_rel == 0xffff_ffff {
            continue;
        }

        let entry_offset = entries_base + entry_rel as usize;
        if entry_offset + 16 > offset + chunk_size {
            continue;
        }

        let flags = u16_at(data, entry_offset + 2)?;
        if flags & 0x0001 != 0 {
            continue;
        }

        let value_offset = entry_offset + 8;
        let data_type = byte_at(data, value_offset + 3)?;
        let data_value = u32_at(data, value_offset + 4)?;
        let resource_id = (package_id << 24) | (type_id << 16) | entry_index as u32;

        if (TYPE_FIRST_COLOR_INT..=TYPE_LAST_COLOR_INT).contains(&data_type) {
            table
                .colors
                .entry(resource_id)
                .or_default()
                .push(ResourceValue {
                    locale: locale.clone(),
                    density,
                    value: format!("@0x{data_value:08x}"),
                });
        }

        if data_type == TYPE_STRING {
            let Some(value) = global_strings.get(data_value as usize).cloned() else {
                continue;
            };

            if type_name == "string" || !looks_like_resource_file(&value) {
                table.strings.entry(resource_id).or_default().push(ResourceValue {
                    locale: locale.clone(),
                    density,
                    value: value.clone(),
                });
            }

            if type_name == "drawable" || type_name == "mipmap" || looks_like_resource_file(&value) {
                table.files.entry(resource_id).or_default().push(ResourceValue {
                    locale: locale.clone(),
                    density,
                    value,
                });
            }
        }
    }

    Ok(())
}

fn looks_like_resource_file(value: &str) -> bool {
    value.starts_with("res/")
        || value.ends_with(".xml")
        || value.ends_with(".png")
        || value.ends_with(".webp")
        || value.ends_with(".jpg")
        || value.ends_with(".jpeg")
        || value.ends_with(".svg")
}

fn parse_config_locale(data: &[u8], offset: usize) -> Result<String, String> {
    let size = u32_at(data, offset)? as usize;
    if size < 32 {
        return Ok(String::new());
    }

    let language = decode_language(byte_at(data, offset + 8)?, byte_at(data, offset + 9)?);
    let region = decode_region(byte_at(data, offset + 10)?, byte_at(data, offset + 11)?);
    if language.is_empty() {
        return Ok(String::new());
    }
    if region.is_empty() {
        Ok(language)
    } else {
        Ok(format!("{language}-r{region}"))
    }
}

fn parse_config_density(data: &[u8], offset: usize) -> Result<u16, String> {
    let size = u32_at(data, offset)? as usize;
    if size < 16 {
        return Ok(0);
    }

    u16_at(data, offset + 14)
}

fn decode_language(first: u8, second: u8) -> String {
    if first == 0 && second == 0 {
        return String::new();
    }
    if first & 0x80 != 0 {
        let first_char = (first & 0x1f) + b'a';
        let second_char = (((first & 0x60) >> 5) | ((second & 0x03) << 3)) + b'a';
        let third_char = ((second & 0x7c) >> 2) + b'a';
        return String::from_utf8_lossy(&[first_char, second_char, third_char]).to_string();
    }
    String::from_utf8_lossy(&[first, second]).trim_matches('\0').to_string()
}

fn decode_region(first: u8, second: u8) -> String {
    if first == 0 && second == 0 {
        return String::new();
    }
    if first & 0x80 != 0 {
        let first_digit = (first & 0x1f) + b'0';
        let second_digit = (((first & 0x60) >> 5) | ((second & 0x03) << 3)) + b'0';
        let third_digit = ((second & 0x7c) >> 2) + b'0';
        return String::from_utf8_lossy(&[first_digit, second_digit, third_digit]).to_string();
    }
    String::from_utf8_lossy(&[first, second]).trim_matches('\0').to_string()
}

fn choose_locale_value(values: &[ResourceValue], preferred_locale: &str) -> Option<String> {
    choose_scoped_value(values, preferred_locale, choose_density_value)
}

fn choose_icon_value(values: &[ResourceValue], preferred_locale: &str) -> Option<String> {
    choose_scoped_value(values, preferred_locale, choose_icon_density_value)
}

fn choose_scoped_value(
    values: &[ResourceValue],
    preferred_locale: &str,
    chooser: fn(&[&ResourceValue]) -> Option<String>,
) -> Option<String> {
    let normalized = normalize_locale(preferred_locale);
    if !normalized.is_empty() {
        let matches = values
            .iter()
            .filter(|item| normalize_locale(&item.locale).eq_ignore_ascii_case(&normalized))
            .collect::<Vec<_>>();
        if !matches.is_empty() {
            return chooser(&matches);
        }
        if let Some(language) = normalized.split('-').next() {
            let matches = values
                .iter()
                .filter(|item| normalize_locale(&item.locale).eq_ignore_ascii_case(language))
                .collect::<Vec<_>>();
            if !matches.is_empty() {
                return chooser(&matches);
            }
        }
    }

    let matches = values
        .iter()
        .filter(|item| item.locale.is_empty())
        .collect::<Vec<_>>();
    if !matches.is_empty() {
        return chooser(&matches);
    }

    let all = values.iter().collect::<Vec<_>>();
    chooser(&all)
}

fn normalize_locale(value: &str) -> String {
    value
        .split('.')
        .next()
        .unwrap_or(value)
        .replace("_", "-")
        .replace("-r", "-")
}

fn choose_density_value(values: &[&ResourceValue]) -> Option<String> {
    values
        .iter()
        .max_by_key(|item| normalized_density(item.density))
        .map(|item| item.value.clone())
}

fn normalized_density(density: u16) -> u16 {
    if density == 0xffff {
        u16::MAX
    } else {
        density
    }
}

fn choose_icon_density_value(values: &[&ResourceValue]) -> Option<String> {
    values
        .iter()
        .max_by_key(|item| icon_density_rank(item))
        .map(|item| item.value.clone())
}

fn icon_density_rank(value: &ResourceValue) -> u16 {
    if is_anydpi(value.density) && value.value.ends_with(".xml") {
        return 700;
    }

    match value.density {
        640 => 600,
        480 => 500,
        320 => 400,
        240 => 300,
        160 => 200,
        density if is_anydpi(density) => 100,
        density => density.min(99),
    }
}

fn is_anydpi(density: u16) -> bool {
    density == 0xffff || density == 0xfffe
}

fn parse_resource_ref(value: &str) -> Option<u32> {
    value.strip_prefix("@0x").and_then(|hex| u32::from_str_radix(hex, 16).ok())
}

fn parse_string_pool(data: &[u8], offset: usize) -> Result<Vec<String>, String> {
    let header_size = u16_at(data, offset + 2)? as usize;
    let string_count = u32_at(data, offset + 8)? as usize;
    let flags = u32_at(data, offset + 16)?;
    let strings_start = u32_at(data, offset + 20)? as usize;
    let is_utf8 = flags & 0x0000_0100 != 0;
    let offsets_start = offset + header_size;
    let base = offset + strings_start;
    let mut strings = Vec::with_capacity(string_count);

    for i in 0..string_count {
        let string_offset = u32_at(data, offsets_start + i * 4)? as usize;
        let cursor = base + string_offset;
        let value = if cursor >= data.len() {
            String::new()
        } else if is_utf8 {
            read_utf8_string(data, cursor)
                .map(|(value, _)| value)
                .unwrap_or_default()
        } else {
            read_utf16_string(data, cursor)
                .map(|(value, _)| value)
                .unwrap_or_default()
        };
        strings.push(value);
    }

    Ok(strings)
}

fn read_utf8_string(data: &[u8], mut offset: usize) -> Result<(String, usize), String> {
    let (_, next) = read_len8(data, offset)?;
    offset = next;
    let (byte_len, next) = read_len8(data, offset)?;
    offset = next;
    let end = offset + byte_len;
    let raw = data.get(offset..end).ok_or_else(|| "字符串池 UTF-8 越界".to_owned())?;
    Ok((String::from_utf8_lossy(raw).to_string(), end + 1))
}

fn read_utf16_string(data: &[u8], mut offset: usize) -> Result<(String, usize), String> {
    let (char_len, next) = read_len16(data, offset)?;
    offset = next;
    let end = offset + char_len * 2;
    let raw = data.get(offset..end).ok_or_else(|| "字符串池 UTF-16 越界".to_owned())?;
    let units = raw.chunks_exact(2).map(|bytes| u16::from_le_bytes([bytes[0], bytes[1]])).collect::<Vec<_>>();
    Ok((String::from_utf16_lossy(&units), end + 2))
}

fn read_len8(data: &[u8], mut offset: usize) -> Result<(usize, usize), String> {
    let first = byte_at(data, offset)?;
    offset += 1;
    if first & 0x80 != 0 {
        let second = byte_at(data, offset)?;
        Ok(((((first & 0x7f) as usize) << 8) | second as usize, offset + 1))
    } else {
        Ok((first as usize, offset))
    }
}

fn read_len16(data: &[u8], mut offset: usize) -> Result<(usize, usize), String> {
    let first = u16_at(data, offset)?;
    offset += 2;
    if first & 0x8000 != 0 {
        let second = u16_at(data, offset)?;
        Ok(((((first & 0x7fff) as usize) << 16) | second as usize, offset + 2))
    } else {
        Ok((first as usize, offset))
    }
}
