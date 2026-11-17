package com.liskovsoft.googlecommon.common.js;

/**
 * leanview: the system JavaScript engine (the JavaScriptSandbox process) went away during a call, or stopped offering what the
 * app needs. Nothing is wrong with the script: the call is made once more on QuickJS, and the system engine is not used again
 * until the app restarts (see JsEngines). Not counted against the player.
 */
public class JsBackendLostException extends JsEngineException {
    public JsBackendLostException(String message) {
        super(message);
    }

    public JsBackendLostException(String message, Throwable cause) {
        super(message, cause);
    }
}
