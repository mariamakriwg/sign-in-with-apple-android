package com.willowtreeapps.signinwithapplebutton.view

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi

/**
 * Custom web client that waits for [urlToIntercept] to be triggered and, when that happens, injects
 * [javascriptToInject] into the web view.
 */
internal class UrlInterceptorWebViewClient(
    private val urlToIntercept: String,
    private val javascriptToInject: String
) : WebViewClient() {

    /**
     * We use this callback if our backend returns a successful response (2xx). If our backend returns
     * a 404 response this callback will not be fired so we must relay on url intercepting techniques.
     */
    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        if (url.contains(urlToIntercept)) {
            /*
             * At this point, we already got a response from apple with the "code" that we want to fetch to authenticate
             * the user and the "state" that we set in the initial request.
             * Still, that information is encoded as a "form_data" from the POST request that we sent.
             * As within the native code we can't access that POST's form_data, we inject a piece of Javascript code
             * that'll access the document's form_data, get the info and process it, so that it's available in "our"
             * code.
             */
            injectJavascript(view)
        } else {
            super.onPageStarted(view, url, favicon)
        }
    }

    // if Android version < Lollipop
    override fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            if (url?.contains(urlToIntercept) == true && view != null) {
                Handler(Looper.getMainLooper()).post { injectJavascript(view) }
            }
        }
        return super.shouldInterceptRequest(view, url)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        if (request.url.toString().contains(urlToIntercept)) {
            Handler(Looper.getMainLooper()).post { injectJavascript(view) }
        }
        return super.shouldInterceptRequest(view, request)
    }

    private fun injectJavascript(webView: WebView) {
        webView.loadUrl("javascript: (function() { ${javascriptToInject} } ) ()")
    }
}