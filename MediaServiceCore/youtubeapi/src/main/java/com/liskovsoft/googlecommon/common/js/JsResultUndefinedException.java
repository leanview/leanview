package com.liskovsoft.googlecommon.common.js;

/**
 * leanview: the script ran but its result is not a string (the engine ran the script but its result has no string form).
 */
public class JsResultUndefinedException extends JsEngineException {
    public JsResultUndefinedException(String message) {
        super(message);
    }
}
