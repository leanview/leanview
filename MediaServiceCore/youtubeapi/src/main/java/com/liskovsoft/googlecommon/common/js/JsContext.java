package com.liskovsoft.googlecommon.common.js;

/**
 * leanview: one JavaScript context of the engine behind {@link JsEngines}. Created, used and closed on one thread.
 */
public interface JsContext extends AutoCloseable {
    /**
     * Runs the script and returns its result, which must be a string.
     *
     * @throws JsResultUndefinedException when the script ends in a value that is not a string
     * @throws JsEngineException on a script error (syntax or run time)
     */
    String evalString(String script);

    /**
     * Like {@link #evalString(String)}, but a script that outlives the deadline is not thrown away: the call still fails with
     * {@link JsTimeoutException} and the context is discarded, and when the script ends, its outcome goes to {@code late} (once,
     * unless the hard cap of the engine passes first, then {@code late} gets a {@link JsTimeoutException}).
     */
    String evalString(String script, LateResult late);

    /**
     * Like {@link #evalString(String, LateResult)}; {@code afterRelease} runs on the engine's thread once the discarded script
     * ended AND its context no longer counts as running (its engine destroyed): a solve started from it is not refused.
     */
    String evalString(String script, LateResult late, Runnable afterRelease);

    /** The engine this context runs on, for the logs ("QUICKJS" or "SANDBOX"; empty when not known). */
    default String engineName() {
        return "";
    }

    /**
     * Runs the script and drops its result.
     *
     * @throws JsEngineException on a script error (syntax or run time)
     */
    void evalVoid(String script);

    @Override
    void close();
}
