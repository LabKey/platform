package org.labkey.experiment;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.files.TableUpdaterFileMoveListener;
import org.labkey.api.security.User;

import java.io.File;

/**
 * User: jeckels
 * Date: 11/7/12
 */
public class ExpDataFileMoveListener extends TableUpdaterFileMoveListener
{
    public ExpDataFileMoveListener()
    {
        super(ExperimentService.get().getTinfoData(), "DataFileUrl", Type.uri);
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
