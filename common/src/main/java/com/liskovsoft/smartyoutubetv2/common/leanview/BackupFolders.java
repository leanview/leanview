package com.liskovsoft.smartyoutubetv2.common.leanview;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.R;

import io.reactivex.Observable;
import io.reactivex.functions.Function;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * leanview: the name of the backup folder (Documents and Google Drive). Upstream calls it SmartTubeBackup; Leanview (flavor
 * leanview) writes to LeanviewBackup (R.string.leanview_backup_folder) but a restore also looks into an existing
 * SmartTubeBackup, so the settings of the old app can be brought over. Other flavors: the name does not change.
 */
public final class BackupFolders {
    /** The folder upstream's app (and the old build of this app) wrote to. */
    public static final String LEGACY = "SmartTubeBackup";

    private BackupFolders() {
    }

    /** Where new backups go. */
    public static String primary(Context context) {
        return context.getString(R.string.leanview_backup_folder);
    }

    /** The folders a restore looks into: the primary one first, then the legacy one when it is a different name. */
    public static List<String> restoreRoots(String primary) {
        List<String> roots = new ArrayList<>();
        roots.add(primary);
        if (!LEGACY.equals(primary)) {
            roots.add(LEGACY);
        }
        return roots;
    }

    /** "root/name" for every backup name found under a root (null names are dropped). */
    public static List<String> entries(String root, List<String> names) {
        if (names == null) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (String name : names) {
            if (name != null) {
                result.add(root + "/" + name);
            }
        }
        return result;
    }

    /** What the restore list shows: the bare name for the primary folder, the whole path for another one. */
    public static String displayName(String primary, String entry) {
        String prefix = primary + "/";
        return entry.startsWith(prefix) ? entry.substring(prefix.length()) : entry;
    }

    /** What listing one Drive folder gave: its entries, or the error (a folder that does not exist is an error of the Drive service). */
    public static final class Outcome {
        final List<String> entries;
        final Throwable error;

        public Outcome(List<String> entries, Throwable error) {
            this.entries = entries;
            this.error = error;
        }
    }

    /**
     * The backups to offer, from the outcomes of the primary folder and then the old one (same order as {@link #restoreRoots}).
     * A folder that failed is skipped as long as another one answered: on first use LeanviewBackup does not exist yet (an error
     * of the Drive service) and the old SmartTubeBackup must still be offered. When every folder failed the error is thrown,
     * an "AuthError" one first so the caller can sign in again.
     */
    public static List<String> combine(List<Outcome> outcomes) throws Exception {
        List<String> all = new ArrayList<>();
        boolean anyAnswered = false;
        Throwable failure = null;
        for (Outcome outcome : outcomes) {
            if (outcome.error == null) {
                anyAnswered = true;
                all.addAll(outcome.entries);
            } else if (failure == null || isAuthError(outcome.error) && !isAuthError(failure)) {
                failure = outcome.error;
            }
        }
        if (!anyAnswered && failure != null) {
            if (failure instanceof Exception) {
                throw (Exception) failure;
            }
            throw new IllegalStateException(failure);
        }
        return all;
    }

    private static boolean isAuthError(Throwable error) {
        String message = error.getMessage();
        return message != null && message.startsWith("AuthError");
    }

    /** Lists the backups of every restore folder with {@code lister} and combines them (see {@link #combine}). */
    public static Observable<List<String>> listBackups(String primary, Function<String, Observable<List<String>>> lister) {
        List<Observable<Outcome>> sources = new ArrayList<>();
        for (final String root : restoreRoots(primary)) {
            sources.add(Observable.defer(() -> lister.apply(root))
                    .map(names -> new Outcome(entries(root, names), null))
                    .onErrorReturn(error -> new Outcome(null, error)));
        }
        return Observable.zip(sources, results -> {
            List<Outcome> outcomes = new ArrayList<>();
            for (Object result : results) {
                outcomes.add((Outcome) result);
            }
            return combine(outcomes);
        });
    }

    /** Both lists in one, the primary folder's entries first. */
    public static List<String> merge(List<String> primaryEntries, List<String> legacyEntries) {
        List<String> all = new ArrayList<>(primaryEntries);
        all.addAll(legacyEntries);
        return all;
    }
}
