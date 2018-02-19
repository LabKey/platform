/*
 * Copyright (c) 2005-2016 LabKey Corporation
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
package org.labkey.api.exp;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * Responsible for storing whatever is desired from a data file in the database. May be the full
 * file's content, a subset, just summary, etc. Implementations recognizes which {@link ExpData} it should each via
 * the Handler superinterface.
 * User: jeckels
 * Date: Sep 23, 2005
 */
public interface ExperimentDataHandler extends Handler<ExpData>
{
    @Nullable
    DataType getDataType();

    default String getFileName(ExpData data, String defaultName)
    {
        return defaultName;
    }

    /**
     * Import whatever content from the file is destined for storage in the database. Typically persisted in a schema
     * owned by the module that holds the implementation of the ExperimentDataHandler.
     */
    void importFile(@NotNull ExpData data, File dataFile, @NotNull ViewBackgroundInfo info, @NotNull Logger log, @NotNull XarContext context) throws ExperimentException;
    default void importFile(@NotNull ExpData data, Path dataFile, @NotNull ViewBackgroundInfo info, @NotNull Logger log, @NotNull XarContext context) throws ExperimentException
    {
        if (FileUtil.hasCloudScheme(dataFile))
            throw new ExperimentException(this.getClass().getName() + " does not support importFile on a cloud path");
        importFile(data, dataFile.toFile(), info, log, context);
    }

    /**
     * Stream the content of this data object. Typically this just streams the bytes of the file from disk, but could
     * create something based exclusively on what's in the database.
     */
    void exportFile(ExpData data, File dataFile, User user, OutputStream out) throws ExperimentException;
    default void exportFile(ExpData data, Path dataFile, User user, OutputStream out) throws ExperimentException
    {
    }

    /** @return URL to the imported version of the data, like a grid view over a database table or a custom details page */
    @Nullable
    ActionURL getContentURL(ExpData data);

    /**
     * Invoked prior to the deletion of the data, potentially because its run is being deleted. Any related rows in
     * the database should detach themselves to avoid FK violations, but may want to avoid being deleted themselves.
     * In the case of moving runs to another container, the data rows could later be re-attached to the relevant data/run
     * without needing to fully reimport. In cases of actual deletion, deleteData() will later be called.
     */
    void beforeDeleteData(List<ExpData> datas) throws ExperimentException;

    /**
     * Completely delete all database rows attached to this data object.
     */
    void deleteData(ExpData data, Container container, User user);

    boolean hasContentToExport(ExpData data, File file);
    default boolean hasContentToExport(ExpData data, Path file)
    {
        return false;
    }

    default void runMoved(ExpData newData, Container container, Container targetContainer, String oldRunLSID, String newRunLSID, User user, int oldDataRowID) throws ExperimentException
    {
        // Do nothing
    }

    void beforeMove(ExpData oldData, Container container, User user) throws ExperimentException;

    default @Nullable URLHelper getShowFileURL(ExpData data)
    {
        return null;
    }
}
