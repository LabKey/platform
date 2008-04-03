package org.labkey.api.study.assay;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.study.actions.AssayRunUploadForm;

/**
 * User: jeckels
 * Date: Aug 3, 2007
 */
public class AssayWarningsDisplayColumn extends DataColumn
{
    private AssayRunUploadForm _form;

    public AssayWarningsDisplayColumn(AssayRunUploadForm form)
    {
        super(createColumnInfo());
        _form = form;
        setInputType("checkbox");
    }

    private static ColumnInfo createColumnInfo()
    {
        ColumnInfo column = new ColumnInfo("ignoreWarnings");
        return column;
    }

    public boolean isEditable()
    {
        return true;
    }
}
