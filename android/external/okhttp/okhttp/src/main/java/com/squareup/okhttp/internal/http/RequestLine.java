package com.squareup.okhttp.internal.http;

import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.Protocol;
import com.squareup.okhttp.Request;
import java.net.HttpURLConnection;
import java.net.Proxy;

public final class RequestLine {
  private RequestLine() {
  }

  /**
   * Returns the request status line, like "GET / HTTP/1.1". This is exposed
   * to the application by {@link HttpURLConnection#getHeaderFields}, so it
   * needs to be set even if the transport is SPDY.
   */
  static String get(Request request, Proxy.Type proxyType, Protocol protocol) {
    StringBuilder result = new StringBuilder();
    result.append(request.method());
    result.append(' ');

    if (includeAuthorityInRequestLine(request, proxyType)) {
      result.append(request.httpUrl());
    } else {
      result.append(requestPath(request.httpUrl()));
    }

    result.append(' ');
    result.append(version(protocol));
    return result.toString();
  }

  /**
   * Returns true if the request line should contain the full URL with host
   * and port (like "GET http://android.com/foo HTTP/1.1") or only the path
   * (like "GET /foo HTTP/1.1").
   */
  private static boolean includeAuthorityInRequestLine(Request request, Proxy.Type proxyType) {
    return !request.isHttps() && proxyType == Proxy.Type.HTTP;
  }

  /**
   * Returns the path to request, like the '/' in 'GET / HTTP/1.1'. Never empty,
   * even if the request URL is. Includes the query component if it exists.
   */
  public static String requestPath(HttpUrl url) {
    String path = url.encodedPath();
    // ANDROID-BEGIN: http://b/29983827 - Now path can be empty, which is forbidden in relative
    // paths so we must handle it here.
    if (path.isEmpty()) {
      path = "/";
    }
    // ANDROID-END: http://b/29983827

    String query = url.encodedQuery();
    return query != null ? (path + '?' + query) : path;
  }

  public static String version(Protocol protocol) {
    return protocol == Protocol.HTTP_1_0 ? "HTTP/1.0" : "HTTP/1.1";
  }
}
