/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.assay.transform;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.TransactionAuditProvider;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.audit.provider.FileSystemAuditProvider;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.query.AbstractQueryUpdateService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.vfs.FileLike;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The outcome of running an assay's transform scripts, before any of it is written to the database.
 * {@link DataTransformService#recordTransform} writes it against the saved run; closing it deletes the scripts'
 * working directories and adds unrecorded file audit events for files still on disk, so close it only after the transaction that
 * recorded it has committed or rolled back.
 */
public class PreparedTransform implements AutoCloseable
{
    private static final Logger LOG = LogHelper.getLogger(PreparedTransform.class, "Assay transform script working directories");

    private final @Nullable DataExchangeHandler _handler;
    private final @NotNull TransformResult _result;
    private final @NotNull List<TransformScriptOutput> _scriptOutputs;
    private final @NotNull List<FileLike> _scriptDirs;
    private boolean _recorded;
    private @Nullable Map<ExpData, String> _runDataInputs;
    private final @NotNull List<PendingFileAudit> _fileAudits = new ArrayList<>();
    private @Nullable User _fileAuditUser;

    PreparedTransform(@Nullable DataExchangeHandler handler, @NotNull TransformResult result, @NotNull List<TransformScriptOutput> scriptOutputs, @NotNull List<FileLike> scriptDirs)
    {
        _handler = handler;
        _result = result;
        _scriptOutputs = scriptOutputs;
        _scriptDirs = scriptDirs;
    }

    static PreparedTransform empty()
    {
        return new PreparedTransform(null, DefaultTransformResult.createEmptyResult(), List.of(), List.of());
    }

    public @NotNull TransformResult getResult()
    {
        return _result;
    }

    @Nullable DataExchangeHandler getHandler()
    {
        return _handler;
    }

    @NotNull List<TransformScriptOutput> getScriptOutputs()
    {
        return _scriptOutputs;
    }

    /** Absolute paths of the files the scripts wrote, which recording attaches to the run as the scripts' outputs. */
    public @NotNull Set<Path> getOutputFiles()
    {
        Set<Path> files = new HashSet<>();
        for (TransformScriptOutput scriptOutput : _scriptOutputs)
        {
            for (var relatedFile : scriptOutput.getRelatedOutputFiles())
                files.add(normalize(relatedFile.second));
            for (ExpData data : scriptOutput.getTransformedDatas())
            {
                File file = data.getFile();
                if (file != null)
                    files.add(normalize(file));
            }
        }
        return files;
    }

    /** The run's data inputs, when collected before the scripts ran; null otherwise. */
    public @Nullable Map<ExpData, String> getRunDataInputs()
    {
        return _runDataInputs;
    }

    public void setRunDataInputs(@NotNull Map<ExpData, String> runDataInputs)
    {
        _runDataInputs = runDataInputs;
    }

    /** The audit event of a file saved while preparing, held until the import transaction opens. */
    public record PendingFileAudit(@NotNull FileSystemAuditProvider.FileSystemAuditEvent event, @NotNull FileLike file) {}

    /** Holds the audits of files saved while preparing, to record against the import transaction once it opens. */
    public void holdFileAudits(@Nullable User user, @NotNull List<PendingFileAudit> audits)
    {
        _fileAuditUser = user;
        _fileAudits.addAll(audits);
    }

    /** Records the held audits in the import transaction; a rollback discards them too, so they stay held for close() until it commits. */
    public void recordFileAuditEvents(@NotNull DbScope.Transaction transaction)
    {
        List<PendingFileAudit> audits = List.copyOf(_fileAudits);
        if (audits.isEmpty())
            return;
        List<FileSystemAuditProvider.FileSystemAuditEvent> events = audits.stream().map(PendingFileAudit::event).toList();
        TransactionAuditProvider.TransactionAuditEvent transactionEvent = transaction.getAuditEvent();
        if (transactionEvent != null)
            events.forEach(event -> event.setTransactionEvent(transactionEvent, FileSystemAuditProvider.EVENT_TYPE));
        AuditLogService.get().addEvents(_fileAuditUser, events);
        transaction.addCommitTask(() -> _fileAudits.removeAll(audits), DbScope.CommitTaskOption.POSTCOMMIT);
        transaction.addCommitTask(() -> events.forEach(event -> event.setTransactionId(null)), DbScope.CommitTaskOption.POSTROLLBACK);
    }

    /** For an import that failed before recording its held audits; cleanup may already have deleted some of the files. */
    public static void addFileAuditEventsForRemainingFiles(@Nullable User user, @NotNull List<PendingFileAudit> audits)
    {
        List<FileSystemAuditProvider.FileSystemAuditEvent> events = audits.stream()
                .filter(audit -> audit.file().exists())
                .map(PendingFileAudit::event)
                .toList();
        if (!events.isEmpty())
            AuditLogService.get().addEvents(user, events);
    }

    public static @NotNull Path normalize(@NotNull File file)
    {
        return file.toPath().toAbsolutePath().normalize();
    }

    boolean isRecorded()
    {
        return _recorded;
    }

    void setRecorded()
    {
        _recorded = true;
    }

    @Override
    public void close()
    {
        try
        {
            addFileAuditEventsForRemainingFiles(_fileAuditUser, _fileAudits);
            _fileAudits.clear();
        }
        finally
        {
            deleteScriptDirs(_scriptDirs);
        }
    }

    static void deleteScriptDirs(List<FileLike> scriptDirs)
    {
        for (FileLike scriptDir : scriptDirs)
        {
            try
            {
                FileUtil.deleteDir(scriptDir.toNioPathForWrite().toFile());
                FileLike parent = scriptDir.getParent();
                if (parent != null)
                    parent.delete();
            }
            catch (IOException e)
            {
                LOG.warn("Unable to delete transform script working directory {}", scriptDir, e);
            }
        }
    }

    public static class TestCase extends Assert
    {
        private FileLike _dir;

        @Before
        public void createDirectory() throws IOException
        {
            _dir = FileUtil.createTempDirectoryFileLike("preparedTransformTest");
        }

        @After
        public void deleteDirectory() throws IOException
        {
            FileUtil.deleteDir(_dir.toNioPathForWrite().toFile());
        }

        @Test
        public void testCloseAddsEventsOnlyForRemainingFiles() throws IOException
        {
            String comment = "PreparedTransform close " + GUID.makeGUID();
            FileLike kept = FileUtil.createTempFile("kept", ".tsv", _dir);
            FileLike deleted = FileUtil.createTempFile("deleted", ".tsv", _dir);

            try (PreparedTransform prepared = PreparedTransform.empty())
            {
                prepared.holdFileAudits(TestContext.get().getUser(), List.of(audit(comment, kept), audit(comment, deleted)));
                deleted.delete();
            }

            List<FileSystemAuditProvider.FileSystemAuditEvent> events = selectEvents(comment);
            assertEquals("Events added on close", List.of(kept.getName()), events.stream().map(FileSystemAuditProvider.FileSystemAuditEvent::getFile).toList());
            assertNull("Transaction of an event added on close", events.getFirst().getTransactionId());
        }

        @Test
        public void testRecordLinksTransactionAndCloseAddsNothingMore() throws IOException
        {
            String comment = "PreparedTransform record " + GUID.makeGUID();
            FileLike file = FileUtil.createTempFile("recorded", ".tsv", _dir);
            TransactionAuditProvider.TransactionAuditEvent transactionEvent = AbstractQueryUpdateService.createTransactionAuditEvent(JunitUtil.getTestContainer(), QueryService.AuditAction.INSERT);

            try (PreparedTransform prepared = PreparedTransform.empty())
            {
                prepared.holdFileAudits(TestContext.get().getUser(), List.of(audit(comment, file)));
                try (DbScope.Transaction transaction = DbScope.getLabKeyScope().ensureTransaction())
                {
                    AbstractQueryUpdateService.addTransactionAuditEvent(transaction, TestContext.get().getUser(), transactionEvent);
                    prepared.recordFileAuditEvents(transaction);
                    transaction.commit();
                }
            }

            List<FileSystemAuditProvider.FileSystemAuditEvent> events = selectEvents(comment);
            assertEquals("Events after recording and closing", 1, events.size());
            assertEquals("Transaction of the recorded event", Long.valueOf(transactionEvent.getRowId()), events.getFirst().getTransactionId());
        }

        @Test
        public void testRolledBackRecordLeavesEventsForClose() throws IOException
        {
            String comment = "PreparedTransform rollback " + GUID.makeGUID();
            FileLike file = FileUtil.createTempFile("rolledBack", ".tsv", _dir);
            TransactionAuditProvider.TransactionAuditEvent transactionEvent = AbstractQueryUpdateService.createTransactionAuditEvent(JunitUtil.getTestContainer(), QueryService.AuditAction.INSERT);

            try (PreparedTransform prepared = PreparedTransform.empty())
            {
                prepared.holdFileAudits(TestContext.get().getUser(), List.of(audit(comment, file)));
                try (DbScope.Transaction transaction = DbScope.getLabKeyScope().ensureTransaction())
                {
                    AbstractQueryUpdateService.addTransactionAuditEvent(transaction, TestContext.get().getUser(), transactionEvent);
                    prepared.recordFileAuditEvents(transaction);
                }
            }

            List<FileSystemAuditProvider.FileSystemAuditEvent> events = selectEvents(comment);
            assertEquals("Events after a rolled back record and closing", 1, events.size());
            assertNull("Transaction of an event added on close", events.getFirst().getTransactionId());
        }

        @Test
        public void testCloseAllContinuesPastFailure() throws IOException
        {
            FileLike scriptDir = _dir.resolveChild("run").resolveChild("work");
            FileUtil.mkdirs(scriptDir);
            // Closing order isn't defined, so two failures guarantee one comes before another close
            PreparedTransforms preparedTransforms = new PreparedTransforms();
            preparedTransforms.put(new JSONObject(), failingTransform());
            preparedTransforms.put(new JSONObject(), failingTransform());
            preparedTransforms.put(new JSONObject(), new PreparedTransform(null, DefaultTransformResult.createEmptyResult(), List.of(), List.of(scriptDir)));

            IllegalStateException e = assertThrows(IllegalStateException.class, preparedTransforms::close);
            assertEquals("Failures suppressed into the first", 1, e.getSuppressed().length);
            assertFalse("Working directory of the run that closed cleanly", scriptDir.exists());
        }

        private static PreparedTransform failingTransform()
        {
            return new PreparedTransform(null, DefaultTransformResult.createEmptyResult(), List.of(), List.of())
            {
                @Override
                public void close()
                {
                    throw new IllegalStateException("close failed");
                }
            };
        }

        private static PendingFileAudit audit(String comment, FileLike file)
        {
            FileSystemAuditProvider.FileSystemAuditEvent event = new FileSystemAuditProvider.FileSystemAuditEvent(JunitUtil.getTestContainer(), comment);
            event.setFile(file.getName());
            event.setProvidedFileName(file.getName());
            return new PendingFileAudit(event, file);
        }

        private static List<FileSystemAuditProvider.FileSystemAuditEvent> selectEvents(String comment)
        {
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Comment"), comment);
            return AuditLogService.get().getAuditEvents(JunitUtil.getTestContainer(), TestContext.get().getUser(), FileSystemAuditProvider.EVENT_TYPE, filter, null);
        }
    }
}
