import struct, hashlib, sys
def cert_hash(path):
    data = open(path,'rb').read()
    # End of central directory
    eocd = data.rfind(b'PK\x05\x06')
    cd_offset = struct.unpack('<I', data[eocd+16:eocd+20])[0]
    magic = data[cd_offset-16:cd_offset]
    assert magic == b'APK Sig Block 42', magic
    block_size = struct.unpack('<Q', data[cd_offset-24:cd_offset-16])[0]
    start = cd_offset - block_size - 8
    pos = start + 8
    end = cd_offset - 24
    while pos < end:
        ln = struct.unpack('<Q', data[pos:pos+8])[0]
        id_ = struct.unpack('<I', data[pos+8:pos+12])[0]
        val = data[pos+12:pos+8+ln]
        if id_ in (0x7109871a, 0xf05368c0):
            def lp(b, o):
                l = struct.unpack('<I', b[o:o+4])[0]; return b[o+4:o+4+l], o+4+l
            signers, _ = lp(val, 0)
            signer, _ = lp(signers, 0)
            signed_data, _ = lp(signer, 0)
            digests, o = lp(signed_data, 0)
            certs, o = lp(signed_data, o)
            cert, _ = lp(certs, 0)
            return hex(id_), hashlib.sha256(cert).hexdigest()
        pos += 8 + ln
for p in sys.argv[1:]:
    print(p, cert_hash(p))
