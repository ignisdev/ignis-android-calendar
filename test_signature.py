#!/usr/bin/env python3
import hashlib
import base64
import sys

def generate_msal_signature(package_name, sha1_hex):
    """Generate MSAL signature hash using the Microsoft algorithm"""
    print(f"Package: {package_name}")
    print(f"SHA1: {sha1_hex}")
    
    # Convert SHA1 hex to bytes
    try:
        sha1_bytes = bytes.fromhex(sha1_hex.replace(":", ""))
    except ValueError as e:
        print(f"Error parsing SHA1: {e}")
        return None
    
    # MSAL algorithm: SHA1(package_name + sha1_bytes)
    package_bytes = package_name.encode('utf-8')
    combined = package_bytes + sha1_bytes
    
    print(f"Combined length: {len(combined)} bytes")
    
    # Hash and encode
    hash_result = hashlib.sha1(combined).digest()
    signature_b64 = base64.b64encode(hash_result).decode()
    
    # MSAL uses URL-safe base64 without padding
    signature_url_safe = signature_b64.replace('+', '-').replace('/', '_').rstrip('=')
    
    print(f"Standard Base64: {signature_b64}")
    print(f"URL-Safe (no padding): {signature_url_safe}")
    print(f"Expected in logs: {signature_url_safe}%3D")
    print(f"Redirect URI: msauth://{package_name}/{signature_url_safe}%3D")
    
    return signature_url_safe

def reverse_engineer_sha1(package_name, expected_signature):
    """Try to reverse engineer what SHA1 would produce the expected signature"""
    print(f"\nReverse engineering for expected signature: {expected_signature}")
    
    # The expected signature from logs is ln6yzrD2U58RkmRuLqdNyrvwpw0
    expected_b64 = expected_signature + "="  # Add padding back
    expected_b64_standard = expected_b64.replace('-', '+').replace('_', '/')
    
    try:
        expected_hash = base64.b64decode(expected_b64_standard)
        print(f"Expected hash (hex): {expected_hash.hex().upper()}")
        
        # This would be the result of SHA1(package_name + original_sha1)
        # We can't easily reverse SHA1, but we can confirm our current SHA1 produces the wrong result
        
    except Exception as e:
        print(f"Error decoding expected signature: {e}")

# Test with current setup
package_name = "com.meetingroom.display"
current_sha1 = "967EB2CEB0F6539F1192646E2EA74DCABBF0A70D"  # From debug keystore
expected_signature = "ln6yzrD2U58RkmRuLqdNyrvwpw0"  # From MSAL error

print("=== Current Debug Keystore Test ===")
result = generate_msal_signature(package_name, current_sha1)

print("\n=== Reverse Engineering ===")
reverse_engineer_sha1(package_name, expected_signature)

print(f"\n=== Summary ===")
print(f"MSAL expects: {expected_signature}%3D")
print(f"We generated: {result}%3D" if result else "Generation failed")
print(f"Match: {'YES' if result == expected_signature else 'NO'}")

# Test what SHA1 might produce the expected result
print(f"\n=== Testing Alternative SHA1 Values ===")
# These are common debug keystore SHA1s
common_sha1s = [
    "A40DA80A59D170CAA950CF15C18C454D47A39B26",  # Common debug keystore
    "58E1C4133F7441EC3D2C270270A14802DA47BA0E",  # Another common one
    "967EB2CEB0F6539F1192646E2EA74DCABBF0A70D",  # Current one
]

for test_sha1 in common_sha1s:
    print(f"\nTesting SHA1: {test_sha1}")
    test_result = generate_msal_signature(package_name, test_sha1)
    if test_result == expected_signature:
        print("*** MATCH FOUND! ***")
        break