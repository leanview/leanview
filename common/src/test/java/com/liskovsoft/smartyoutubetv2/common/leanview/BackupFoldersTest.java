package com.liskovsoft.smartyoutubetv2.common.leanview;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import java.util.Collections;

// leanview: backups go to the flavor's folder; a restore also finds the old SmartTubeBackup.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class BackupFoldersTest {
    @Test
    public void otherFlavorsKeepUpstreamsFolderName() {
        assertEquals("SmartTubeBackup", BackupFolders.primary(RuntimeEnvironment.getApplication()));
    }

    @Test
    public void leanviewRestoresFromItsOwnFolderAndFromTheOldOne() {
        assertEquals(Arrays.asList("LeanviewBackup", "SmartTubeBackup"), BackupFolders.restoreRoots("LeanviewBackup"));
    }

    @Test
    public void aFlavorThatKeepsTheOldNameLooksInOneFolder() {
        assertEquals(Collections.singletonList("SmartTubeBackup"), BackupFolders.restoreRoots("SmartTubeBackup"));
    }

    @Test
    public void entriesCarryTheirFolderAndDropNulls() {
        assertEquals(Arrays.asList("SmartTubeBackup/org.smarttube.oldbuild", "SmartTubeBackup/app.x"),
                BackupFolders.entries("SmartTubeBackup", Arrays.asList("org.smarttube.oldbuild", null, "app.x")));
        assertEquals(Collections.emptyList(), BackupFolders.entries("SmartTubeBackup", null));
    }

    @Test
    public void theListShowsBareNamesForTheNewFolderAndPathsForTheOldOne() {
        assertEquals("app.leanview.tv", BackupFolders.displayName("LeanviewBackup", "LeanviewBackup/app.leanview.tv"));
        assertEquals("SmartTubeBackup/org.smarttube.oldbuild", BackupFolders.displayName("LeanviewBackup", "SmartTubeBackup/org.smarttube.oldbuild"));
    }

    @Test
    public void bothFoldersAreOffered_newOnesFirst() {
        assertEquals(Arrays.asList("LeanviewBackup/a", "SmartTubeBackup/b"),
                BackupFolders.merge(Arrays.asList("LeanviewBackup/a"), Arrays.asList("SmartTubeBackup/b")));
    }

    // --- Drive restore (review N-1): a fake Drive with the folders it has; a folder it lacks is an ERROR, like DriveService's fromNullable ---

    private static io.reactivex.functions.Function<String, io.reactivex.Observable<List<String>>> drive(
            java.util.Map<String, List<String>> folders) {
        return root -> folders.containsKey(root)
                ? io.reactivex.Observable.just(folders.get(root))
                : io.reactivex.Observable.<List<String>>error(new IllegalStateException("fromNullable result is null"));
    }

    private static java.util.Map<String, List<String>> folders(Object... pairs) {
        java.util.Map<String, List<String>> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], (List<String>) pairs[i + 1]);
        }
        return map;
    }

    @Test
    public void onFirstUseOnlyTheOldFolderExistsAndItsBackupsAreOffered() {
        List<String> shown = BackupFolders.listBackups("LeanviewBackup",
                drive(folders("SmartTubeBackup", Arrays.asList("org.smarttube.oldbuild")))).blockingFirst();
        assertEquals(Arrays.asList("SmartTubeBackup/org.smarttube.oldbuild"), shown);
    }

    @Test
    public void bothFoldersAreOfferedWhenBothExist() {
        List<String> shown = BackupFolders.listBackups("LeanviewBackup", drive(folders(
                "LeanviewBackup", Arrays.asList("app.leanview.tv"), "SmartTubeBackup", Arrays.asList("org.smarttube.oldbuild")))).blockingFirst();
        assertEquals(Arrays.asList("LeanviewBackup/app.leanview.tv", "SmartTubeBackup/org.smarttube.oldbuild"), shown);
    }

    @Test
    public void theNewFolderAloneIsEnoughWhenThereIsNoOldOne() {
        List<String> shown = BackupFolders.listBackups("LeanviewBackup",
                drive(folders("LeanviewBackup", Arrays.asList("app.leanview.tv")))).blockingFirst();
        assertEquals(Arrays.asList("LeanviewBackup/app.leanview.tv"), shown);
    }

    @Test
    public void whenNeitherFolderExistsTheErrorIsReported() {
        io.reactivex.observers.TestObserver<List<String>> observer =
                BackupFolders.listBackups("LeanviewBackup", drive(folders())).test();
        observer.assertError(IllegalStateException.class);
    }

    @Test
    public void anAuthErrorIsKeptSoTheCallerSignsInAgain() {
        io.reactivex.functions.Function<String, io.reactivex.Observable<List<String>>> expired = root ->
                io.reactivex.Observable.<List<String>>error(new IllegalStateException(
                        root.equals("LeanviewBackup") ? "fromNullable result is null" : "AuthError: invalid_grant"));
        io.reactivex.observers.TestObserver<List<String>> observer = BackupFolders.listBackups("LeanviewBackup", expired).test();
        observer.assertError(e -> e.getMessage().startsWith("AuthError"));
    }

    @Test
    public void aFlavorWithOneFolderStillFailsWhenThatFolderFails() {
        BackupFolders.listBackups("SmartTubeBackup", drive(folders())).test().assertError(IllegalStateException.class);
    }

    @Test
    public void anEmptyAnswerFromEveryFolderIsAnEmptyList() {
        List<String> shown = BackupFolders.listBackups("LeanviewBackup", drive(folders(
                "LeanviewBackup", new ArrayList<String>(), "SmartTubeBackup", new ArrayList<String>()))).blockingFirst();
        assertEquals(Collections.emptyList(), shown);
    }

    @Test
    public void noOtherSourceFileHardCodesTheFolderName() throws IOException {
        File main = new File("src/main");
        if (!main.isDirectory()) {
            main = new File("common/src/main");
        }
        List<String> hits = new ArrayList<>();
        try (Stream<Path> files = Files.walk(main.toPath())) {
            files.filter(p -> p.toString().endsWith(".java") && !p.toString().endsWith("BackupFolders.java")).forEach(p -> {
                try {
                    String text = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                    if (text.contains("\"SmartTubeBackup")) {
                        hits.add(p.getFileName().toString());
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        assertEquals("a literal folder name is back: " + hits, Collections.emptyList(), hits);
    }
}
