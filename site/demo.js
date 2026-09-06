/* ExifDrop demo. Reads a local image's EXIF/GPS with a hand-rolled binary
 * decoder + segment strip. No upload: FileReader + Blob, pixels never re-encoded. */

(function () {
  'use strict';

  var zone = null, input = null, found = null, foundList = null, stripBtn = null;
  var result = null, resultRemoved = null, resultNote = null, downloadBtn = null;
  var againBtns = null, errBox = null, errText = null;
  var doc = typeof document !== 'undefined' ? document : null;
  if (doc) {
    zone = doc.getElementById('dropzone');
    input = doc.getElementById('file');
    found = doc.getElementById('found');
    foundList = doc.getElementById('found-list');
    stripBtn = doc.getElementById('strip-btn');
    result = doc.getElementById('result');
    resultRemoved = doc.getElementById('result-removed');
    resultNote = doc.getElementById('result-note');
    downloadBtn = doc.getElementById('download-btn');
    againBtns = doc.querySelectorAll('#again-btn, #error-again');
    errBox = doc.getElementById('error');
    errText = doc.getElementById('error-text');
  }

  var fileBuffer = null;      /* last ArrayBuffer opened */
  var fileName = '';

  /* ================= EXIF decoder ================= */

  /* A little endianness-aware reader over a Uint8Array, absolute offsets. */
  function makeReader(buf) {
    return {
      ascii: function (o, n) { var s = ''; for (var i = 0; i < n; i++) s += String.fromCharCode(buf[o + i]); return s; },
      strlen: function (o, max) { var s = '', c; for (; o < max; o++) { c = buf[o]; if (!c) break; s += String.fromCharCode(c); } return s; }
    };
  }

  /* Parse a TIFF block. u8 is the full image bytes, tiffOff points at 'II'/MM. */
  function parseTiff(u8, tiffOff) {
    var le = u8[tiffOff] === 0x49; /* I */
    var r = makeReader(u8);
    function u16(o) { return le ? u8[o] | (u8[o + 1] << 8) : (u8[o] << 8) | u8[o + 1]; }
    function u32(o) { return le ? u8[o] | (u8[o + 1] << 8) | (u8[o + 2] << 16) | (u8[o + 3] << 24)
                               : (u8[o] << 24) | (u8[o + 1] << 16) | (u8[o + 2] << 8) | u8[o + 3]; }
    var magic = u16(tiffOff + 2);
    if (magic !== 42) return null;
    var ifd0 = tiffOff + u32(tiffOff + 4);

    var report = { ifds: [] };
    var done = 0;

    /* Walk one IFD: collection of entries. Returns { entries:[[tag,type,count,value],...], next } */
    function readIfd(off) {
      var n = u16(off);
      var entries = [];
      for (var i = 0; i < n; i++) {
        var e = off + 2 + i * 12;
        var tag = u16(e), type = u16(e + 2), cnt = u32(e + 4);
        var val = u32(e + 8);
        entries.push({ tag: tag, type: type, count: cnt, val: val, off: e });
      }
      var next = u32(off + 2 + n * 12);
      return { entries: entries, next: next };
    }

    var ifd = readIfd(ifd0); done++;
    if (!ifd.entries.length) return null;
    report.ifds.push(ifd);
    var visited = [ifd0];

    function entryValue(entry) {
      /* value is inline if typeSize*count <= 4 */
      var typeSize = { 1: 1, 2: 1, 3: 2, 4: 4, 5: 8, 6: 1, 7: 1, 9: 4, 10: 8 }[entry.type] || 1;
      /* Value is inline in the 4-byte field iff typeSize*count <= 4. */
      /* For string types (2 ASCII, 3 short pairs), do inline read if short. */
      if (entry.type === 2) {
        var p = (typeSize * entry.count <= 4) ? (entry.off + 8) : tiffOff + entry.val;
        return r.strlen(p, Math.min(p + entry.count, u8.length));
      }
      if (entry.type === 3) {
        var useShort = typeSize * entry.count <= 4;
        if (useShort && (entry.count === 1 || entry.count === 2)) {
          var vals = [];
          for (var sIdx = 0; sIdx < entry.count; sIdx++) vals.push(u16(entry.off + 8 + sIdx * 2));
          return vals.join(', ');
        }
        var off3 = tiffOff + entry.val, s3 = '';
        for (var i = 0; i < Math.min(entry.count, 8); i++) s3 += String.fromCharCode(u8[off3 + i * 2]);
        return s3;
      }
      if (entry.type === 4) return entry.count === 1 ? entry.val : entry.val;
      if (entry.type === 5) {
        var off5 = tiffOff + entry.val; var nums = [];
        for (var j = 0; j < Math.min(entry.count, 3); j++) {
          var num = u32(off5 + j * 8), den = u32(off5 + j * 8 + 4);
          nums.push(den ? num / den : 0);
        }
        return nums.map(function (v) { return Math.round(v * 1e6) / 1e6; });
      }
      if (entry.type === 1) return entry.val;
      return null;
    }

    /* Flatten first IFD (usually 0). Resolve sub-IFD pointers: 0x8769 Exif, 0x8825 GPS, 0xA005 Interop. */
    var byTag = {};
    ifd.entries.forEach(function (en) { byTag[en.tag] = en; });

    if (byTag[0x8769] && done < 4) {
      var exifOff = tiffOff + byTag[0x8769].val;
      var exifIfd = readIfd(exifOff); done++;
      report.ifds.push(exifIfd);
      exifIfd.entries.forEach(function (en) { byTag[en.tag] = byTag[en.tag] || en; });
    }

    var gpsEntries = [];
    if (byTag[0x8825]) {
      var gpsOff = tiffOff + byTag[0x8825].val;
      var gpsIfd = readIfd(gpsOff); done++;
      report.ifds.push(gpsIfd);
      gpsEntries = gpsIfd.entries;
    }

    /* Compose readable fields */
    var out = {};
    function tag(t) { return byTag[t]; }
    function str(t) { var e = tag(t); return e ? entryValue(e) : null; }
    var gps = {};
    gpsEntries.forEach(function (e) { gps[e.tag] = e; });

    if (gps[1] && gps[2]) { out.GPSLatitude = fmtGPS(gps[2], entryValue(gps[1]) === 'S' ? -1 : 1); }
    if (gps[3] && gps[4]) { out.GPSLongitude = fmtGPS(gps[4], entryValue(gps[3]) === 'W' ? -1 : 1); }

    var dt = str(0x9003) || str(0x0132);
    if (dt) out.DateTimeOriginal = dt;
    var make = str(0x010F); if (make) out.Make = make;
    var model = str(0x0110); if (model) out.Model = model;
    var soft = str(0x0131); if (soft) out.Software = soft;
    var tiffVals = {};
    out._meta = tiffVals;
    report.ifds.forEach(function (ifd2) {
      ifd2.entries.forEach(function (en) {
        var v = entryValue(en);
        if (v !== null && v !== undefined) {
          if (typeof v === 'object') v = v.join(', ');
          out[en.tag] = v;
        }
      });
    });
    return out;

    function fmtGPS(entry, sign) {
      var nums = entryValue(entry);
      if (!Array.isArray(nums)) return sign > 0 ? '' : '';
      var d = nums[0], m = nums[1], s = nums[2] || 0;
      return (sign < 0 ? '-' : '') + d + '°' + m + '′' + Math.round(s) + '″';
    }
  }

  /* JPEG: scan markers, find APP1 'Exif\0\0' (FFE1), parse that TIFF. */
  function readJpeg(u8) {
    var o = 2;
    var out = {};
    while (o + 4 <= u8.length) {
      if (u8[o] !== 0xFF) { o++; continue; }
      var m = u8[o + 1];
      if (m === 0xD8 || m === 0xD9) { o += 2; continue; }
      if (m >= 0xD0 && m <= 0xD7) { o += 2; continue; }
      var len = (u8[o + 2] << 8) | u8[o + 3];
      if (len < 2) break;
      if (m === 0xE1 && u8[o + 4] === 0x45 && u8[o + 9] === 0 && u8[o + 5] === 0x78) {
        var tiff = o + 4 + 6; /* skip 'Exif\0\0' */
        var ex = parseTiff(u8, tiff);
        if (ex) { for (var k in ex) out[k] = ex[k]; }
        break;
      }
      o += 2 + len;
    }
    return out;
  }

  /* PNG: tEXt/zTXt/iTXt/tIME chunks. */
  function readPng(u8) {
    var o = 8, out = {};
    while (o + 8 <= u8.length) {
      var len = (u8[o] << 24) | (u8[o + 1] << 16) | (u8[o + 2] << 8) | u8[o + 3];
      var type = String.fromCharCode(u8[o + 4]) + String.fromCharCode(u8[o + 5]) + String.fromCharCode(u8[o + 6]) + String.fromCharCode(u8[o + 7]);
      var d = o + 8;
      if (type === 'tEXt') {
        var keyEnd = d; while (keyEnd < d + len && u8[keyEnd]) keyEnd++;
        var key = String.fromCharCode.apply(null, u8.subarray(d, keyEnd));
        var val = String.fromCharCode.apply(null, u8.subarray(keyEnd + 1, d + len));
        out[key] = val;
      }
      if (len === 0 && type === 'IEND') break;
      o += 12 + len;
    }
    if (!Object.keys(out).length) out = null;
    return out;
  }

  /* WebP: EXIF and XMP chunks may appear before VP8. Parse EXIF chunk. */
  function readWebp(u8) {
    var o = 12, out = {};
    while (o + 8 <= u8.length) {
      var four = String.fromCharCode(u8[o]) + String.fromCharCode(u8[o + 1]) + String.fromCharCode(u8[o + 2]) + String.fromCharCode(u8[o + 3]);
      var size = (u8[o + 4] | (u8[o + 5] << 8) | (u8[o + 6] << 16) | (u8[o + 7] << 24)) >>> 0;
      var d = o + 8;
      if (size > u8.length - d) break;
      if (four === 'EXIF') {
        var tiff = d;
        if (u8[tiff] === 0x49 || u8[tiff] === 0x4D) {
          var ex = parseTiff(u8, tiff);
          if (ex) { for (var k in ex) out[k] = ex[k]; }
        }
      } else if (four === 'XMP') {
        out.XMP = 'present (XMP packet)';
      }
      if (four === 'VP8 ' || four === 'VP8L' || four === 'VP8X') break;
      o += 8 + size + (size % 2);
    }
    return out;
  }

  /* ================= strip ================= */

  /* JPEG: drop APP1(EXIF), APP2(ICC), APP13(IPTC) and APP14(Adobe); keep APP0 JFIF.
   * SOS has no length after the header, so stop advancing the scan there and
   * carry the entropy + EOI through verbatim. */
  function stripJpeg(u8) {
    var out = [0xFF, 0xD8];
    var o = 2;
    while (o + 2 < u8.length && u8[o] === 0xFF) {
      var m = u8[o + 1];
      if (m === 0xD8 || m === 0xD9 || (m >= 0xD0 && m <= 0xD7)) { o++; continue; } /* SOI/EOI/RSTn */
      if (m === 0xDA) { /* SOS: marker + length(2) + header; the rest is entropy. */
        var sosLen = (u8[o + 2] << 8) | u8[o + 3];
        var hEnd = o + 2 + sosLen;
        for (var h = o; h < hEnd && h < u8.length; h++) out.push(u8[h]);
        for (var e = hEnd; e < u8.length; e++) out.push(u8[e]); /* entropy + EOI verbatim */
        break;
      }
      var len = (u8[o + 2] << 8) | u8[o + 3];
      if (len < 2) break;
      var segEnd = o + 2 + len;
      var drop = (m === 0xE1) || (m === 0xE2) || (m === 0xE3) || (m === 0xED) || (m === 0xEE);
      if (!drop) for (var i = o; i < segEnd; i++) out.push(u8[i]);
      o = segEnd;
    }
    return new Uint8Array(out);
  }

  function stripPng(u8) {
    var out = Array.prototype.slice.call(u8.subarray(0, 8));
    var o = 8;
    while (o + 8 <= u8.length) {
      var len = (u8[o] << 24) | (u8[o + 1] << 16) | (u8[o + 2] << 8) | u8[o + 3];
      var type = String.fromCharCode(u8[o + 4]) + String.fromCharCode(u8[o + 5]) + String.fromCharCode(u8[o + 6]) + String.fromCharCode(u8[o + 7]);
      if (type !== 'tEXt' && type !== 'zTXt' && type !== 'iTXt' && type !== 'tIME') {
        for (var i = o; i < o + 12 + len; i++) out.push(u8[i]);
      }
      if (len === 0 && type === 'IEND') break;
      o += 12 + len;
    }
    return new Uint8Array(out);
  }

  /* WebP: rebuild the RIFF, dropping EXIF/XMP chunks (they precede the image
   * data), then patch the RIFF size. ICCP is kept for color fidelity. */
  function stripWebp(u8) {
    var out = Array.prototype.slice.call(u8.subarray(0, 12)); /* 'RIFF' + size + 'WEBP' */
    var o = 12, chunkBytes = 0;
    while (o + 8 <= u8.length) {
      var four = String.fromCharCode(u8[o]) + String.fromCharCode(u8[o + 1]) + String.fromCharCode(u8[o + 2]) + String.fromCharCode(u8[o + 3]);
      var size = (u8[o + 4] | (u8[o + 5] << 8) | (u8[o + 6] << 16) | (u8[o + 7] << 24)) >>> 0;
      var chunkLen = 8 + size + (size % 2);
      if (four !== 'EXIF' && four !== 'XMP ') {
        for (var i = o; i < o + chunkLen && i < u8.length; i++) out.push(u8[i]);
        chunkBytes += chunkLen;
      }
      o += chunkLen;
    }
    var riffLen = 4 + chunkBytes; /* 'WEBP' + chunks */
    out[4] = riffLen & 0xFF; out[5] = (riffLen >> 8) & 0xFF; out[6] = (riffLen >> 16) & 0xFF; out[7] = (riffLen >> 24) & 0xFF;
    return new Uint8Array(out);
  }

  function makeBlob(arr, type) { return new Blob([arr], { type: type }); }

  /* ================= UI ================= */

  function handleFile(file) {
    fileName = file.name;
    var reader = new FileReader();
    reader.onload = function (ev) {
      var u8 = new Uint8Array(ev.target.result);
      fileBuffer = u8;
      var ext = (file.name.split('.').pop() || '').toLowerCase();
      var meta;
      if (ext === 'png') meta = readPng(u8);
      else if (ext === 'webp') meta = readWebp(u8);
      else meta = readJpeg(u8);
      showFound(meta, file);
    };
    reader.onerror = function () { showError('Could not read that file. Try a JPEG, PNG or WebP.'); };
    reader.readAsArrayBuffer(file);
  }

  var MAP = {
    GPSLatitude: 'GPS latitude', GPSLongitude: 'GPS longitude',
    Make: 'Camera make', Model: 'Camera model',
    DateTimeOriginal: 'Date taken', Software: 'Software',
    XMP: 'XMP packet'
  };

  function showFound(meta, file) {
    var rows = [];
    if (!meta || !Object.keys(meta).length) {
      rows.push(['Metadata', 'No EXIF tags detected. Already clean.']);
    } else {
      Object.keys(meta).forEach(function (k) {
        if (k === '_meta') return;
        var label = MAP[k] || k;
        if (label === k) return; /* unknown hex tags: skip */
        var v = String(meta[k]);
        rows.push([label, v]);
      });
      if (!rows.length) rows.push(['Metadata', 'No readable EXIF tags found.']);
    }
    renderFound(rows, meta && Object.keys(meta).length ? 'A share would leak this' : 'Already clean');
    found.hidden = false; zone.hidden = true;
  }

  function renderFound(rows, title) {
    document.getElementById('found-title').textContent = title;
    foundList.innerHTML = '';
    rows.forEach(function (r) {
      var d = document.createElement('div');
      var dt = document.createElement('dt'), dd = document.createElement('dd');
      dt.textContent = r[0]; dd.textContent = r[1];
      d.appendChild(dt); d.appendChild(dd);
      foundList.appendChild(d);
    });
  }

  function stripAndOffer() {
    if (!fileBuffer) return showError('No file loaded.');
    var ext = (fileName.split('.').pop() || '').toLowerCase();
    var clean, type;
    if (ext === 'png') { clean = stripPng(fileBuffer); type = 'image/png'; }
    else if (ext === 'webp') { clean = stripWebp(fileBuffer); type = 'image/webp'; }
    else { clean = stripJpeg(fileBuffer); type = 'image/jpeg'; }

    var prefix = fileName.replace(/\.[^/.]+$/, '');
    var outName = 'clean_' + prefix + '.' + ext;
    downloadBtn.href = URL.createObjectURL(makeBlob(clean, type));
    downloadBtn.setAttribute('download', outName);

    resultRemoved.textContent = 'Removed EXIF, GPS, XMP and IPTC segments. Pixels untouched.';
    resultNote.textContent = 'Clean copy is ' + Math.max(1, Math.round(clean.length / 1024)) + ' KB. Same image, no tags.';
    found.hidden = true; result.hidden = false;
    result.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  }

  function showError(msg) { errText.textContent = msg; errBox.hidden = false; found.hidden = true; result.hidden = true; zone.hidden = true; }
  function reset() { found.hidden = true; result.hidden = true; errBox.hidden = true; zone.hidden = false; if (downloadBtn.href.startsWith('blob:')) URL.revokeObjectURL(downloadBtn.href); fileBuffer = null; }

  if (doc && zone) {
  zone.addEventListener('click', function () { input.click(); });
  zone.addEventListener('keydown', function (e) { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); input.click(); } });
  zone.addEventListener('dragover', function (e) { e.preventDefault(); zone.style.borderColor = '#2E7D4F'; });
  zone.addEventListener('dragleave', function () { zone.style.borderColor = ''; });
  zone.addEventListener('drop', function (e) { e.preventDefault(); zone.style.borderColor = ''; var f = e.dataTransfer.files[0]; if (f) { input.files = e.dataTransfer.files; handleFile(f); } });
  input.addEventListener('change', function () { if (input.files[0]) handleFile(input.files[0]); });
  stripBtn.addEventListener('click', function (e) { e.preventDefault(); stripAndOffer(); });
  againBtns.forEach(function (b) { b.addEventListener('click', reset); });
}

  /* Self-check: build a realistic JPEG with EXIF APP1, strip it, assert APP1 gone + image data intact. */
  function selfCheck() {
    var seg = [0xFF, 0xD8];
    var tiff = [0x49, 0x49, 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00];
    var exif = [0x45, 0x78, 0x69, 0x66, 0x00, 0x00];
    var app1Len = 2 + exif.length + tiff.length;
    seg.push(0xFF, 0xE1, (app1Len >> 8) & 0xFF, app1Len & 0xFF);
    seg = seg.concat(exif, tiff);
    seg = seg.concat([0xFF, 0xDB, 0x00, 0x43, 0x00, 0x08, 0x06], [0xFF, 0xC0, 0x00, 0x0B, 0x08, 0x00, 0x80, 0x01, 0x00, 0x01, 0x01, 0x11, 0x00]);
    seg = seg.concat([0xFF, 0xDA, 0x00, 0x08, 0x01, 0x01, 0x00, 0x00, 0x00, 0x3F, 0x00], [0x24, 0x1A, 0x42, 0xFF, 0x00, 0x10], [0xFF, 0xD9]);
    var out = stripJpeg(new Uint8Array(seg));
    var hasApp1 = false, sawSos = false;
    for (var i = 0; i + 1 < out.length; i++) {
      if (out[i] === 0xFF && out[i + 1] === 0xE1) { hasApp1 = true; break; }
      if (out[i] === 0xFF && out[i + 1] === 0xDA) sawSos = true;
    }
    if (hasApp1 || !sawSos) throw new Error('selfCheck FAILED: APP1 leaked or SOS lost');
  }
  selfCheck();

  /* Node: expose pure functions for the local self-check (.test/strip.test.js). */
  if (typeof module !== 'undefined' && module.exports) {
    module.exports = { readJpeg: readJpeg, readPng: readPng, readWebp: readWebp, stripJpeg: stripJpeg, stripPng: stripPng, stripWebp: stripWebp };
  }
})();