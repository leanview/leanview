package com.liskovsoft.googlecommon.common.js;

import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.mylogger.Log;

import java.util.List;

/**
 * leanview: the name stays (call sites and tests use it), the engine behind it is {@link JsEngines}: a new context per call,
 * like before.
 */
public final class V8Runtime {
    private static final String TAG = V8Runtime.class.getSimpleName();
    private static V8Runtime sInstance;

    private V8Runtime() {
    }

    public static V8Runtime instance() {
        if (sInstance == null) {
            sInstance = new V8Runtime();
        }

        return sInstance;
    }

    public static void unhold() {
        sInstance = null;
    }

    @Nullable
    public String evaluate(final String source) {
        try {
            return evaluateSafe(source);
        } catch (JsEngineException e) {
            Log.e(TAG, e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    @Nullable
    public String evaluateWithErrors(final String source) throws JsEngineException {
        return evaluateSafe(source);
    }

    @Nullable
    public String evaluate(final List<String> sources) {
        try {
            return evaluateSafe(sources);
        } catch (JsEngineException e) {
            Log.e(TAG, e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    @Nullable
    public String evaluateWithErrors(final List<String> sources) throws JsEngineException {
        return evaluateSafe(sources);
    }

    /**
     * Thread safe: a new context for every call. leanview: a call that lost the system engine (its process died) runs once more,
     * on QuickJS.
     */
    private String evaluateSafe(final String source) throws JsEngineException {
        try {
            return evaluateOnce(source);
        } catch (JsBackendLostException e) {
            Log.w(TAG, "system JavaScript engine lost, once more on QuickJS: " + e.getMessage());
            return evaluateOnce(source);
        }
    }

    private String evaluateOnce(final String source) throws JsEngineException {
        JsContext context = null;

        try {
            context = JsEngines.create();
            return context.evalString(source);
        } finally {
            if (context != null) {
                context.close();
            }
        }
    }

    /**
     * Thread safe: a new context for every call. leanview: a call that lost the system engine runs once more, on QuickJS.
     */
    private String evaluateSafe(final List<String> sources) throws JsEngineException {
        try {
            return evaluateOnce(sources);
        } catch (JsBackendLostException e) {
            Log.w(TAG, "system JavaScript engine lost, once more on QuickJS: " + e.getMessage());
            return evaluateOnce(sources);
        }
    }

    private String evaluateOnce(final List<String> sources) throws JsEngineException {
        JsContext context = null;
        String result = null;

        try {
            context = JsEngines.create();
            for (String source : sources) {
                try {
                    result = context.evalString(source);
                } catch (JsResultUndefinedException e) {
                    // NOP
                }
            }
        } finally {
            if (context != null) {
                context.close();
            }
        }

        return result;
    }
}
