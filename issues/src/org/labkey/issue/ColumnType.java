package org.labkey.issue;

import org.jetbrains.annotations.NotNull;
import org.labkey.issue.model.Issue;

import java.util.Map;

/**
 * Created by klum on 4/18/2016.
 */
@Deprecated // This class can be deleted in 19.1
public interface ColumnType
{
    int getOrdinal();
    String getColumnName();
    boolean isStandard();
    boolean isCustomString();
    boolean isCustomInteger();
    boolean isCustom();

    // Most pick lists display a blank entry
    boolean allowBlank();
    @NotNull String[] getInitialValues();
    @NotNull String getInitialDefaultValue();
}
