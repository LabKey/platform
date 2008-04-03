package org.labkey.study.query;

import org.labkey.api.data.*;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.query.AliasedColumn;
import org.labkey.study.StudySchema;
import org.labkey.study.model.DataSetDefinition;

public class ParticipantDataSetTable extends VirtualTable
{
    StudyQuerySchema _schema;
    ColumnInfo _colParticipantId;
    
    public ParticipantDataSetTable(StudyQuerySchema schema, ColumnInfo colParticipantId)
    {
        super(StudySchema.getInstance().getSchema());
        _schema = schema;
        _colParticipantId = colParticipantId;
        for (DataSetDefinition dataset : _schema.getStudy().getDataSets())
        {
            String name = dataset.getLabel();
            if (name == null)
                continue;
            // if not keyed by Participant/SequenceNum it is not a lookup
            if (dataset.getKeyPropertyName() != null)
                continue;
            // duplicate labels! see BUG 2206
            if (getColumn(name) != null)
                continue;
            addColumn(createDataSetColumn(name, dataset));
        }
    }

    protected ColumnInfo createDataSetColumn(String name, final DataSetDefinition def)
    {
        ColumnInfo column;
        if (_colParticipantId == null)
        {
            column = new ColumnInfo(name, this);
            column.setSqlTypeName("VARCHAR");
        }
        else
        {
            column = new AliasedColumn(name, _colParticipantId);
        }
        column.setCaption(def.getLabel());
        column.setFk(new ForeignKey()
        {
            public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
            {
                if (displayField == null)
                    return null;
                ColumnInfo ret = new ParticipantVisitDataSetTable(_schema, def, parent).getColumn(displayField);
                ret.setCaption(parent.getCaption() + " " + ret.getCaption());
                return ret;
            }

            public TableInfo getLookupTableInfo()
            {
                return new ParticipantVisitDataSetTable(_schema, def, null);
            }

            public StringExpressionFactory.StringExpression getURL(ColumnInfo parent)
            {
                return null;
            }
        });
        column.setIsUnselectable(true);
        return column;
    }
}
