package com.couchbase.cblite.support;

import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;

import java.util.List;

public enum CBLHttpClientFactory implements HttpClientFactory {

    INSTANCE;

    private CookieStore cookieStore;

    @Override
    public HttpClient getHttpClient() {

        synchronized (this) {

            // workaround attempt for issue #81
            BasicHttpParams params = new BasicHttpParams();
            SchemeRegistry schemeRegistry = new SchemeRegistry();
            schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
            final SSLSocketFactory sslSocketFactory = SSLSocketFactory.getSocketFactory();
            schemeRegistry.register(new Scheme("https", sslSocketFactory, 443));
            ClientConnectionManager cm = new ThreadSafeClientConnManager(params, schemeRegistry);

            DefaultHttpClient client = new DefaultHttpClient(cm, params);
            client.setCookieStore(cookieStore);
            return client;

        }

    }

    public void addCookies(List<Cookie> cookies) {
        synchronized (this) {
            if (cookieStore == null) {
                cookieStore = new BasicCookieStore();
            }
            for (Cookie cookie : cookies) {
                cookieStore.addCookie(cookie);
            }
        }
    }




}
