/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
package org.labkey.experiment;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.files.TableUpdaterFileListener;
import org.labkey.api.security.User;

import java.io.File;

/**
 * User: jeckels
 * Date: 11/7/12
 */
public class ExpDataFileListener extends TableUpdaterFileListener
{
    public ExpDataFileListener()
    {
        super(ExperimentService.get().getTinfoData(), "DataFileUrl", Type.uri, "RowId");
    }

    @Override
    public void fileMoved(@NotNull File src, @NotNull File dest, @Nullable User user, @Nullable Container c)
    {
        ExpData data = ExperimentService.get().getExpDataByURL(src, c);

        if (data == null)
        {
            data = ExperimentService.get().getExpDataByURL(dest, c);
        }
            // Do not create a new ExpData for directories
        if (data == null && !dest.isDirectory() && c != null)
        {
            data = ExperimentService.get().createData(c, new DataType("UploadedFile"));
        }

        if (data != null && data.getName().equals(src.getName()) && !src.getName().equals(dest.getName()))
        {
            // The file has been renamed, so rename the exp.data row if its name matches
            data.setName(dest.getName());
            data.save(user);
        }

        super.fileMoved(src, dest, user, c);
    }
}
