import base64

KEY = "ExclusiveTVByCodesRahul96"

def decode(encoded_str):
    try:
        decoded_bytes = base64.b64decode(encoded_str)
        decrypted = ""
        for i in range(len(decoded_bytes)):
            decrypted += chr(decoded_bytes[i] ^ ord(KEY[i % len(KEY)]))
        return decrypted
    except Exception as e:
        return str(e)

print("Primary: " + decode("LQwXHAZJRlkCPSIqDCFBBwoefSIHEQlKZCQQFgBMRUYzHTc6NwoqGQFIJwRMKSU8FkQgFAYNBhYaWQE7ISwVLA4ASg=="))
print("Fallback: " + decode("LQwXHAZJRlkCPSIqDCFBBwoefSIHEQlKZCQQFgBMRUYzHTc6NwoqGQFIJwRMPQUIWEIgC0weEB8MFxYxJW0dLBgKCRwzBUc="))
