import sys
import base64
import hashlib
from Crypto.Cipher import AES
from Crypto.Util.Padding import pad
import os

def get_native_key():
    # As constructed in native-lib.cpp: "-CodesRahul96-" + "-ExclusiveTV-" + "-2026-"
    return "-CodesRahul96--ExclusiveTV--2026-"

def encrypt_url(url, key):
    # Professional Key Derivation (Matches Kotlin SHA-256 derivation)
    key_bytes = hashlib.sha256(key.encode('utf-8')).digest()
    
    # Generate random 16-byte IV
    iv = os.urandom(16)
    
    # Init AES/CBC/PKCS5Padding cipher
    cipher = AES.new(key_bytes, AES.MODE_CBC, iv)
    
    # Pad and encrypt
    encrypted_bytes = cipher.encrypt(pad(url.encode('utf-8'), AES.block_size))
    
    # Combine IV + Encrypted Data (matches Kotlin decryption logic)
    combined = iv + encrypted_bytes
    
    # Encode as Base64 for Firebase
    return base64.b64encode(combined).decode('utf-8')

if __name__ == "__main__":
    if len(sys.argv) > 1:
        # Command line arg encryptor
        target_url = sys.argv[1]
        native_key = get_native_key()
        print(f"\nObfuscating Stream URL: {target_url}\n")
        print("Encrypted AES-256 Payload:")
        print(encrypt_url(target_url, native_key))
        print("\n(You can copy and paste this string directly into your M3U or JSON playlist!)")
    else:
        # Default Firebase Generation
        native_key = get_native_key()
        
        standard_url = "https://rebroadcast.indevs.in/freeTV"
        premium_url = "https://exclusivetvapi.indevs.in/api/channels"
        waves_fallback = "https://raw.githubusercontent.com/alpha4528/m3u/refs/heads/main/waves.m3u"
        
        print("=" * 60)
        print("FIREBASE REMOTE CONFIG ENCRYPTED URLS")
        print("=" * 60)
        print("Key used for encryption:", native_key)
        print("-" * 60)
        
        print("\n1. standard_api_url (FreeTV):")
        print(encrypt_url(standard_url, native_key))
        
        print("\n2. premium_api_url (ExclusiveTV API):")
        print(encrypt_url(premium_url, native_key))
        
        print("\n3. waves_api_url (Fallback - if needed):")
        print(encrypt_url(waves_fallback, native_key))
        
        print("\n" + "=" * 60)
        print("Copy these Base64 strings directly into your Firebase Console!")
        
        # Save to file
        with open("firebase_encrypted_urls.txt", "w") as f:
            f.write("standard_api_url:\n" + encrypt_url(standard_url, native_key) + "\n\n")
            f.write("premium_api_url:\n" + encrypt_url(premium_url, native_key) + "\n\n")
            f.write("waves_api_url:\n" + encrypt_url(waves_fallback, native_key) + "\n")
            
        print("Saved output to 'firebase_encrypted_urls.txt'")
        print("\nTIP: To encrypt a single stream URL, run: python encrypt_urls_for_firebase.py \"http://stream.m3u8\"")
