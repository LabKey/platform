package org.labkey.study.query;

import org.labkey.api.data.*;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.LookupForeignKey;

import java.io.Writer;
import java.io.IOException;

/**
 * Superclass for specimen tables that adds and configures all the common columns.
 * User: jeckels
 * Date: May 8, 2009
 */
public abstract class AbstractSpecimenTable extends BaseStudyTable
{
    public AbstractSpecimenTable(StudyQuerySchema schema, TableInfo realTable)
    {
        super(schema, realTable);

        ColumnInfo rowIdColumn = addWrapColumn(_rootTable.getColumn("RowId"));
        rowIdColumn.setKeyField(true);
        addWrapColumn(_rootTable.getColumn("Container")).setFk(new ContainerForeignKey());
        addWrapColumn(_rootTable.getColumn("SpecimenHash")).setIsHidden(true);
        addWrapColumn(_rootTable.getColumn("GlobalUniqueId"));
        addWrapParticipantColumn("PTID").setKeyField(true);
    }

    protected void addVolumeAndTypeColumns()
    {
        addWrapColumn(_rootTable.getColumn("Volume"));
        addWrapColumn(_rootTable.getColumn("VolumeUnits"));
        addWrapTypeColumn("PrimaryType", "PrimaryTypeId");
        addWrapTypeColumn("DerivativeType", "DerivativeTypeId");
        addWrapTypeColumn("AdditiveType", "AdditiveTypeId");
        addWrapTypeColumn("DerivativeType2", "DerivativeTypeId2");
        addWrapColumn(_rootTable.getColumn("SubAdditiveDerivative"));
        addWrapColumn(_rootTable.getColumn("PrimaryVolume"));
        addWrapColumn(_rootTable.getColumn("PrimaryVolumeUnits"));
        addWrapColumn(_rootTable.getColumn("DrawTimestamp"));
        addWrapColumn(_rootTable.getColumn("FrozenTime"));
        addWrapColumn(_rootTable.getColumn("ProcessingTime"));

        addWrapLocationColumn("Clinic", "OriginatingLocationId");

        addWrapColumn(_rootTable.getColumn("SalReceiptDate"));
        addWrapColumn(_rootTable.getColumn("ClassId"));
        addWrapColumn(_rootTable.getColumn("ProtocolNumber"));

        ColumnInfo commentsColumn = new AliasedColumn(this, "Comments", _rootTable.getColumn("GlobalUniqueId"));
        LookupForeignKey commentsFK = new LookupForeignKey("GlobalUniqueId")
        {
            public TableInfo getLookupTableInfo()
            {
                return new SpecimenCommentTable(_schema);
            }
        };
        commentsFK.setJoinOnContainer(true);
        commentsColumn.setFk(commentsFK);
        commentsColumn.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new CommentDisplayColumn(colInfo);
            }
        });
        addColumn(commentsColumn);
    }

    protected void addVisitColumn(boolean dateBased)
    {
        AliasedColumn visitColumn;
        ColumnInfo visitDescriptionColumn = addWrapColumn(_rootTable.getColumn("VisitDescription"));
        if (dateBased)
        {
            //consider:  use SequenceNumMin for visit-based studies too (in visit-based studies VisitValue == SequenceNumMin)
            // could change to visitrowid but that changes datatype and displays rowid
            // instead of sequencenum when label is null
            visitColumn = new AliasedColumn(this, "Visit", _rootTable.getColumn("SequenceNumMin"));
            visitColumn.setCaption("Timepoint");
            visitDescriptionColumn.setIsHidden(true);
        }
        else
        {
            visitColumn = new AliasedColumn(this, "Visit", _rootTable.getColumn("VisitValue"));
        }
        LookupForeignKey visitFK = new LookupForeignKey(null, (String) null, "SequenceNumMin", null)
        {
            public TableInfo getLookupTableInfo()
            {
                return new VisitTable(_schema);
            }
        };
        visitFK.setJoinOnContainer(true);
        visitColumn.setFk(visitFK);
        visitColumn.setKeyField(true);
        addColumn(visitColumn);
    }

    public static class CommentDisplayColumn extends DataColumn
    {
        public CommentDisplayColumn(ColumnInfo commentColumn)
        {
            super(commentColumn);
        }

        public Object getDisplayValue(RenderContext ctx)
        {
            Object value = getDisplayColumn().getValue(ctx);
            if (value == null)
                return "";
            else
                return value;
        }

        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            Object value = getDisplayColumn().getValue(ctx);
            if (value != null  && value instanceof String)
                out.write((String) value);
        }
    }

}

