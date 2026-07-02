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
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
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

    // Direct reference to the WebView, since the fragment's root view is now
    // a FrameLayout wrapper (needed for inset/padding handling), not the WebView itself.
    private var webViewIfCreated: WebView? = null

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

        val root = FrameLayout(requireContext())

        val webView = WebView(requireContext()).apply {
            settings.apply {
                javaScriptEnabled = true
                javaScriptCanOpenWindowsAutomatically = true
                domStorageEnabled = true
                setSupportMultipleWindows(true)

                val defaultUa = WebSettings.getDefaultUserAgent(requireContext())
                val finalUa = defaultUa
                    .replace("; wv", "")
                    .replace(" Version/4.0", "")
                userAgentString = finalUa
            }
        }
        webViewIfCreated = webView

        root.addView(
            webView,
            FrameLayout.LayoutParams(
                MATCH_PARENT,
                MATCH_PARENT
            )
        )

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        // Adding a JavaScript interface
        val formInterceptorInterface = FormInterceptorInterface(authenticationAttempt.state, ::onCallback)
        webView.addJavascriptInterface(formInterceptorInterface, FormInterceptorInterface.NAME)

        webView.webViewClient =
            UrlInterceptorWebViewClient(authenticationAttempt.redirectUri, FormInterceptorInterface.JS_TO_INJECT)

        if (savedInstanceState != null) {
            savedInstanceState.getBundle(WEB_VIEW_KEY)?.run {
                webView.restoreState(this)
            }
        } else {
            webView.loadUrl(authenticationAttempt.authenticationUri)
        }

       ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
    view.setPadding(
        insets.systemWindowInsetLeft,
        insets.systemWindowInsetTop,
        insets.systemWindowInsetRight,
        insets.systemWindowInsetBottom
    )
    insets
}

        ViewCompat.requestApplyInsets(root)

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        webViewIfCreated = null
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

        dialog?.window?.let { window ->
            window.setLayout(MATCH_PARENT, MATCH_PARENT)
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        }
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
