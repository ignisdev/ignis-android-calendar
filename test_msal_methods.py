#!/usr/bin/env python3
import hashlib
import base64

def test_msal_signature_methods(package_name, sha1_hex):
    """Test different methods MSAL might use to generate signatures"""
    
    print(f"Package: {package_name}")
    print(f"SHA1: {sha1_hex}")
    
    # Convert SHA1 to bytes
    sha1_bytes = bytes.fromhex(sha1_hex.replace(":", ""))
    package_bytes = package_name.encode('utf-8')
    
    print(f"\nMethod 1: Standard MSAL (package + sha1_bytes)")
    combined1 = package_bytes + sha1_bytes
    hash1 = hashlib.sha1(combined1).digest()
    sig1 = base64.b64encode(hash1).decode().rstrip('=').replace('+', '-').replace('/', '_')
    print(f"Result: {sig1}")
    
    print(f"\nMethod 2: Reverse order (sha1_bytes + package)")
    combined2 = sha1_bytes + package_bytes
    hash2 = hashlib.sha1(combined2).digest()
    sig2 = base64.b64encode(hash2).decode().rstrip('=').replace('+', '-').replace('/', '_')
    print(f"Result: {sig2}")
    
    print(f"\nMethod 3: SHA1 as string + package")
    combined3 = sha1_hex.encode('utf-8') + package_bytes
    hash3 = hashlib.sha1(combined3).digest()
    sig3 = base64.b64encode(hash3).decode().rstrip('=').replace('+', '-').replace('/', '_')
    print(f"Result: {sig3}")
    
    print(f"\nMethod 4: Package + SHA1 as string")
    combined4 = package_bytes + sha1_hex.encode('utf-8')
    hash4 = hashlib.sha1(combined4).digest()
    sig4 = base64.b64encode(hash4).decode().rstrip('=').replace('+', '-').replace('/', '_')
    print(f"Result: {sig4}")
    
    print(f"\nMethod 5: Standard Base64 without URL-safe conversion")
    hash5 = hashlib.sha1(combined1).digest()
    sig5 = base64.b64encode(hash5).decode().rstrip('=')
    print(f"Result: {sig5}")
    
    # Test if any match the expected
    expected = "ln6yzrD2U58RkmRuLqdNyrvwpw0"
    methods = [sig1, sig2, sig3, sig4, sig5]
    for i, method_result in enumerate(methods, 1):
        if method_result == expected:
            print(f"\n*** METHOD {i} MATCHES EXPECTED! ***")
            return method_result
    
    print(f"\nExpected: {expected}")
    print("No methods matched the expected signature")
    
    return None

# Test with the values from the error
package_name = "com.meetingroom.display"
sha1 = "967EB2CEB0F6539F1192646E2EA74DCABBF0A70D"

test_msal_signature_methods(package_name, sha1)

# Let's also try to brute force by testing if a different SHA1 could produce the expected result
print(f"\n{'='*50}")
print("TRYING TO FIND MATCHING SHA1...")

# Let's decode the expected signature and see what it should hash to
expected_sig = "ln6yzrD2U58RkmRuLqdNyrvwpw0"
expected_b64 = expected_sig + "=" if len(expected_sig) % 4 != 0 else expected_sig
expected_b64 = expected_b64.replace('-', '+').replace('_', '/')

try:
    expected_hash_bytes = base64.b64decode(expected_b64)
    print(f"Expected hash bytes: {expected_hash_bytes.hex().upper()}")
    print(f"This should be the SHA1 result of: package_name + some_sha1")
    
    # If we know the expected hash result, we need to find what SHA1 + package produces it
    # This is computationally intensive, but let's check if it's one of the common values
    
except Exception as e:
    print(f"Could not decode expected signature: {e}")
    
# Try with a different approach - maybe the SHA1 from a different keystore format
print(f"\nTrying with different SHA1 formats...")
alt_formats = [
    "96:7E:B2:CE:B0:F6:53:9F:11:92:64:6E:2E:A7:4D:CA:BB:F0:A7:0D",  # With colons
    "967eb2ceb0f6539f1192646e2ea74dcabbf0a70d",  # Lowercase
]

for alt_sha1 in alt_formats:
    print(f"\nTesting format: {alt_sha1}")
    clean_sha1 = alt_sha1.replace(":", "").upper()
    test_msal_signature_methods(package_name, clean_sha1)