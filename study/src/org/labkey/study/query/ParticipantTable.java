package org.labkey.study.query;

import org.labkey.api.query.*;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.ActionURL;
import org.labkey.study.StudySchema;
import org.labkey.study.model.StudyManager;

import java.util.Map;
import java.util.Collections;

public class ParticipantTable extends FilteredTable
{
    StudyQuerySchema _schema;

    public ParticipantTable(StudyQuerySchema schema)
    {
        super(StudySchema.getInstance().getTableInfoParticipant(), schema.getContainer());
        _schema = schema;
        ColumnInfo rowIdColumn = new AliasedColumn(this, "ParticipantId", _rootTable.getColumn("ParticipantId"));
        rowIdColumn.setFk(new TitleForeignKey(getBaseDetailsURL(), null, null, "participantId"));

        addColumn(rowIdColumn);
        ColumnInfo datasetColumn = new AliasedColumn(this, "DataSet", _rootTable.getColumn("ParticipantId"));
        datasetColumn.setIsUnselectable(true);
        datasetColumn.setCaption("DataSet");
        datasetColumn.setFk(new ForeignKey()
        {
            public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
            {
                if (displayField == null)
                    return null;
                return new ParticipantDataSetTable(_schema, parent).getColumn(displayField);
            }

            public TableInfo getLookupTableInfo()
            {
                return new ParticipantDataSetTable(_schema, null);
            }

            public StringExpressionFactory.StringExpression getURL(ColumnInfo parent)
            {
                return null;
            }
        }
        );
        addColumn(datasetColumn);

        if (StudyManager.getInstance().showCohorts(schema.getContainer(), schema.getUser()))
        {
            ColumnInfo cohortColumn = new AliasedColumn(this, "Cohort", _rootTable.getColumn("CohortId"));
            cohortColumn.setFk(new LookupForeignKey("RowId")
            {
                public TableInfo getLookupTableInfo()
                {
                    return new CohortTable(_schema);
                }
            });
            addColumn(cohortColumn);
        }
        
        ForeignKey fkSite = SiteTable.fkFor(_schema);
        addWrapColumn(_rootTable.getColumn("EnrollmentSiteId")).setFk(fkSite);
        addWrapColumn(_rootTable.getColumn("CurrentSiteId")).setFk(fkSite);
        addWrapColumn(_rootTable.getColumn("StartDate"));
        setTitleColumn("ParticipantId");
    }

    public ActionURL getBaseDetailsURL()
    {
        return new ActionURL("Study", "participant", _schema.getContainer().getPath());
    }

    public StringExpressionFactory.StringExpression getDetailsURL(Map<String, ColumnInfo> columns)
    {
        ColumnInfo colRowId = columns.get("ParticipantId");
        if (colRowId == null)
            return null;
        return new LookupURLExpression(getBaseDetailsURL(), Collections.singletonMap("participantId", colRowId));
    }
}
