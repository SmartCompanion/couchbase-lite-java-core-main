package com.couchbase.cblite.support;

import android.util.Log;

import com.couchbase.cblite.CBLDatabase;
import com.couchbase.cblite.CBLServer;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

public class CBLRemoteMultipartDownloaderRequest extends CBLRemoteRequest {

    private CBLDatabase db;

    public CBLRemoteMultipartDownloaderRequest(ScheduledExecutorService workExecutor,
                                     HttpClientFactory clientFactory, String method, URL url,
                                     Object body, CBLDatabase db, CBLRemoteRequestCompletionBlock onCompletion,
                                     BasicHttpContext httpContext) {
        super(workExecutor, clientFactory, method, url, body, onCompletion, httpContext);
        this.db = db;
    }

    @Override
    public void run() {

        HttpClient httpClient = clientFactory.getHttpClient();

        preemptivelySetAuthCredentials(httpClient);

        HttpUriRequest request = createConcreteRequest();

        request.addHeader("Accept", "*/*");

        executeRequest(httpClient, request);

    }

    protected void executeRequest(HttpClient httpClient, HttpUriRequest request) {
        Object fullBody = null;
        Throwable error = null;

        Log.d(CBLDatabase.TAG, String.format("executeRequest().  client: %s, thread: %s, request: %s", httpClient, Thread.currentThread(), request));

        try {

            int numCookiesBefore = 0;
            Log.d(CBLDatabase.TAG, "Cookies before");
            BasicCookieStore cookieStore = (BasicCookieStore) httpContext.getAttribute(ClientContext.COOKIE_STORE);
            if (cookieStore != null) {
                List<Cookie> cookies = cookieStore.getCookies();
                for (Cookie cookie : cookies) {
                    Log.d(CBLDatabase.TAG, "Cookie: " + cookie.toString());
                }
                numCookiesBefore = cookies.size();
            }

            HttpResponse response = httpClient.execute(request, httpContext);

            // add in cookies to global store
            DefaultHttpClient defaultHttpClient = (DefaultHttpClient)httpClient;
            CBLHttpClientFactory.INSTANCE.addCookies(defaultHttpClient.getCookieStore().getCookies());

            Log.d(CBLDatabase.TAG, "Cookies after");
            cookieStore = (BasicCookieStore) httpContext.getAttribute(ClientContext.COOKIE_STORE);
            if (cookieStore != null) {

                List<Cookie> cookies = cookieStore.getCookies();
                for (Cookie cookie : cookies) {
                    Log.d(CBLDatabase.TAG, "Cookie: " + cookie.toString());
                }

                if (numCookiesBefore != cookies.size()) {
                    Log.d(CBLDatabase.TAG, "Got new cookies!");
                }
            }


            StatusLine status = response.getStatusLine();
            if (status.getStatusCode() >= 300) {
                Log.e(CBLDatabase.TAG,
                        "Got error " + Integer.toString(status.getStatusCode()));
                Log.e(CBLDatabase.TAG, "Request was for: " + request.toString());
                Log.e(CBLDatabase.TAG,
                        "Status reason: " + status.getReasonPhrase());
                error = new HttpResponseException(status.getStatusCode(),
                        status.getReasonPhrase());
            } else {

                HttpEntity entity = response.getEntity();
                Header contentTypeHeader = entity.getContentType();
                InputStream inputStream = null;

                if (contentTypeHeader.getValue().contains("multipart/related")) {

                    try {
                        CBLMultipartDocumentReader reader = new CBLMultipartDocumentReader(response, db);
                        reader.setContentType(contentTypeHeader.getValue());
                        inputStream = entity.getContent();

                        int bufLen = 1024;
                        byte[] buffer = new byte[bufLen];
                        int numBytesRead = 0;
                        while ( (numBytesRead = inputStream.read(buffer))!= -1 ) {
                            if (numBytesRead != bufLen) {
                                byte[] bufferToAppend = Arrays.copyOfRange(buffer, 0, numBytesRead);
                                reader.appendData(bufferToAppend);
                            }
                            else {
                                reader.appendData(buffer);
                            }
                        }

                        reader.finish();
                        fullBody = reader.getDocumentProperties();

                        respondWithResult(fullBody, error);

                    } finally {
                        try {
                            inputStream.close();
                        } catch (IOException e) {
                            Log.e(CBLDatabase.TAG, "exception closing input stream", e);
                        }
                    }


                }
                else {
                    if (entity != null) {
                        try {
                            inputStream = entity.getContent();
                            fullBody = CBLServer.getObjectMapper().readValue(inputStream,
                                    Object.class);
                            respondWithResult(fullBody, error);
                        } finally {
                            try {
                                inputStream.close();
                            } catch (IOException e) {
                                Log.e(CBLDatabase.TAG, "exception closing input stream", e);
                            }
                        }
                    }

                }
            }
        } catch (ClientProtocolException e) {
            Log.e(CBLDatabase.TAG, "client protocol exception", e);
            error = e;
        } catch (IOException e) {
            Log.e(CBLDatabase.TAG, "io exception", e);
            error = e;
        } catch (IllegalStateException e) {
            Log.e(CBLDatabase.TAG, "illegal state exception", e);
            Log.d(CBLDatabase.TAG, String.format("executeRequest() exception.  client: %s, thread: %s", httpClient, Thread.currentThread()));
            throw e;
        }
    }

}
