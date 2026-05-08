pub fn human_size(size: u64) -> String {
    let mut value = size as f64;
    for unit in ["B", "KB", "MB", "GB"] {
        if value < 1024.0 || unit == "GB" {
            return if unit == "B" {
                format!("{} B", value as u64)
            } else {
                format!("{value:.1} {unit}")
            };
        }
        value /= 1024.0;
    }
    format!("{size} B")
}

pub fn byte_at(data: &[u8], offset: usize) -> Result<u8, String> {
    data.get(offset)
        .copied()
        .ok_or_else(|| "二进制 XML 读取越界".to_owned())
}

pub fn u16_at(data: &[u8], offset: usize) -> Result<u16, String> {
    let bytes = data
        .get(offset..offset + 2)
        .ok_or_else(|| "二进制 XML 读取越界".to_owned())?;
    Ok(u16::from_le_bytes([bytes[0], bytes[1]]))
}

pub fn u32_at(data: &[u8], offset: usize) -> Result<u32, String> {
    let bytes = data
        .get(offset..offset + 4)
        .ok_or_else(|| "二进制 XML 读取越界".to_owned())?;
    Ok(u32::from_le_bytes([bytes[0], bytes[1], bytes[2], bytes[3]]))
}
