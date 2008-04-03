package org.labkey.study.plate.query;

import org.labkey.api.query.*;
import org.labkey.api.data.*;
import org.labkey.api.exp.*;

import java.sql.Types;
import java.sql.SQLException;
import java.util.Map;
import java.util.TreeMap;
import java.util.List;
import java.util.ArrayList;

import org.labkey.study.StudySchema;

/**
 * User: brittp
 * Date: Nov 1, 2006
 * Time: 4:37:02 PM
 */
public class PlateTable extends BasePlateTable
{
    public PlateTable(PlateSchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoPlate());
        FieldKey keyProp = new FieldKey(null, "Property");
        List<FieldKey> visibleColumns = new ArrayList<FieldKey>();
        addWrapColumn(_rootTable.getColumn("RowId"));
        addWrapColumn(_rootTable.getColumn("Name"));
        setTitleColumn("Name");
        ColumnInfo templateCol =_rootTable.getColumn("Template");
        addWrapColumn(templateCol);
        addCondition(templateCol, "0");
        addWrapColumn(_rootTable.getColumn("Created"));
        visibleColumns.add(FieldKey.fromParts("Created"));
        addWrapColumn(_rootTable.getColumn("CreatedBy"));
        visibleColumns.add(FieldKey.fromParts("CreatedBy"));

        String sqlObjectId = "( SELECT objectid FROM exp.object WHERE exp.object.objecturi = " + ExprColumn.STR_TABLE_ALIAS + ".lsid)";

        try
        {
            ColumnInfo colProperty = new ExprColumn(this, "property", new SQLFragment(sqlObjectId), Types.INTEGER);
            String propPrefix = new Lsid("PlateInstance", "Folder-" + schema.getContainer().getRowId(), "").toString();
            SimpleFilter filter = new SimpleFilter();
            filter.addCondition("PropertyURI", propPrefix, CompareType.STARTS_WITH);
            PropertyDescriptor[] pds = Table.select(OntologyManager.getTinfoPropertyDescriptor(), Table.ALL_COLUMNS, filter, null, PropertyDescriptor.class);
            Map<String, PropertyDescriptor> map = new TreeMap<String, PropertyDescriptor>();
            for(PropertyDescriptor pd : pds)
            {
                if (pd.getPropertyType() == PropertyType.DOUBLE)
                    pd.setFormat("0.##");
                map.put(pd.getName(), pd);
                visibleColumns.add(new FieldKey(keyProp, pd.getName()));
            }
            colProperty.setFk(new PropertyForeignKey(map, schema));
            colProperty.setIsUnselectable(true);
            addColumn(colProperty);
            setDefaultVisibleColumns(visibleColumns);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    protected String getPlateIdColumnName()
    {
        return "RowId";
    }
}
