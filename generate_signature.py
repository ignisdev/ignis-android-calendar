#!/usr/bin/env python3
import hashlib
import base64

# Convert SHA1 fingerprint to bytes
sha1_hex = "967EB2CEB0F6539F1192646E2EA74DCABBF0A70D"
sha1_bytes = bytes.fromhex(sha1_hex)

# Generate signature hash using MSAL algorithm
package_name = "com.meetingroom.display"
combined = package_name.encode() + sha1_bytes

# Hash and encode
hash_result = hashlib.sha1(combined).digest()
signature_hash = base64.b64encode(hash_result).decode().rstrip('=').replace('+', '-').replace('/', '_')

print(f"Package: {package_name}")
print(f"SHA1: {sha1_hex}")  
print(f"Signature Hash: {signature_hash}")
print(f"Redirect URI: msauth://{package_name}/{signature_hash}%3D")