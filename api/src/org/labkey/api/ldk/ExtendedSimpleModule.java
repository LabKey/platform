package org.labkey.api.ldk;

import org.labkey.api.module.SimpleModule;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 2/6/13
 * Time: 6:26 PM
 */
public class ExtendedSimpleModule extends SimpleModule
{


    @Override
    public String getResourcePath()
    {
        return "/" + getClass().getPackage().getName().replaceAll("\\.", "/");
    }

    public boolean hasScripts()
    {
        return true;
    }
}
