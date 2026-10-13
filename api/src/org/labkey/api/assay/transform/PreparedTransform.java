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
import org.labkey.api.assay.AssayRunUploadContext;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.vfs.FileLike;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;

/**
 * The outcome of running an assay's transform scripts, before any of it is written to the database.
 * {@link DataTransformService#recordTransform} writes it against the saved run; closing it deletes the scripts'
 * working directories, so close it only after the transaction that recorded it has committed or rolled back.
 */
public class PreparedTransform implements AutoCloseable
{
    private static final Logger LOG = LogHelper.getLogger(PreparedTransform.class, "Assay transform script working directories");

    private final @Nullable DataExchangeHandler _handler;
    private final @NotNull TransformResult _result;
    private final @NotNull List<TransformScriptOutput> _scriptOutputs;
    private final @NotNull List<FileLike> _scriptDirs;
    // Weak so this value doesn't pin its key in DataTransformService's weak-keyed map
    private @Nullable WeakReference<AssayRunUploadContext<?>> _context;
    private boolean _recorded;

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

    void setContext(@NotNull AssayRunUploadContext<?> context)
    {
        _context = new WeakReference<>(context);
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
        AssayRunUploadContext<?> context = _context == null ? null : _context.get();
        if (context != null)
            DataTransformService.get().forgetPreparedTransform(context, this);
        deleteScriptDirs(_scriptDirs);
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
}
