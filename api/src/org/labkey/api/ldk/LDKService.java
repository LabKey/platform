package org.labkey.api.ldk;

import org.labkey.api.data.TableCustomizer;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 11/4/12
 * Time: 3:48 PM
 */
abstract public class LDKService
{
    static LDKService instance;

    public static LDKService get()
    {
        return instance;
    }

    static public void setInstance(LDKService instance)
    {
        LDKService.instance = instance;
    }

    abstract public TableCustomizer getDefaultTableCustomizer();

    abstract public TableCustomizer getBuiltInColumnsCustomizer();
}
