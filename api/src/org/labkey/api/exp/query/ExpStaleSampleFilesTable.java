package org.labkey.api.exp.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.VirtualTable;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.files.FileContentService;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.column.BuiltInColumnTypes;

public class ExpStaleSampleFilesTable extends FilteredTable<ExpSchema>
{
    public ExpStaleSampleFilesTable(@NotNull ExpSchema schema, ContainerFilter cf)
    {
        super(createVirtualTable(schema), schema, cf);
        wrapAllColumns(true);
    }

    private static TableInfo createVirtualTable(@NotNull ExpSchema schema)
    {
        return new ExpStaleSampleFilesTable.FileUnionTable(schema);
    }

    private static class FileUnionTable extends VirtualTable
    {
        private final SQLFragment _query;

        public FileUnionTable(@NotNull UserSchema schema)
        {
            super(CoreSchema.getInstance().getSchema(), ExpSchema.SAMPLE_FILES_TABLE, schema);

            FileContentService svc = FileContentService.get();
            _query = new SQLFragment();
            _query.appendComment("<SampleFileListTableInfo>", getSchema().getSqlDialect());

            TableInfo expDataTable = ExperimentService.get().getTinfoData();
            TableInfo materialTable = ExperimentService.get().getTinfoMaterial();

            SQLFragment sampleFileSql = new SQLFragment("SELECT m.Container, if.FilePathShort \n")
                    .append("FROM (")
                    .append(svc.listSampleFilesQuery(schema.getUser()))
                    .append(") AS if \n")
                    .append("JOIN ")
                    .append(materialTable, "m")
                    .append(" ON if.SourceKey = m.RowId");

            SQLFragment staleFileSql = new SQLFragment("SELECT ed.rowId, ed.name as filename, ed.container, ed.created, ed.createdBy, ed.DataFileUrl FROM ")
                    .append(expDataTable, "ed")
                    .append(" LEFT JOIN (")
                    .append(sampleFileSql)
                    .append(" ) sf\n")
                    .append(" ON ed.name = sf.FilePathShort AND ed.container = sf.container\n")
                    .append(" WHERE ed.datafileurl LIKE ")
                    .appendValue("%@files/sampletype/%")
                    .append(" AND sf.FilePathShort IS NULL");

            _query.append(staleFileSql);

            _query.appendComment("</SampleFileListTableInfo>", getSchema().getSqlDialect());


            var filePathShortCol = new BaseColumnInfo("FileName", this, JdbcType.VARCHAR);
            addColumn(filePathShortCol);

            if (schema.getUser().hasApplicationAdminPermission())
            {
                var filePathCol = new BaseColumnInfo("DataFileUrl", this, JdbcType.VARCHAR);
                filePathCol.setHidden(true);
                addColumn(filePathCol);
            }

            var rowIdCol = new BaseColumnInfo("RowId", this, JdbcType.INTEGER);
            rowIdCol.setHidden(true);
            rowIdCol.setKeyField(true);
            addColumn(rowIdCol);

            var containerCol = new BaseColumnInfo("Container", this, JdbcType.VARCHAR);
            containerCol.setConceptURI(BuiltInColumnTypes.CONTAINERID_CONCEPT_URI);
            addColumn(containerCol);

            var createdCol = new BaseColumnInfo("Created", this, JdbcType.DATE);
            addColumn(createdCol);

            var createdByCol = new BaseColumnInfo("CreatedBy", this, JdbcType.INTEGER);
            UserIdForeignKey.initColumn(createdByCol);
            addColumn(createdByCol);

        }

        @NotNull
        @Override
        public SQLFragment getFromSQL()
        {
            return _query;
        }

    }

}
