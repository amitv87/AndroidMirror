package com.boggyb.androidmirror.util;

import android.content.Context;
import android.widget.Toast;

import com.boggyb.androidmirror.BuildConfig;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class Misc {
  public static boolean isP2PEanbled(){
    return "p2p".equals(BuildConfig.FLAVOR);
  }

  public static Map<String, List<String>> getQueryMap(URL url) throws UnsupportedEncodingException {
    final Map<String, List<String>> query_pairs = new LinkedHashMap<>();
    String query = url.getQuery();
    if (query == null) query = "";
    final String[] pairs = query.split("&");
    for (String pair : pairs) {
      final int idx = pair.indexOf("=");
      final String key = idx > 0 ? URLDecoder.decode(pair.substring(0, idx), "UTF-8") : pair;
      if (!query_pairs.containsKey(key)) {
        query_pairs.put(key, new LinkedList<>());
      }
      final String value = idx > 0 && pair.length() > idx + 1 ? URLDecoder.decode(pair.substring(idx + 1), "UTF-8") : "";
      query_pairs.get(key).add(value);
    }
    return query_pairs;
  }

  private static Toast toast;
  public static void showToast(String txt, Context ctx){
    try {if (toast != null) toast.cancel();}
    catch (Exception ignored){}
    toast = Toast.makeText(ctx, txt, Toast.LENGTH_SHORT);
    toast.show();
  }
}
