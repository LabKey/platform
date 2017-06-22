/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

package org.labkey.api.study.assay;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.view.HttpView;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Data collectors are responsible for getting the primary data file(s) for the assay to consume. Different
 * implementations gather the file(s) through different mechanisms - from a directory that already exists
 * on the server's file system, via a HTML textarea that the user has copy/pasted data into, etc.
 *
 * Instances will be associated with a single request and/or upload attempt, and so will be short-lived and can hold state.
 *
 * User: jeckels
 * Date: Jul 12, 2007
 */
public interface AssayDataCollector<ContextType extends AssayRunUploadContext>
{
    String PRIMARY_FILE = "__primaryFile__";

    /** Indicates if there are (or might be) additional files that queued up to be consumed by more runs */
    enum AdditionalUploadType
    {
        Disallowed(null), AlreadyUploaded("Save and Import Next File"), UploadRequired("Save and Import Another Run");

        private String _buttonText;

        AdditionalUploadType(String buttonText)
        {
            _buttonText = buttonText;
        }

        public String getButtonText()
        {
            return _buttonText;
        }
    }

    /** @return the UI to plug into the import wizard for the user to somehow select/upload the file */
    HttpView getView(ContextType context) throws ExperimentException;

    /** @return the name for this AssayDataCollector. Needs to be unique within the set of data collectors for any given import attempt */
    String getShortName();

    String getDescription(ContextType context);

    /** Map of original file name to file on disk */
    @NotNull Map<String, File> createData(ContextType context) throws IOException, ExperimentException;

    boolean isVisible();

    Map<String, File> uploadComplete(ContextType context, @Nullable ExpRun run) throws ExperimentException;

    AdditionalUploadType getAdditionalUploadType(ContextType context);

    default void initDir(ContextType context) throws ExperimentException {}

    File getRoot(@Nullable ExpRun run, @Nullable File data);

    /**
     * For files that already existed on the server's file system prior to import, and which have been copied
     * to a temporary directory for processing, the original path to the primary data file.
     * @return null if the file was uploaded as part of the import
     */
    @Nullable
    default File getOriginalFileLocation() { return null; }
}
