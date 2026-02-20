#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_codesrahul_exclusivetv_SecretManager_getNativeKey(JNIEnv *env,
                                                           jobject /* this */) {

  // Obfuscated Key Construction
  std::string p1 = "-CodesRahul96-";
  std::string p2 = "-ExclusiveTV-";
  std::string p3 = "-2026-";

  return env->NewStringUTF((p1 + p2 + p3).c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_codesrahul_exclusivetv_SecretManager_getMaintenanceKey(
    JNIEnv *env, jobject /* this */) {

  // Obfuscated "maintenance_mode"
  std::string s1 = "main";
  std::string s2 = "tenan";
  std::string s3 = "ce_mo";
  std::string s4 = "de";
  return env->NewStringUTF((s1 + s2 + s3 + s4).c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_codesrahul_exclusivetv_SecretManager_getNativeHmacKey(
    JNIEnv *env, jobject /* this */) {

  // Obfuscated HMAC Key
  // Original: "Excl2026S3cr3tT0k3n_!"
  std::string k1 = "Excl";
  std::string k2 = "2026";
  std::string k3 = "S3cr";
  std::string k4 = "3t";
  std::string k5 = "T0k3n";
  std::string k6 = "_!";

  return env->NewStringUTF((k1 + k2 + k3 + k4 + k5 + k6).c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_codesrahul_exclusivetv_SecretManager_verifyNativeIntegrity(
    JNIEnv *env, jobject /* this */, jobject context) {

  jclass contextClass = env->GetObjectClass(context);
  jmethodID getPackageNameMid =
      env->GetMethodID(contextClass, "getPackageName", "()Ljava/lang/String;");
  jstring packageName =
      (jstring)env->CallObjectMethod(context, getPackageNameMid);

  jmethodID getPackageManagerMid =
      env->GetMethodID(contextClass, "getPackageManager",
                       "()Landroid/content/pm/PackageManager;");
  jobject packageManager = env->CallObjectMethod(context, getPackageManagerMid);

  jclass packageManagerClass = env->GetObjectClass(packageManager);
  jmethodID getPackageInfoMid =
      env->GetMethodID(packageManagerClass, "getPackageInfo",
                       "(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;");

  // 64 = GET_SIGNATURES
  jobject packageInfo =
      env->CallObjectMethod(packageManager, getPackageInfoMid, packageName, 64);

  jclass packageInfoClass = env->GetObjectClass(packageInfo);
  jfieldID signaturesFid = env->GetFieldID(packageInfoClass, "signatures",
                                           "[Landroid/content/pm/Signature;");
  jobjectArray signatures =
      (jobjectArray)env->GetObjectField(packageInfo, signaturesFid);

  jobject signature = env->GetObjectArrayElement(signatures, 0);
  jclass signatureClass = env->GetObjectClass(signature);
  jmethodID toByteArrayMid =
      env->GetMethodID(signatureClass, "toByteArray", "()[B");
  jbyteArray signatureBytes =
      (jbyteArray)env->CallObjectMethod(signature, toByteArrayMid);

  // Placeholder for expected signature hash check
  // In a real scenario, we'd hash this and compare with a hardcoded hash.
  // For now, we'll return false (not tampered) to avoid blocking the user
  // before we know their signature.

  return JNI_FALSE;
}
