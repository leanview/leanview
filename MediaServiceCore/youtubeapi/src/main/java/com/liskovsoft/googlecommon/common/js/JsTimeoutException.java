package com.liskovsoft.googlecommon.common.js;

/**
 * leanview: the script did not end inside the deadline. The context was discarded: its thread may still be running the script,
 * the engine has no way to stop it.
 */
public class JsTimeoutException extends JsEngineException {
    public JsTimeoutException(String message) {
        super(message);
    }
}
