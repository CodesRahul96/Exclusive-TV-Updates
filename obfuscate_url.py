import sys

def chunk_string(string, length):
    return [string[0+i:length+i] for i in range(0, len(string), length)]

def generate_cpp_obfuscation(url, var_prefix="u"):
    chunks = chunk_string(url, 7)  # Split into chunks of 7 characters
    
    print("\n  // Obfuscated String: \"{}\"".format(url))
    
    for i, chunk in enumerate(chunks):
        # Escape quotes and backslashes for C++ strings
        safe_chunk = chunk.replace('\\', '\\\\').replace('"', '\\"')
        print(f"  std::string {var_prefix}{i+1} = \"{safe_chunk}\";")
    
    var_additions = " + ".join([f"{var_prefix}{i+1}" for i in range(len(chunks))])
    print(f"\n  return env->NewStringUTF(({var_additions}).c_str());\n")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python obfuscate_url.py \"<your_url_here>\" [variable_prefix]")
        print("Example: python obfuscate_url.py \"https://mysecretapi.com\" p")
        sys.exit(1)
        
    target_url = sys.argv[1]
    prefix = sys.argv[2] if len(sys.argv) > 2 else "u"
    
    generate_cpp_obfuscation(target_url, prefix)
