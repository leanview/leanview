package com.liskovsoft.googlecommon.common.js;

/**
 * leanview: no context for a solve now: the script of a context discarded after a timeout is still running and still holds its
 * memory. Nothing failed: the caller tries again later, it does not count this against the player.
 */
public class JsEngineBusyException extends JsEngineException {
    public JsEngineBusyException(String message) {
        super(message);
    }
}
