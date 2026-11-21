// SPDX-License-Identifier: GPL-3.0-only
//! FMOD 3 CE sample channels and tracker music used by Age of Empires Pocket PC.
use crate::{import_name, CallCtx};
use pocket_kernel::{DispatchOutcome, GuestFormat, KernelError, KernelState};
use std::{
    cell::RefCell,
    collections::HashMap,
    ffi::{c_int, c_long, c_void},
    sync::Arc,
};

#[link(name = "xmp-lite", kind = "static")]
extern "C" {
    fn xmp_create_context() -> *mut c_void;
    fn xmp_free_context(c: *mut c_void);
    fn xmp_load_module_from_memory(c: *mut c_void, data: *const c_void, size: c_long) -> c_int;
    fn xmp_start_player(c: *mut c_void, rate: c_int, format: c_int) -> c_int;
    fn xmp_end_player(c: *mut c_void);
    fn xmp_release_module(c: *mut c_void);
    fn xmp_restart_module(c: *mut c_void);
    fn xmp_play_buffer(c: *mut c_void, out: *mut c_void, size: c_int, loops: c_int) -> c_int;
}

struct Song {
    context: *mut c_void,
    looped: bool,
    paused: bool,
    playing: bool,
    volume: i32,
}
impl Song {
    fn load(bytes: &[u8], rate: u32) -> Option<Self> {
        unsafe {
            let c = xmp_create_context();
            if c.is_null() {
                return None;
            }
            if xmp_load_module_from_memory(c, bytes.as_ptr().cast(), bytes.len() as c_long) != 0 {
                xmp_free_context(c);
                return None;
            }
            if xmp_start_player(c, rate as i32, 0) != 0 {
                xmp_release_module(c);
                xmp_free_context(c);
                return None;
            }
            Some(Self {
                context: c,
                looped: true,
                paused: false,
                playing: false,
                volume: 256,
            })
        }
    }
}
impl Drop for Song {
    fn drop(&mut self) {
        unsafe {
            xmp_end_player(self.context);
            xmp_release_module(self.context);
            xmp_free_context(self.context);
        }
    }
}
struct Sample {
    pcm: Vec<i16>,
    format: GuestFormat,
    looped: bool,
}
struct Voice {
    sample: Arc<Sample>,
    phase: u64,
    paused: bool,
}
#[derive(Default)]
struct Mixer {
    rate: u32,
    next: u32,
    channel_limit: u32,
    volume: i32,
    samples: HashMap<u32, Arc<Sample>>,
    voices: HashMap<u32, Voice>,
    songs: HashMap<u32, Song>,
}
thread_local! { static MIX: RefCell<Mixer> = RefCell::new(Mixer::default()); }

/// Refill the Android PCM queue from active guest channels. Leave waveOut alone while FMOD is idle.
pub fn pump(kernel: &KernelState) {
    MIX.with(|cell| {
        let mut m = cell.borrow_mut();
        if m.rate == 0 || (m.voices.is_empty() && !m.songs.values().any(|s| s.playing && !s.paused))
        {
            return;
        }
        let count = (m.rate as usize / 10 * 2).saturating_sub(kernel.audio.buffered_samples()) & !1;
        if count == 0 {
            return;
        }
        let mut mixed = vec![0i32; count];
        let mut track = vec![0i16; count];
        for s in m.songs.values_mut().filter(|s| s.playing && !s.paused) {
            // libxmp returns -XMP_END at the first loop when looping is disabled.
            if unsafe {
                xmp_play_buffer(
                    s.context,
                    track.as_mut_ptr().cast(),
                    (count * 2) as i32,
                    (!s.looped) as i32,
                )
            } != 0
            {
                s.playing = false;
                continue;
            }
            for (out, v) in mixed.iter_mut().zip(&track) {
                *out += *v as i32 * s.volume / 256;
            }
        }
        let rate = m.rate as u64;
        let volume = m.volume;
        m.voices.retain(|_, v| {
            if v.paused {
                return true;
            }
            let sample = &v.sample;
            let channels = sample.format.channels as usize;
            let frames = sample.pcm.len() / channels;
            for stereo in mixed.chunks_exact_mut(2) {
                if v.phase / rate >= frames as u64 {
                    if !sample.looped {
                        return false;
                    }
                    v.phase %= frames as u64 * rate;
                }
                let i = (v.phase / rate) as usize * channels;
                stereo[0] += sample.pcm[i] as i32 * volume / 255;
                stereo[1] += sample.pcm[i + channels - 1] as i32 * volume / 255;
                v.phase += sample.format.sample_rate as u64;
            }
            true
        });
        for (v, sum) in track.iter_mut().zip(mixed) {
            *v = sum.clamp(-32768, 32767) as i16;
        }
        kernel.audio.push_samples(&track);
    })
}

fn file(ctx: &mut CallCtx<'_>, p: u32) -> Option<Vec<u8>> {
    let start = ctx.cpu.read_mem(p, 2).ok()?;
    let path = if start[1] == 0 {
        crate::coredll::read_guest_wstr_lossy(ctx, p, 1024).ok()?
    } else {
        let mut bytes = Vec::new();
        for i in 0..1024 {
            let b = ctx.cpu.read_mem(p.checked_add(i)?, 1).ok()?[0];
            if b == 0 {
                break;
            }
            bytes.push(b);
        }
        String::from_utf8_lossy(&bytes).into_owned()
    };
    crate::coredll::read_guest_file_for(ctx, &path)
}

pub fn call(ctx: &mut CallCtx<'_>) -> Result<Option<DispatchOutcome>, KernelError> {
    let name = import_name(ctx.thunk).into_owned();
    let a = ctx.arg_u32(0)?;
    let b = ctx.arg_u32(1)?;
    let result = MIX.with(|cell| -> Result<Option<u32>, KernelError> {
        let mut m = cell.borrow_mut();
        let value = match name.as_str() {
            "FSOUND_Init" => {
                *m = Mixer::default();
                m.rate = a.clamp(8000, 96000);
                m.channel_limit = b.clamp(1, 256);
                m.volume = 255;
                ctx.kernel.audio.set_guest_format(GuestFormat {
                    sample_rate: m.rate,
                    channels: 2,
                    bits_per_sample: 16,
                });
                1
            }
            "FSOUND_Close" => {
                *m = Mixer::default();
                1
            }
            "FMUSIC_LoadSong" => {
                let Some(data) = file(ctx, a) else {
                    return Ok(Some(0));
                };
                let Some(song) = Song::load(&data, m.rate) else {
                    return Ok(Some(0));
                };
                m.next += 1;
                let id = m.next;
                m.songs.insert(id, song);
                id
            }
            "FMUSIC_PlaySong" => {
                if let Some(s) = m.songs.get_mut(&a) {
                    unsafe {
                        xmp_restart_module(s.context);
                    }
                    s.playing = true;
                    s.paused = false;
                    1
                } else {
                    0
                }
            }
            "FMUSIC_SetLooping" => {
                if let Some(s) = m.songs.get_mut(&a) {
                    s.looped = b != 0;
                }
                1
            }
            "FMUSIC_SetPaused" => {
                if let Some(s) = m.songs.get_mut(&a) {
                    s.paused = b != 0;
                }
                1
            }
            "FMUSIC_SetMasterVolume" => {
                if let Some(s) = m.songs.get_mut(&a) {
                    s.volume = b.min(256) as i32;
                }
                1
            }
            "FMUSIC_IsFinished" => (!m.songs.get(&a).is_some_and(|s| s.playing)) as u32,
            "FMUSIC_StopSong" => {
                if let Some(s) = m.songs.get_mut(&a) {
                    s.playing = false;
                }
                1
            }
            "FMUSIC_StopAllSongs" => {
                for s in m.songs.values_mut() {
                    s.playing = false;
                }
                1
            }
            "FMUSIC_FreeSong" => {
                m.songs.remove(&a);
                1
            }
            "FSOUND_Sample_Load" => {
                let flags = ctx.arg_u32(2)?;
                let size_or_offset = ctx.arg_u32(3)?;
                let bytes = if flags & 0x8000 != 0 {
                    // Earlier FMOD CE passes a byte count in r3; later revisions use
                    // (offset, length). The embedded RIFF header identifies the base.
                    let base = ctx.cpu.read_mem(b, 4).ok();
                    let pos = if base.as_deref() == Some(&b"RIFF"[..]) {
                        b
                    } else if let Some(p) = b.checked_add(size_or_offset) {
                        p
                    } else {
                        return Ok(Some(0));
                    };
                    let Ok(header) = ctx.cpu.read_mem(pos, 12) else {
                        return Ok(Some(0));
                    };
                    if &header[..4] != b"RIFF" {
                        return Ok(Some(0));
                    }
                    let len =
                        u32::from_le_bytes(header[4..8].try_into().unwrap()).saturating_add(8);
                    if len > 64 * 1024 * 1024 {
                        return Ok(Some(0));
                    }
                    ctx.cpu.read_mem(pos, len).ok()
                } else {
                    file(ctx, b).and_then(|bytes| {
                        if bytes.starts_with(b"RIFF") {
                            Some(bytes)
                        } else {
                            bytes.get(size_or_offset as usize..).map(|s| s.to_vec())
                        }
                    })
                };
                let Some((format, pcm)) =
                    bytes.as_deref().and_then(crate::coredll::decode_pcm_wave)
                else {
                    return Ok(Some(0));
                };
                if !(1..=2).contains(&format.channels) || pcm.len() < format.channels as usize {
                    return Ok(Some(0));
                }
                m.next += 1;
                let id = m.next;
                m.samples.insert(
                    id,
                    Arc::new(Sample {
                        pcm,
                        format,
                        looped: flags & 6 != 0,
                    }),
                );
                id
            }
            "FSOUND_PlaySound" => {
                let Some(sample) = m.samples.get(&b).cloned() else {
                    return Ok(Some(u32::MAX));
                };
                let ch = if a == u32::MAX {
                    (0..m.channel_limit)
                        .find(|i| !m.voices.contains_key(i))
                        .unwrap_or(0)
                } else {
                    a
                };
                m.voices.insert(
                    ch,
                    Voice {
                        sample,
                        phase: 0,
                        paused: false,
                    },
                );
                ch
            }
            "FSOUND_IsPlaying" => m.voices.contains_key(&a) as u32,
            "FSOUND_StopSound" => {
                if a == (-3i32) as u32 {
                    m.voices.clear();
                } else {
                    m.voices.remove(&a);
                }
                1
            }
            "FSOUND_Sample_Free" => {
                m.samples.remove(&a);
                1
            }
            "FSOUND_SetPaused" => {
                for (id, v) in &mut m.voices {
                    if a == (-3i32) as u32 || *id == a {
                        v.paused = b != 0;
                    }
                }
                1
            }
            "FSOUND_SetSFXMasterVolume" => {
                m.volume = a.min(255) as i32;
                1
            }
            _ => return Ok(None),
        };
        Ok(Some(value))
    })?;
    Ok(result.map(DispatchOutcome::ReturnedR0))
}
