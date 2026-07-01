use crate::utils::{byte_at, u16_at, u32_at};

const RES_STRING_POOL_TYPE: u16 = 0x0001;
const RES_XML_TYPE: u16 = 0x0003;
const RES_XML_START_ELEMENT_TYPE: u16 = 0x0102;
const RES_XML_END_ELEMENT_TYPE: u16 = 0x0103;
const RES_XML_RESOURCE_MAP_TYPE: u16 = 0x0180;
const TYPE_STRING: u8 = 0x03;
const TYPE_FLOAT: u8 = 0x04;
const TYPE_DIMENSION: u8 = 0x05;
const TYPE_INT_DEC: u8 = 0x10;
const TYPE_INT_HEX: u8 = 0x11;
const TYPE_INT_BOOLEAN: u8 = 0x12;
const ANDROID_NS: &str = "http://schemas.android.com/apk/res/android";

#[derive(Debug, Clone)]
pub struct XmlNode {
    pub(crate) name: String,
    pub(crate) attrs: Vec<(String, String)>,
    pub(crate) children: Vec<XmlNode>,
}

impl XmlNode {
    pub fn children_named<'a>(&'a self, name: &str) -> Vec<&'a XmlNode> {
        self.children
            .iter()
            .filter(|child| child.name == name)
            .collect()
    }

    pub fn attr(&self, name: &str) -> String {
        self.attrs
            .iter()
            .find_map(|(key, value)| (key == name).then(|| value.clone()))
            .unwrap_or_default()
    }

    pub fn android_attr(&self, name: &str) -> String {
        let android_name = format!("android:{name}");
        let value = self.attr(&android_name);
        if value.is_empty() {
            self.attr(name)
        } else {
            value
        }
    }
}

pub fn parse_binary_manifest(data: &[u8]) -> Result<XmlNode, String> {
    if !looks_like_binary_xml(data)? {
        return Err("AndroidManifest.xml 不是 Android 二进制 XML".to_owned());
    }

    let mut strings = Vec::new();
    let mut resource_ids = Vec::new();
    let mut root: Option<XmlNode> = None;
    let mut stack: Vec<XmlNode> = Vec::new();
    let mut offset = u16_at(data, 2)? as usize;

    while offset + 8 <= data.len() {
        let chunk_type = u16_at(data, offset)?;
        let header_size = u16_at(data, offset + 2)? as usize;
        let chunk_size = u32_at(data, offset + 4)? as usize;
        if chunk_size == 0 || offset + chunk_size > data.len() {
            break;
        }

        match chunk_type {
            RES_STRING_POOL_TYPE => strings = parse_string_pool(data, offset)?,
            RES_XML_RESOURCE_MAP_TYPE => {
                resource_ids = parse_resource_map(data, offset, header_size, chunk_size);
            }
            RES_XML_START_ELEMENT_TYPE => {
                stack.push(parse_start_element(data, offset, &strings, &resource_ids)?);
            }
            RES_XML_END_ELEMENT_TYPE => {
                if let Some(node) = stack.pop() {
                    if let Some(parent) = stack.last_mut() {
                        parent.children.push(node);
                    } else {
                        root = Some(node);
                    }
                }
            }
            _ => {}
        }
        offset += chunk_size;
    }

    root.ok_or_else(|| "AndroidManifest.xml 没有 manifest 根节点".to_owned())
}

fn looks_like_binary_xml(data: &[u8]) -> Result<bool, String> {
    let chunk_type = u16_at(data, 0)?;
    if chunk_type == RES_XML_TYPE {
        return Ok(true);
    }

    let header_size = u16_at(data, 2)? as usize;
    let chunk_size = u32_at(data, 4)? as usize;
    Ok(chunk_type == 0
        && header_size == 8
        && chunk_size <= data.len()
        && data.get(header_size..header_size + 2).is_some()
        && u16_at(data, header_size)? == RES_STRING_POOL_TYPE)
}

fn parse_resource_map(
    data: &[u8],
    offset: usize,
    header_size: usize,
    chunk_size: usize,
) -> Vec<u32> {
    let count = (chunk_size - header_size) / 4;
    (0..count)
        .filter_map(|i| u32_at(data, offset + header_size + i * 4).ok())
        .collect()
}

fn parse_start_element(
    data: &[u8],
    offset: usize,
    strings: &[String],
    resource_ids: &[u32],
) -> Result<XmlNode, String> {
    let name = string_at(strings, u32_at(data, offset + 20)? as usize);
    let attr_start = u16_at(data, offset + 24)? as usize;
    let attr_size = u16_at(data, offset + 26)? as usize;
    let attr_count = u16_at(data, offset + 28)? as usize;
    let attr_base = offset + 16 + attr_start;
    let mut attrs = Vec::with_capacity(attr_count);

    for i in 0..attr_count {
        let item = attr_base + i * attr_size;
        if data.get(item..item + 20).is_none() {
            continue;
        }
        let ns_idx = u32_at(data, item)?;
        let name_idx = u32_at(data, item + 4)?;
        let raw_idx = u32_at(data, item + 8)?;
        let value_type = byte_at(data, item + 15)?;
        let data_value = u32_at(data, item + 16)?;
        let ns = string_at(strings, ns_idx as usize);
        let resource_attr_name = attr_name_from_resource_map(resource_ids, name_idx);
        let string_attr_name = string_at(strings, name_idx as usize);
        let attr_name = if ns == ANDROID_NS && is_sdk_attr(&resource_attr_name) {
            resource_attr_name
        } else if string_attr_name.is_empty() {
            resource_attr_name
        } else {
            string_attr_name
        };
        let raw = string_at(strings, raw_idx as usize);
        let value = format_typed_value(strings, value_type, data_value, &raw);
        let key = if ns == ANDROID_NS && !attr_name.is_empty() {
            format!("android:{attr_name}")
        } else {
            attr_name
        };

        if !key.is_empty() {
            attrs.push((key, value));
        }
    }

    Ok(XmlNode {
        name,
        attrs,
        children: Vec::new(),
    })
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
    let raw = data
        .get(offset..end)
        .ok_or_else(|| "字符串池 UTF-8 越界".to_owned())?;
    Ok((String::from_utf8_lossy(raw).to_string(), end + 1))
}

fn read_utf16_string(data: &[u8], mut offset: usize) -> Result<(String, usize), String> {
    let (char_len, next) = read_len16(data, offset)?;
    offset = next;
    let end = offset + char_len * 2;
    let raw = data
        .get(offset..end)
        .ok_or_else(|| "字符串池 UTF-16 越界".to_owned())?;
    let units = raw
        .chunks_exact(2)
        .map(|bytes| u16::from_le_bytes([bytes[0], bytes[1]]))
        .collect::<Vec<_>>();
    Ok((String::from_utf16_lossy(&units), end + 2))
}

fn read_len8(data: &[u8], mut offset: usize) -> Result<(usize, usize), String> {
    let first = byte_at(data, offset)?;
    offset += 1;
    if first & 0x80 != 0 {
        let second = byte_at(data, offset)?;
        Ok((
            (((first & 0x7f) as usize) << 8) | second as usize,
            offset + 1,
        ))
    } else {
        Ok((first as usize, offset))
    }
}

fn read_len16(data: &[u8], mut offset: usize) -> Result<(usize, usize), String> {
    let first = u16_at(data, offset)?;
    offset += 2;
    if first & 0x8000 != 0 {
        let second = u16_at(data, offset)?;
        Ok((
            (((first & 0x7fff) as usize) << 16) | second as usize,
            offset + 2,
        ))
    } else {
        Ok((first as usize, offset))
    }
}

fn string_at(strings: &[String], index: usize) -> String {
    if index == 0xffff_ffff || index >= strings.len() {
        String::new()
    } else {
        strings[index].clone()
    }
}

fn format_typed_value(strings: &[String], value_type: u8, data_value: u32, raw: &str) -> String {
    if !raw.is_empty() {
        return raw.to_owned();
    }
    match value_type {
        TYPE_STRING => string_at(strings, data_value as usize),
        TYPE_FLOAT => f32::from_bits(data_value).to_string(),
        TYPE_DIMENSION => complex_to_float(data_value).to_string(),
        TYPE_INT_BOOLEAN => (data_value != 0).to_string(),
        TYPE_INT_DEC => data_value.to_string(),
        TYPE_INT_HEX => format!("0x{data_value:08x}"),
        _ if data_value != 0 => format!("@0x{data_value:08x}"),
        _ => String::new(),
    }
}

fn complex_to_float(value: u32) -> f32 {
    const RADIX_MULTS: [f32; 4] = [
        1.0,
        1.0 / (1 << 7) as f32,
        1.0 / (1 << 15) as f32,
        1.0 / (1 << 23) as f32,
    ];
    let mantissa = ((value >> 8) & 0x00ff_ffff) as i32;
    let signed = (mantissa << 8) >> 8;
    let radix = ((value >> 4) & 0x3) as usize;
    signed as f32 * RADIX_MULTS[radix]
}

fn attr_name_from_resource_map(resource_ids: &[u32], name_idx: u32) -> String {
    let Some(id) = resource_ids.get(name_idx as usize).copied() else {
        return String::new();
    };

    attr_name_from_res_id(id)
}

fn is_sdk_attr(name: &str) -> bool {
    matches!(
        name,
        "minSdkVersion" | "targetSdkVersion" | "compileSdkVersion"
    )
}

fn attr_name_from_res_id(id: u32) -> String {
    match id {
        0x0101_0001 => "label",
        0x0101_0002 => "icon",
        0x0101_0003 => "name",
        0x0101_021b => "versionCode",
        0x0101_021c => "versionName",
        0x0101_000f => "debuggable",
        0x0101_020c => "minSdkVersion",
        0x0101_0270 => "targetSdkVersion",
        0x0101_0572 => "compileSdkVersion",
        0x0101_0402 => "drawable",
        0x0101_03fb => "height",
        0x0101_03fc => "width",
        0x0101_0405 => "viewportWidth",
        0x0101_0406 => "viewportHeight",
        0x0101_0408 => "fillColor",
        0x0101_0409 => "pathData",
        0x0101_040c => "strokeColor",
        0x0101_040d => "strokeWidth",
        0x0101_0411 => "fillType",
        0x0101_031a => "pivotX",
        0x0101_031b => "pivotY",
        0x0101_0320 => "translateX",
        0x0101_0321 => "translateY",
        0x0101_031c => "scaleX",
        0x0101_031d => "scaleY",
        0x0101_031e => "rotation",
        0x0101_0176 => "insetLeft",
        0x0101_0177 => "insetRight",
        0x0101_0178 => "insetTop",
        0x0101_0179 => "insetBottom",
        0x0101_019d => "color",
        0x0101_019e => "startColor",
        0x0101_019f => "endColor",
        0x0101_01a0 => "angle",
        0x0101_020b => "centerColor",
        0x0101_051d => "offset",
        _ => return format!("attr_0x{id:08x}"),
    }
    .to_owned()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn maps_sdk_resource_ids_to_android_attrs() {
        assert_eq!(attr_name_from_res_id(0x0101_020c), "minSdkVersion");
        assert_eq!(attr_name_from_res_id(0x0101_0270), "targetSdkVersion");
        assert_eq!(attr_name_from_res_id(0x0101_0572), "compileSdkVersion");
    }
}
