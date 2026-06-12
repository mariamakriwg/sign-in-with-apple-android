package com.willowtreeapps.signinwithapplebutton.view

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.WebView
import android.webkit.CookieManager
import android.os.Message
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import androidx.fragment.app.DialogFragment
import com.willowtreeapps.signinwithapplebutton.R
import com.willowtreeapps.signinwithapplebutton.SignInWithAppleResult
import com.willowtreeapps.signinwithapplebutton.SignInWithAppleService
import com.willowtreeapps.signinwithapplebutton.view.SignInWithAppleButton.Companion.SIGN_IN_WITH_APPLE_LOG_TAG

@SuppressLint("SetJavaScriptEnabled")
internal class SignInWebViewDialogFragment : DialogFragment() {

    companion object {
        private const val AUTHENTICATION_ATTEMPT_KEY = "authenticationAttempt"
        private const val WEB_VIEW_KEY = "webView"

        fun newInstance(authenticationAttempt: SignInWithAppleService.AuthenticationAttempt): SignInWebViewDialogFragment {
            val fragment = SignInWebViewDialogFragment()
            fragment.arguments = Bundle().apply {
                putParcelable(AUTHENTICATION_ATTEMPT_KEY, authenticationAttempt)
            }
            return fragment
        }
    }

    private lateinit var authenticationAttempt: SignInWithAppleService.AuthenticationAttempt
    private var callback: ((SignInWithAppleResult) -> Unit)? = null

    private val webViewIfCreated: WebView?
        get() = view as? WebView

    fun configure(callback: (SignInWithAppleResult) -> Unit) {
        this.callback = callback
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authenticationAttempt = arguments?.getParcelable(AUTHENTICATION_ATTEMPT_KEY)!!
        setStyle(STYLE_NORMAL, R.style.sign_in_with_apple_button_DialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)

        val webView = WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                javaScriptCanOpenWindowsAutomatically = true

                domStorageEnabled = true 
                setSupportMultipleWindows(true)


                userAgentString = "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36"
            }
        }

        // Adding a JavaScript interface
        val formInterceptorInterface = FormInterceptorInterface(authenticationAttempt.state, ::onCallback)
        webView.addJavascriptInterface(formInterceptorInterface, FormInterceptorInterface.NAME)

        CookieManager.getInstance().apply {
    setAcceptCookie(true)
    setAcceptThirdPartyCookies(webView, true)
}

webView.webChromeClient = object : WebChromeClient() {
    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message
    ): Boolean {
        val newWebView = WebView(requireContext()).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = view.settings.userAgentString
        }
        (resultMsg.obj as WebView.WebViewTransport).webView = newWebView
        resultMsg.sendToTarget()
        return true
    }
}

}
        
        webView.webViewClient =
            UrlInterceptorWebViewClient(authenticationAttempt.redirectUri, FormInterceptorInterface.JS_TO_INJECT)

        if (savedInstanceState != null) {
            savedInstanceState.getBundle(WEB_VIEW_KEY)?.run {
                webView.restoreState(this)
            }
        } else {
            webView.loadUrl(authenticationAttempt.authenticationUri)
        }

        return webView
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBundle(
            WEB_VIEW_KEY,
            Bundle().apply {
                webViewIfCreated?.saveState(this)
            }
        )
    }

    override fun onStart() {
        super.onStart()

        dialog?.window?.setLayout(MATCH_PARENT, MATCH_PARENT)
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        onCallback(SignInWithAppleResult.Cancel)
    }

    // SignInWithAppleCallback

    private fun onCallback(result: SignInWithAppleResult) {
        dialog?.dismiss()
        val callback = callback
        if (callback == null) {
            Log.e(SIGN_IN_WITH_APPLE_LOG_TAG, "Callback is not configured")
            return
        }
        callback(result)
    }
}
