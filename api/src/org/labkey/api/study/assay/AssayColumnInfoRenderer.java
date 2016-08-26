package org.labkey.api.study.assay;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.security.User;

public interface AssayColumnInfoRenderer
{
    boolean isApplicable(ExpProtocol protocol, ColumnInfo columnInfo, Container container, User user);

    void fixupColumnInfo(ExpProtocol protocol, ColumnInfo columnInfo);
}
