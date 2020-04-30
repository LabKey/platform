package org.labkey.experiment.api.data;

public class ImmediateParentOfMethod extends ParentOfMethod
{
    public static final String NAME = "ExpDirectParentOf";

    @Override
    protected int getDepth()
    {
        return -1;
    }
}
