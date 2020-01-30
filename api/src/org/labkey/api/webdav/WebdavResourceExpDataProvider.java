package org.labkey.api.webdav;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpData;

import java.nio.file.Path;
import java.util.List;

public interface WebdavResourceExpDataProvider
{
    /** @return the ExpDatas associated with the data file's URL */
    List<ExpData> getExpDataByPath(Path path, @Nullable Container container);

}
