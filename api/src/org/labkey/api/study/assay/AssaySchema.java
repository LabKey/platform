package org.labkey.api.study.assay;

import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

/**
 * User: jeckels
 * Date: May 25, 2009
 */
public abstract class AssaySchema extends UserSchema
{
    protected Container _targetStudy;

    public static String NAME = "assay";

    public AssaySchema(String name, User user, Container container, DbSchema dbSchema)
    {
        super(name, user, container, dbSchema);
    }

    public void setTargetStudy(Container studyContainer)
    {
        _targetStudy = studyContainer;
    }

    public Container getTargetStudy()
    {
        return _targetStudy;
    }
}
