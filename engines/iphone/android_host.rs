// SPDX-License-Identifier: MPL-2.0
use std::ffi::c_void;
use std::io::{Read, Write};
use std::os::fd::{BorrowedFd, RawFd};
use std::sync::atomic::{AtomicBool, Ordering};

static PAUSED: AtomicBool = AtomicBool::new(false);

pub fn wait_if_paused() {
    while PAUSED.load(Ordering::Relaxed) {
        std::thread::sleep(std::time::Duration::from_millis(8));
    }
}

#[no_mangle]
pub extern "system" fn Java_io_github_eriark_abyssme_iphone_IphoneActivity_pauseGuest(
    _: *mut c_void, _: *mut c_void, paused: u8,
) {
    let mut clock = CLOCK.lock().unwrap();
    clock.anchor();
    clock.paused = paused != 0;
    PAUSED.store(paused != 0, Ordering::Relaxed);
}

// Java owns the original file descriptors for the duration of this call.
fn file(fd: RawFd) -> std::io::Result<std::fs::File> {
    unsafe { BorrowedFd::borrow_raw(fd) }.try_clone_to_owned().map(Into::into)
}
#[no_mangle]
pub extern "system" fn Java_io_github_eriark_abyssme_iphone_IphoneLibrary_decodeIcon(
    _: *mut c_void, _: *mut c_void, png_fd: RawFd, rgba_fd: RawFd,
) -> u8 {
    let decode = || -> Option<()> {
        let mut bytes = Vec::new();
        file(png_fd).ok()?.take(16 << 20).read_to_end(&mut bytes).ok()?;
        // Icons are PNGs, including preprocessed CgBI files. Check IHDR before decoding.
        if !bytes.starts_with(b"\x89PNG\r\n\x1a\n") { return None; }
        let mut chunks = bytes.get(8..)?;
        loop {
            let len = u32::from_be_bytes(chunks.get(..4)?.try_into().ok()?) as usize;
            if chunks.get(4..8)? == b"IHDR" {
                let w = u32::from_be_bytes(chunks.get(8..12)?.try_into().ok()?);
                let h = u32::from_be_bytes(chunks.get(12..16)?.try_into().ok()?);
                if w == 0 || h == 0 || w > 2048 || h > 2048 { return None; }
                break;
            }
            chunks = chunks.get(len.checked_add(12)?..)?;
        }
        let image = crate::image::Image::from_bytes(&bytes).ok()?;
        let mut output = file(rgba_fd).ok()?;
        let (w,h) = image.dimensions();
        output.write_all(&w.to_be_bytes()).ok()?;
        output.write_all(&h.to_be_bytes()).ok()?;
        output.write_all(image.pixels()).ok()?;
        Some(())
    };
    u8::from(decode().is_some())
}

use std::sync::{LazyLock, Mutex};
use std::time::{Duration, Instant};

struct Clock {
    anchor: Instant,
    guest: Instant,
    percent: u32,
    paused: bool,
}

impl Clock {
    fn at(&self, now: Instant) -> Instant {
        if self.paused { return self.guest; }
        self.guest + (now - self.anchor).mul_f64(f64::from(self.percent) / 100.0)
    }
    fn anchor(&mut self) {
        let now = Instant::now();
        self.guest = self.at(now);
        self.anchor = now;
    }
}

static CLOCK: LazyLock<Mutex<Clock>> = LazyLock::new(|| {
    let now = Instant::now();
    Mutex::new(Clock { anchor: now, guest: now, percent: 100, paused: false })
});

pub fn now() -> Instant {
    CLOCK.lock().unwrap().at(Instant::now())
}

pub fn sleep(duration: Duration) {
    let deadline = now() + duration;
    loop {
        let clock = CLOCK.lock().unwrap();
        let remaining = deadline.saturating_duration_since(clock.at(Instant::now()));
        if remaining.is_zero() { return; }
        let wait = remaining.div_f64(f64::from(clock.percent) / 100.0);
        drop(clock);
        std::thread::sleep(wait.min(Duration::from_millis(20)));
    }
}

#[no_mangle]
pub extern "system" fn Java_io_github_eriark_abyssme_iphone_IphoneActivity_setGuestSpeed(
    _: *mut c_void, _: *mut c_void, percent: i32,
) {
    let mut clock = CLOCK.lock().unwrap();
    clock.anchor();
    clock.percent = percent.max(1) as u32;
}
