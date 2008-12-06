/*
 * Copyright (c) 2006-2008 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.study.query;

import org.labkey.study.StudySchema;
import org.labkey.study.SampleManager;
import org.labkey.study.model.SpecimenComment;
import org.labkey.api.data.*;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.ExprColumn;

import java.sql.Types;
import java.sql.SQLException;
import java.io.Writer;
import java.io.IOException;
import java.util.*;

public class SpecimenSummaryTable extends BaseStudyTable
{
    public SpecimenSummaryTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoSpecimenSummary());
        ColumnInfo participantColumn = new AliasedColumn(this, "ParticipantId", _rootTable.getColumn("PTID"));
        participantColumn.setFk(new QueryForeignKey(_schema, "Participant", "ParticipantId", null));
        participantColumn.setKeyField(true);
        addColumn(participantColumn);

        // Same as in SpecimentDetailTable
        AliasedColumn visitColumn;
        ColumnInfo visitDescriptionColumn = addWrapColumn(_rootTable.getColumn("VisitDescription"));
        if (_schema.getStudy().isDateBased())
        {
            visitColumn = new AliasedColumn(this, "Visit", _rootTable.getColumn("SequenceNumMin"));
            visitColumn.setCaption("Timepoint");
            visitDescriptionColumn.setIsHidden(true);
        }
        else
        {
            visitColumn = new AliasedColumn(this, "Visit", _rootTable.getColumn("VisitValue"));
        }
        visitColumn.setFk(new LookupForeignKey(null, (String) null, "SequenceNumMin", null)
        {
            public TableInfo getLookupTableInfo()
            {
                return new VisitTable(_schema);
            }
        });
        visitColumn.setKeyField(true);
        addColumn(visitColumn);

        ColumnInfo pvColumn = new ParticipantVisitColumn(
                "ParticipantVisit",
                new AliasedColumn(this, "PVParticipant", getRealTable().getColumn("PTID")),
                new AliasedColumn(this, "PVVisit", getRealTable().getColumn("VisitValue")));
        addColumn(pvColumn);
        pvColumn.setFk(new LookupForeignKey("ParticipantVisit")
        {
            public TableInfo getLookupTableInfo()
            {
                return new ParticipantVisitTable(_schema, null);
            }
        });

        addWrapColumn(_rootTable.getColumn("TotalVolume"));
        addWrapColumn(_rootTable.getColumn("AvailableVolume"));
        addWrapColumn(_rootTable.getColumn("VolumeUnits"));
        ColumnInfo primaryTypeColumn = new AliasedColumn(this, "PrimaryType", _rootTable.getColumn("PrimaryTypeId"));
        primaryTypeColumn.setFk(new LookupForeignKey("ScharpId")
        {
            public TableInfo getLookupTableInfo()
            {
                return new PrimaryTypeTable(_schema);
            }
        });
        addColumn(primaryTypeColumn);
        ColumnInfo additiveTypeColumn = new AliasedColumn(this, "AdditiveType", _rootTable.getColumn("AdditiveTypeId"));
        additiveTypeColumn.setFk(new LookupForeignKey("ScharpId")
        {
            public TableInfo getLookupTableInfo()
            {
                return new AdditiveTypeTable(_schema);
            }
        });
        addColumn(additiveTypeColumn);
        ColumnInfo derivativeTypeColumn = new AliasedColumn(this, "DerivativeType", _rootTable.getColumn("DerivativeTypeId"));
        derivativeTypeColumn.setFk(new LookupForeignKey("ScharpId")
        {
            public TableInfo getLookupTableInfo()
            {
                return new DerivativeTypeTable(_schema);
            }
        });
        addColumn(derivativeTypeColumn);

        ColumnInfo originatingSiteCol = new AliasedColumn(this, "Clinic", _rootTable.getColumn("OriginatingLocationId"));
        originatingSiteCol.setFk(new LookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                return new SiteTable(_schema);
            }
        });
        addColumn(originatingSiteCol);

        addWrapColumn(_rootTable.getColumn("SubAdditiveDerivative"));
        addWrapColumn(_rootTable.getColumn("VialCount"));
        addWrapColumn(_rootTable.getColumn("LockedInRequestCount"));
        addWrapColumn(_rootTable.getColumn("AtRepositoryCount"));
        addWrapColumn(_rootTable.getColumn("AvailableCount"));
        addWrapColumn(_rootTable.getColumn("DrawTimestamp"));
        addWrapColumn(_rootTable.getColumn("SalReceiptDate"));
        addWrapColumn(_rootTable.getColumn("ClassId"));
        addWrapColumn(_rootTable.getColumn("ProtocolNumber"));
        addWrapColumn(_rootTable.getColumn("SpecimenHash")).setIsHidden(true);

        // Create an ExprColumn to get the max *possible* comments for each specimen.  It's only the possible number
        // (rather than the actual number), because a specimennumber isn't sufficient to identify a row in the specimen
        // summary table; derivative and additive types are required as well.  We use this number so we know if additional
        // (more expensive) queries are required to check for actual comments in the DB for each row.
        SQLFragment sqlFrag = new SQLFragment("(SELECT CAST(COUNT(*) AS VARCHAR(5)) FROM " +
                StudySchema.getInstance().getTableInfoSpecimenComment() +
                " WHERE SpecimenHash = " + ExprColumn.STR_TABLE_ALIAS + ".SpecimenHash" +
                " AND Container = ?)");
        sqlFrag.add(getContainer().getId());
        //  Set this column type to string so that exports to excel correctly set the column type as string.
        // (We're using a custom display column to output the text of the comment in this col, even though
        // the SQL expression returns an integer.)
        ColumnInfo commentsCol = addColumn(new ExprColumn(this, "Comments", sqlFrag, Types.VARCHAR));
        commentsCol.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new CommentDisplayColumn(colInfo);
            }
        });
    }

    public static class CommentDisplayColumn extends DataColumn
    {
        public CommentDisplayColumn(ColumnInfo commentColumn)
        {
            super(commentColumn);
            setWidth("200px");
        }

        public boolean isFilterable()
        {
            return false;
        }

        public boolean isSortable()
        {
            return false;
        }

        public void addQueryColumns(Set<ColumnInfo> columns)
        {
            columns.add(getColumnInfo().getParentTable().getColumn("Comments"));
            columns.add(getColumnInfo().getParentTable().getColumn("SpecimenHash"));
        }

        private String getDisplayText(RenderContext ctx, String lineSeparator)
        {
            StringBuilder builder = new StringBuilder();
            String maxPossibleCount = (String) getValue(ctx);
            // the string compare below is a big of a hack, but it's cheaper than converting the string to a number and
            // equally effective.  The column type is string so that exports to excel correctly set the column type as string.
            if (maxPossibleCount != null && !"0".equals(maxPossibleCount))
            {
                try
                {
                    String specimenHash = (String) ctx.get("SpecimenHash");
                    SpecimenComment[] comments = SampleManager.getInstance().getSpecimenCommentForSpecimen(ctx.getContainer(), specimenHash);
                    if (comments != null && comments.length > 0)
                    {
                        Map<String, List<String>> commentToIds = new TreeMap<String, List<String>>();
                        for (SpecimenComment comment : comments)
                        {
                            List<String> ids = commentToIds.get(comment.getComment());
                            if (ids == null)
                            {
                                ids = new ArrayList<String>();
                                commentToIds.put(comment.getComment(), ids);
                            }
                            ids.add(comment.getGlobalUniqueId());
                        }
                        String tempSep = "";
                        for (Map.Entry<String, List<String>> entry : commentToIds.entrySet())
                        {
                            builder.append(tempSep);
                            builder.append(entry.getValue().size()).append(" vial");
                            if (entry.getValue().size() > 1)
                                builder.append("s");
                            builder.append(": ").append(entry.getKey());
                            tempSep = lineSeparator;
                        }
                    }
                }
                catch (SQLException e)
                {
                    throw new RuntimeSQLException(e);
                }
            }
            return builder.toString();
        }

        public Object getDisplayValue(RenderContext ctx)
        {
            return getDisplayText(ctx, ", ");
        }

        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            out.write(getDisplayText(ctx, "<br>"));
        }
    }

}
