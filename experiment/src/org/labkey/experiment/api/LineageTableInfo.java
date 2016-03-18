package org.labkey.experiment.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.VirtualTable;
import org.labkey.api.exp.api.ExpLineageOptions;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.UserSchema;

/**
 * User: kevink
 * Date: 2/23/16
 */
public class LineageTableInfo extends VirtualTable
{
    private SQLFragment _lsids;
    private boolean _parents;
    private Integer _depth;
    private SQLFragment _expType;
    private SQLFragment _cpasType;

    public LineageTableInfo(String name, @NotNull UserSchema schema, SQLFragment lsids, boolean parents, @Nullable Integer depth, @Nullable SQLFragment expType, @Nullable SQLFragment cpasType)
    {
        super(schema.getDbSchema(), name, schema);
        _lsids = lsids;
        _parents = parents;

        // depth is negative for parent values
        if (depth != null && depth > 0 && _parents)
            depth = -1 * depth;
        _depth = depth;
        _expType = expType;
        _cpasType = cpasType;

        ColumnInfo selfLsid = new ColumnInfo(FieldKey.fromParts("self_lsid"), this, JdbcType.VARCHAR);
        selfLsid.setSqlTypeName("lsidtype");
        addColumn(selfLsid);

        ColumnInfo selfRowId = new ColumnInfo(FieldKey.fromParts("self_rowid"), this, JdbcType.INTEGER);
        addColumn(selfRowId);

        ColumnInfo depthCol = new ColumnInfo(FieldKey.fromParts("depth"), this, JdbcType.INTEGER);
        addColumn(depthCol);

        ColumnInfo parentContainer = new ColumnInfo(FieldKey.fromParts("parent_container"), this, JdbcType.VARCHAR);
        parentContainer.setSqlTypeName("entityid");
        ContainerForeignKey.initColumn(parentContainer, schema);
        addColumn(parentContainer);

        ColumnInfo parentExpType = new ColumnInfo(FieldKey.fromParts("parent_exptype"), this, JdbcType.VARCHAR);
        addColumn(parentExpType);

        ColumnInfo parentCpasType = new ColumnInfo(FieldKey.fromParts("parent_cpastype"), this, JdbcType.VARCHAR);
        addColumn(parentCpasType);

        ColumnInfo parentName = new ColumnInfo(FieldKey.fromParts("parent_name"), this, JdbcType.VARCHAR);
        addColumn(parentName);

        ColumnInfo parentLsid = new ColumnInfo(FieldKey.fromParts("parent_lsid"), this, JdbcType.VARCHAR);
        parentLsid.setSqlTypeName("lsidtype");
        // TODO: parameterize the lookup target table depending on what SampleSet or DataClass we are interested in
        parentLsid.setFk(new LookupForeignKey("lsid")
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return new NodesTableInfo(schema);
            }
        });
        addColumn(parentLsid);

        ColumnInfo parentRowId = new ColumnInfo(FieldKey.fromParts("parent_rowId"), this, JdbcType.INTEGER);
        //parentRowId.setFk(new QueryForeignKey("exp", schema.getContainer(), schema.getContainer(), schema.getUser(), "Materials", "rowId", "Name"));
        addColumn(parentRowId);

        ColumnInfo role = new ColumnInfo(FieldKey.fromParts("role"), this, JdbcType.VARCHAR);
        addColumn(role);

        ColumnInfo childContainer = new ColumnInfo(FieldKey.fromParts("child_container"), this, JdbcType.VARCHAR);
        childContainer.setSqlTypeName("entityid");
        ContainerForeignKey.initColumn(childContainer, schema);
        addColumn(childContainer);

        ColumnInfo childExpType = new ColumnInfo(FieldKey.fromParts("child_exptype"), this, JdbcType.VARCHAR);
        addColumn(childExpType);

        ColumnInfo childCpasType = new ColumnInfo(FieldKey.fromParts("child_cpastype"), this, JdbcType.VARCHAR);
        addColumn(childCpasType);

        ColumnInfo childName = new ColumnInfo(FieldKey.fromParts("child_name"), this, JdbcType.VARCHAR);
        addColumn(childName);

        ColumnInfo childLsid = new ColumnInfo(FieldKey.fromParts("child_lsid"), this, JdbcType.VARCHAR);
        childLsid.setSqlTypeName("lsidtype");
        // TODO: parameterize the lookup target table depending on what SampleSet or DataClass we are interested in
        childLsid.setFk(new LookupForeignKey("lsid")
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return new NodesTableInfo(schema);
            }
        });
        addColumn(childLsid);

        ColumnInfo childRowId = new ColumnInfo(FieldKey.fromParts("child_rowId"), this, JdbcType.INTEGER);
        addColumn(childRowId);

    }

    @NotNull
    @Override
    public SQLFragment getFromSQL()
    {
        ExpLineageOptions options = new ExpLineageOptions();
        options.setParents(_parents);
        options.setChildren(!_parents);

        boolean filtered =
                (_expType != null && !isNULL(_expType)) ||
                (_cpasType != null && !isNULL(_cpasType)) ||
                (_depth != null && _depth != 0);

        SQLFragment tree = ExperimentServiceImpl.get().generateExperimentTreeSQL(_lsids, options);

        SQLFragment sql = new SQLFragment();
        sql.append("(SELECT * FROM (");
        sql.append(tree);
        sql.append(") AS X\n");
        sql.append("WHERE\n");

        // Remove any rows that match self_lsid from the results.
        // CONSIDER: Do this in the generateExperimentTreeSQL itself so the lineage.api looks the same
        sql.append("X.").append(_parents ? "parent_" : "child_").append("lsid <> X.self_lsid\n");

        if (_expType != null && !isNULL(_expType))
            sql.append("AND X.").append(_parents ? "parent_" : "child_").append("exptype = ").append(_expType).append("\n");

        if (_cpasType != null && !isNULL(_cpasType))
            sql.append("AND X.").append(_parents ? "parent_" : "child_").append("cpastype = ").append(_cpasType).append("\n");

        if (_depth != null && _depth != 0)
            sql.append("AND X.depth ").append(_parents ? " > " : " < ").append(_depth).append("\n");

        sql.append(")\n");

        return sql;
    }

    public static boolean isNULL(SQLFragment f)
    {
        if (f.getParams().size() > 0)
            return false;
        String s = f.getSQL().trim();
        if (s.equalsIgnoreCase("NULL"))
            return true;
        return false;
    }

    /**
     * Union of all Data, Material, and ExperimentRun rows for use as a generic lookup target.
     */
    private static class NodesTableInfo extends VirtualTable
    {
        public NodesTableInfo(@Nullable UserSchema schema)
        {
            super(schema.getDbSchema(), "Nodes", schema);

            ColumnInfo containerCol = new ColumnInfo(FieldKey.fromParts("Container"), this, JdbcType.VARCHAR);
            containerCol.setSqlTypeName("entityid");
            ContainerForeignKey.initColumn(containerCol, schema);
            addColumn(containerCol);

            ColumnInfo name = new ColumnInfo(FieldKey.fromParts("name"), this, JdbcType.VARCHAR);
            name.setURL(DetailsURL.fromString("experiment/resolveLsid.view?lsid=${LSID}&type=${exptype}"));
            addColumn(name);

            ColumnInfo expType = new ColumnInfo(FieldKey.fromParts("exptype"), this, JdbcType.VARCHAR);
            addColumn(expType);

            ColumnInfo cpasType = new ColumnInfo(FieldKey.fromParts("cpastype"), this, JdbcType.VARCHAR);
            addColumn(cpasType);

            ColumnInfo lsid = new ColumnInfo(FieldKey.fromParts("lsid"), this, JdbcType.VARCHAR);
            lsid.setSqlTypeName("lsidtype");
            addColumn(lsid);

            ColumnInfo rowId = new ColumnInfo(FieldKey.fromParts("rowId"), this, JdbcType.INTEGER);
            addColumn(rowId);

        }

        @NotNull
        @Override
        public SQLFragment getFromSQL()
        {
            SQLFragment sql = new SQLFragment();
            sql.append(
                    "SELECT container, CAST('Data' AS VARCHAR(50)) AS exptype, CAST(cpastype AS VARCHAR(200)) AS cpastype, name, lsid, rowid\n" +
                    "FROM exp.Data\n" +
                    "\n" +
                    "UNION ALL\n" +
                    "\n" +
                    "SELECT container, CAST('Material' AS VARCHAR(50)) AS exptype, CAST(cpastype AS VARCHAR(200)) AS cpastype, name, lsid, rowid\n" +
                    "FROM exp.Material\n" +
                    "\n" +
                    "UNION ALL\n" +
                    "\n" +
                    "SELECT container, CAST('ExperimentRun' AS VARCHAR(50)) AS exptype, CAST(NULL AS VARCHAR(200)) AS cpastype, name, lsid, rowid\n" +
                    "FROM exp.ExperimentRun\n");
            return sql;
        }
    }

}
