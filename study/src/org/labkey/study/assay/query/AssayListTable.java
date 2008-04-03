package org.labkey.study.assay.query;

import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.*;
import org.labkey.api.data.*;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.ActionURL;

import java.util.*;

/**
 * User: brittp
 * Date: Jun 28, 2007
 * Time: 11:07:32 AM
 */
public class AssayListTable extends FilteredTable
{
    protected AssaySchema _schema;
    public AssayListTable(AssaySchema schema, String alias)
    {
        super(ExperimentService.get().getTinfoProtocol(), null);
        setAlias(alias);

        List<String> containerIds = new ArrayList<String>(2);
        containerIds.add(schema.getContainer().getId());
        containerIds.add(schema.getContainer().getProject().getId());
        addInClause(_rootTable.getColumn("container"), containerIds);
        addCondition(_rootTable.getColumn("ApplicationType"), "ExperimentRun");

        _schema = schema;

        addWrapColumn(_rootTable.getColumn("RowId")).setIsHidden(true);
        ColumnInfo nameCol = addWrapColumn(_rootTable.getColumn("Name"));
        nameCol.setURL(getDetailsURL(Collections.singletonMap("RowId", getColumn("RowId"))));
        ColumnInfo desc = wrapColumn("Description", _rootTable.getColumn("ProtocolDescription"));
        addColumn(desc);
        addWrapColumn(_rootTable.getColumn("Created"));
        addWrapColumn(_rootTable.getColumn("CreatedBy"));
        addWrapColumn(_rootTable.getColumn("Modified"));
        addWrapColumn(_rootTable.getColumn("ModifiedBy"));
        ColumnInfo typeColumn = wrapColumn("Type", _rootTable.getColumn("LSID"));
        typeColumn.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new TypeDisplayColumn(colInfo);
            }
        });
        addColumn(typeColumn);

        List<FieldKey> defaultCols = new ArrayList<FieldKey>();
        defaultCols.add(FieldKey.fromParts("Name"));
        defaultCols.add(FieldKey.fromParts("Description"));
        defaultCols.add(FieldKey.fromParts("Type"));
        defaultCols.add(FieldKey.fromParts("Created"));
        defaultCols.add(FieldKey.fromParts("CreatedBy"));
        defaultCols.add(FieldKey.fromParts("Modified"));
        defaultCols.add(FieldKey.fromParts("ModifiedBy"));
        setDefaultVisibleColumns(defaultCols);

        // TODO - this is a horrible way to filter out non-assay protocols
        addCondition(new SQLFragment("(SELECT MAX(pd.PropertyId) from exp.object o, exp.objectproperty op, exp.propertydescriptor pd where pd.propertyid = op.propertyid and op.objectid = o.objectid and o.objecturi = " + _rootTable + ".lsid AND pd.PropertyURI LIKE '%AssayDomain-Run%') IS NOT NULL"));
    }

    public StringExpressionFactory.StringExpression getDetailsURL(Map<String, ColumnInfo> columns)
    {
        ColumnInfo rowid = columns.get("RowId");
        if (rowid == null)
            return null;
        ActionURL url = new ActionURL("assay",
            "summaryRedirect", _schema.getContainer());
        return new LookupURLExpression(url, Collections.singletonMap("rowId", rowid));
    }
}
