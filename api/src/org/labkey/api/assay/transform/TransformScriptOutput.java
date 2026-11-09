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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.util.Pair;
import org.labkey.vfs.FileLike;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The database records one transform script invocation needs, collected while parsing its output and written by
 * {@link DataExchangeHandler#recordTransformationOutput} once the run is saved.
 */
public class TransformScriptOutput
{
    private final String _scriptName;
    private final Set<FileLike> _inputDataFiles;
    private final List<Pair<String, File>> _relatedOutputFiles = new ArrayList<>();
    private final List<ExpData> _transformedDatas = new ArrayList<>();

    public TransformScriptOutput(@NotNull String scriptName, @NotNull Set<FileLike> inputDataFiles)
    {
        _scriptName = scriptName;
        _inputDataFiles = inputDataFiles;
    }

    public @NotNull String getScriptName()
    {
        return _scriptName;
    }

    public @NotNull Set<FileLike> getInputDataFiles()
    {
        return _inputDataFiles;
    }

    /** Pairs of the uploaded file's base name and an extra file the script wrote, to attach as run outputs. */
    public @NotNull List<Pair<String, File>> getRelatedOutputFiles()
    {
        return _relatedOutputFiles;
    }

    public void addRelatedOutputFile(@NotNull String baseName, @NotNull File file)
    {
        _relatedOutputFiles.add(Pair.of(baseName, file));
    }

    /** Transformed data outputs, possibly not yet saved. */
    public @NotNull List<ExpData> getTransformedDatas()
    {
        return _transformedDatas;
    }

    public void addTransformedData(@NotNull ExpData data)
    {
        _transformedDatas.add(data);
    }
}
