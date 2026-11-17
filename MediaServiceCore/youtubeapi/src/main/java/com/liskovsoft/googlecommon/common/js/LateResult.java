package com.liskovsoft.googlecommon.common.js;

/**
 * leanview: the outcome of a script that outlived its caller's deadline, see {@link JsContext#evalString(String, LateResult)}.
 */
public interface LateResult {
    /**
     * Called once, on the engine's or on a timer's thread: the string the script ended with (error null), or why there is none
     * (result null): the script error, or a {@link JsTimeoutException} when the hard cap passed before the script ended.
     */
    void onLate(String result, Throwable error);
}
