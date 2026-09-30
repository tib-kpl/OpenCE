"""Stops the Android guest image from printing silent errors on screen.

The Xbox beta build prints error() messages of _error_silent priority to the
on-screen terminal as well as to debug.txt, so the missing Bink movies, the
first run's missing files and the map precaching show over the main menu.
This patches the built guest image, without recompiling:

- error()'s "if (priority != _error_log) terminal_printf(...)" becomes
  "if (priority < _error_silent)": immediate and delayed errors still show,
  silent ones only go to debug.txt;
- its "too many errors, only printing to debug.txt" notice (only reached
  for silent errors) is dropped.

Usage: python tools/android_hide_silent_errors.py build/android/assets/halo_guest.elf
The instructions are checked before they are changed; an image patched
already is left as it is.
"""

import struct
import sys

# AArch64
NOP = 0xD503201F


def load_segments(data):
    phoff, = struct.unpack_from("<Q", data, 0x20)
    phentsize, phnum = struct.unpack_from("<HH", data, 0x36)
    segments = []
    for index in range(phnum):
        kind, flags, offset, vaddr, _, filesz, _, _ = struct.unpack_from("<IIQQQQQQ", data, phoff + index * phentsize)
        if kind == 1:
            segments.append((vaddr, offset, filesz))
    return segments


def file_offset(segments, address):
    for vaddr, offset, filesz in segments:
        if vaddr <= address < vaddr + filesz:
            return offset + address - vaddr
    raise SystemExit(f"address {address:#x} is not in the image")


def symbol(data, name):
    """the address of a function symbol (the image keeps its symbol table)"""
    shoff, = struct.unpack_from("<Q", data, 0x28)
    shentsize, shnum = struct.unpack_from("<HH", data, 0x3A)
    sections = [struct.unpack_from("<IIQQQQIIQQ", data, shoff + index * shentsize) for index in range(shnum)]
    for section in sections:
        if section[1] != 2:  # SHT_SYMTAB
            continue
        strtab = sections[section[6]]
        for at in range(section[4], section[4] + section[5], 24):
            name_offset, info, _, _, value, _ = struct.unpack_from("<IBBHQQ", data, at)
            end = data.index(b"\0", strtab[4] + name_offset)
            if data[strtab[4] + name_offset:end].decode() == name:
                return value
    raise SystemExit(f"no symbol {name}")


def calls_to(data, segments, start, length, target):
    """the addresses of the BL instructions to target in start..start+length"""
    found = []
    for address in range(start, start + length, 4):
        word, = struct.unpack_from("<I", data, file_offset(segments, address))
        if word >> 26 == 0b100101:
            offset = word & 0x3FFFFFF
            if offset & 0x2000000:
                offset -= 0x4000000
            if address + offset * 4 == target:
                found.append(address)
    return found


def main():
    path = sys.argv[1]
    data = bytearray(open(path, "rb").read())
    segments = load_segments(data)
    error = symbol(data, "error")
    terminal_printf = symbol(data, "terminal_printf")
    calls = sorted(calls_to(data, segments, error, 0x400, terminal_printf))
    # the message, then the notice (gone once patched)
    if len(calls) not in (1, 2):
        raise SystemExit(f"error() calls terminal_printf {len(calls)} times, expected 2: unknown build")
    message = calls[0]
    notice = calls[1] if len(calls) == 2 else None

    # the branch over the message's call: "cmp w8, #3 (_error_log); b.eq"
    changed = 0
    for address in range(message - 0x40, message, 4):
        at = file_offset(segments, address)
        word, = struct.unpack_from("<I", data, at)
        previous, = struct.unpack_from("<I", data, at - 4)
        if (word & 0xFF00001F in (0x54000000, 0x54000002) and previous & 0xFFC0001F == 0x7100001F
                and (previous >> 10) & 0xFFF in (2, 3)):  # cmp wN, #3 (#2 once patched)
            if word & 0xF == 0:  # b.eq -> b.hs (unsigned >=): priority >= _error_silent
                struct.pack_into("<I", data, at - 4, (previous & ~(0xFFF << 10)) | (2 << 10))  # cmp #2
                struct.pack_into("<I", data, at, (word & ~0xF) | 2)
                changed += 1
            break
    else:
        raise SystemExit("the priority test before terminal_printf was not found: unknown build")

    if notice is not None:
        struct.pack_into("<I", data, file_offset(segments, notice), NOP)
        changed += 1

    open(path, "wb").write(data)
    print(f"{path}: {changed} instruction(s) patched" if changed else f"{path}: already patched")


if __name__ == "__main__":
    main()
