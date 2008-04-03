package org.labkey.experiment.api.flag;

import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.VirtualTable;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.exp.api.ExperimentService;

public class FlagForeignKey implements ForeignKey
{
    public static final String DISPLAYFIELD_NAME = "Comment";
    String _urlFlagged;
    String _urlUnflagged;
    public FlagForeignKey(String urlFlagged, String urlUnflagged)
    {
        _urlFlagged = urlFlagged;
        _urlUnflagged = urlUnflagged;
    }

    public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
    {
        if (displayField == null)
        {
            displayField = DISPLAYFIELD_NAME;
        }
        if (!displayField.equalsIgnoreCase(DISPLAYFIELD_NAME))
            return null;
        return new FlagColumn(parent, _urlFlagged, _urlUnflagged);
    }

    public TableInfo getLookupTableInfo()
    {
        VirtualTable ret = new VirtualTable(ExperimentService.get().getSchema());
        ColumnInfo colComment = new ColumnInfo("Comment", ret);
        colComment.setSqlTypeName("VARCHAR");
        ret.addColumn(colComment);
        return ret;
    }

    public StringExpressionFactory.StringExpression getURL(ColumnInfo parent)
    {
        return null;
    }
}
