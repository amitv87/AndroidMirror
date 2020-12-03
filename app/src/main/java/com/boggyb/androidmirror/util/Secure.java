package com.boggyb.androidmirror.util;

import android.util.Log;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class Secure {
  private static final String TAG = Secure.class.getCanonicalName();
  private static final int kAuthWindowMs = 250;
  private static final String kSymbols = "abcdefgjklmnprstuvwxyzABCDEFGJKLMNPRSTUVWXYZ0123456789";

  public enum Algorithm{
    AES,
  };

  public static String getRandomString(int length){
    char[] buf = new char[length];
    SecureRandom random = new SecureRandom();
    for (int idx = 0; idx < buf.length; ++idx)
      buf[idx] = kSymbols.charAt(random.nextInt(kSymbols.length()));
    return new String(buf);
  }

  public static byte[] generateKey(Algorithm algorithm, int length) throws NoSuchAlgorithmException {
    KeyGenerator keyGen = KeyGenerator.getInstance(algorithm.toString());
    keyGen.init(length); // for example
    return keyGen.generateKey().getEncoded();
  }

  public static byte[] crypt(Algorithm algorithm, int crypt, byte[] key, byte[] data) throws Exception {
    SecretKey secretKey = new SecretKeySpec(key, algorithm.toString());
    Cipher cipher = Cipher.getInstance(secretKey.getAlgorithm());
    cipher.init(crypt, secretKey);
    return cipher.doFinal(data);
  }

  public static String encryptAES(String key, String string) throws Exception {
    return Base64.getEncoder().encodeToString(crypt(Algorithm.AES, Cipher.ENCRYPT_MODE, Base64.getDecoder().decode(key), string.getBytes()));
  }

  public static String decryptAES(String key, String base64) throws Exception {
    return new String(crypt(Algorithm.AES, Cipher.DECRYPT_MODE, Base64.getDecoder().decode(key), Base64.getDecoder().decode(base64)));
  }

  public static byte[] encryptAES(byte[] key, byte[] data) throws Exception {
    return crypt(Algorithm.AES, Cipher.ENCRYPT_MODE, key, data);
  }

  public static byte[] decryptAES(byte[] key, byte[] data) throws Exception {
    return crypt(Algorithm.AES, Cipher.DECRYPT_MODE, key, data);
  }

  public static boolean doWSAuth(String key, Algorithm algorithm, String startsWith, String message) throws Exception {
    message = Secure.decryptAES(key, message);
    Log.d(TAG, "doAuth message: " + message);
    String[] arr = message.split("_");
    long diff = Math.abs(System.currentTimeMillis() - Long.parseLong(arr[1]));
    Log.d(TAG, "doAuth diff: " + diff);
    return arr[0].startsWith(startsWith) && diff < kAuthWindowMs;
  }
}
