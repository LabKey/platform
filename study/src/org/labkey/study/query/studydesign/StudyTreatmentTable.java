package org.labkey.study.query.studydesign;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.wiki.WikiRendererDisplayColumn;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;
import org.labkey.study.query.StudyQuerySchema;

import java.util.ArrayList;
import java.util.List;

/**
 * User: cnathe
 * Date: 12/17/13
 */
public class StudyTreatmentTable extends DefaultStudyDesignTable
{
    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {

        defaultVisibleColumns.add(FieldKey.fromParts("Container"));
        defaultVisibleColumns.add(FieldKey.fromParts("Label"));
        defaultVisibleColumns.add(FieldKey.fromParts("Description"));
    }

    public StudyTreatmentTable(Domain domain, DbSchema dbSchema, UserSchema schema)
    {
        super(domain, dbSchema, schema);

        setName(StudyQuerySchema.TREATMENT_TABLE_NAME);
        setDescription("Contains one row per study treatment");

        final ColumnInfo descriptionRendererTypeColumn = getColumn("DescriptionRendererType");
        descriptionRendererTypeColumn.setFk(new LookupForeignKey("Value")
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return QueryService.get().getUserSchema(_userSchema.getUser(), _userSchema.getContainer(), WikiService.SCHEMA_NAME).getTable(WikiService.RENDERER_TYPE_TABLE_NAME);
            }
        });

        ColumnInfo descriptionColumn = getColumn("Description");
        descriptionColumn.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            @Override
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new WikiRendererDisplayColumn(colInfo, descriptionRendererTypeColumn.getName(), WikiRendererType.TEXT_WITH_LINKS);
            }
        });
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }
}
