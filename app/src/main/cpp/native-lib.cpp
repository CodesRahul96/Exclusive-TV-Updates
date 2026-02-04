#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_codesrahul_exclusivetv_SecretManager_getNativeKey(
        JNIEnv* env,
        jobject /* this */) {
    
    // Obfuscated Key Construction
    // Key: "your-custom-secret-key-2026"
    std::string part1 = "-CodesRahul96-";
    std::string part2 = "-ExclusiveTV-";
    std::string part3 = "-2026-";
    
    std::string key = part1 + part2 + part3;
    
    return env->NewStringUTF(key.c_str());
}
