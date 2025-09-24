#!/usr/bin/env python3
import subprocess
import hashlib
import base64
import sys

def get_apk_signature():
    """Get the actual signature from the built APK"""
    try:
        # Get the APK path
        apk_path = "/Users/eamon/Projects/ignis-android-calendar/app/build/outputs/apk/debug/app-debug.apk"
        
        # Extract certificate from APK
        result = subprocess.run([
            "keytool", "-printcert", "-jarfile", apk_path
        ], capture_output=True, text=True)
        
        if result.returncode != 0:
            print(f"Error getting certificate: {result.stderr}")
            return None
            
        # Extract SHA1 from output
        for line in result.stdout.split('\n'):
            if 'SHA1:' in line:
                sha1 = line.split('SHA1:')[1].strip().replace(':', '')
                print(f"APK SHA1: {sha1}")
                return sha1
                
        return None
        
    except Exception as e:
        print(f"Error: {e}")
        return None

def generate_redirect_uri(package_name, sha1_hex):
    """Generate the redirect URI that MSAL would create"""
    
    # Test multiple methods that MSAL might use
    methods = []
    
    # Method 1: Direct SHA1 as base64
    sha1_bytes = bytes.fromhex(sha1_hex)
    sig1 = base64.b64encode(sha1_bytes).decode().rstrip('=')
    methods.append(("Direct SHA1 as base64", sig1))
    
    # Method 2: SHA1(package + sha1_bytes)
    package_bytes = package_name.encode('utf-8')
    combined = package_bytes + sha1_bytes
    hash_result = hashlib.sha1(combined).digest()
    sig2 = base64.b64encode(hash_result).decode().rstrip('=').replace('+', '-').replace('/', '_')
    methods.append(("MSAL standard method", sig2))
    
    # Method 3: What we had originally
    original_sig = "ln6yzrD2U58RkmRuLqdNyrvwpw0"
    methods.append(("Original configuration", original_sig))
    
    print(f"\nPossible redirect URIs for {package_name}:")
    print(f"SHA1: {sha1_hex}")
    print("=" * 60)
    
    for method_name, signature in methods:
        uri_encoded = f"msauth://{package_name}/{signature}%3D"
        uri_unencoded = f"msauth://{package_name}/{signature}="
        print(f"\n{method_name}:")
        print(f"  Encoded:   {uri_encoded}")
        print(f"  Unencoded: {uri_unencoded}")
    
    return methods

def main():
    package_name = "com.meetingroom.display"
    
    # First try to get SHA1 from the actual APK
    apk_sha1 = get_apk_signature()
    
    if apk_sha1:
        print("Using SHA1 from actual APK:")
        generate_redirect_uri(package_name, apk_sha1)
    else:
        print("Could not get SHA1 from APK, using debug keystore SHA1:")
        debug_sha1 = "967EB2CEB0F6539F1192646E2EA74DCABBF0A70D"
        generate_redirect_uri(package_name, debug_sha1)
    
    print(f"\n" + "=" * 60)
    print("RECOMMENDATIONS:")
    print("1. Try each of these redirect URIs in Azure Portal")
    print("2. The one that works without errors is the correct one")
    print("3. Update app configuration to match the working Azure URI")

if __name__ == "__main__":
    main()