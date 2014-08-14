package org.labkey.study.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.study.StudySchema;

import java.io.IOException;
import java.io.Writer;

/**
 * Created by klum on 8/8/2014.
 */
public class StudySnapshotTable extends FilteredTable<StudyQuerySchema>
{
    public StudySnapshotTable(StudyQuerySchema schema)
    {
        super(StudySchema.getInstance().getTableInfoStudySnapshot(), schema);

        setDescription("Contains a row for each Ancillary or Published Study that was created from the study in this folder." +
                " Only users with administrator permissions will see any data.");

        ColumnInfo rowIdColumn = addWrapColumn(_rootTable.getColumn("RowId"));
        rowIdColumn.setHidden(true);
        rowIdColumn.setUserEditable(false);
        rowIdColumn.setKeyField(true);

        ColumnInfo source = new AliasedColumn(this, "Source", _rootTable.getColumn("source"));
        ContainerForeignKey.initColumn(source, getUserSchema());
        addColumn(source);

        ColumnInfo destination = new AliasedColumn(this, "Destination", _rootTable.getColumn("destination"));
        ContainerForeignKey.initColumn(destination, getUserSchema());
        addColumn(destination);

        ColumnInfo createdBy = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("CreatedBy")));
        UserIdForeignKey.initColumn(createdBy);

        ColumnInfo modifiedBy = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("ModifiedBy")));
        UserIdForeignKey.initColumn(modifiedBy);

        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Created")));
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Modified")));
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Refresh")));

        ColumnInfo settingsColumn = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Settings")));
        settingsColumn.setDisplayColumnFactory(new DisplayColumnFactory(){
            @Override
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new DataColumn(colInfo){

                    @Override
                    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
                    {
                        Object value = getValue(ctx);
                        ObjectMapper jsonMapper = new ObjectMapper();
                        Object jsonValue = jsonMapper.readValue(String.valueOf(value), Object.class);

                        out.write(PageFlowUtil.filter(jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonValue), true, true));
                  }
                };
            }
        });

        if (!getContainer().hasPermission(schema.getUser(), AdminPermission.class))
        {
            // non-admins see no data
            addCondition(new SQLFragment("1=2"), FieldKey.fromParts("CreatedBy"));
        }
    }


    @Override
    protected String getContainerFilterColumn()
    {
        return "Source";
    }
}
