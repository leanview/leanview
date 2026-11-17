package com.liskovsoft.googlecommon.common.js;

/**
 * leanview: a script error from the JavaScript engine. The message is the plain error text, e.g. "x is not defined",
 * the same for every engine (callers match on it).
 */
public class JsEngineException extends RuntimeException {
    public JsEngineException(String message) {
        super(message);
    }

    public JsEngineException(String message, Throwable cause) {
        super(message, cause);
    }
}
