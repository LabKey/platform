/*
 * Copyright (c) 2006-2009 LabKey Corporation
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
import org.labkey.api.query.*;

import java.sql.Types;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.io.Writer;
import java.io.IOException;
import java.util.*;

public class SpecimenSummaryTable extends BaseStudyTable
{
    public SpecimenSummaryTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoSpecimenSummary());
        addWrapParticipantColumn("PTID").setKeyField(true);
        addWrapColumn(_rootTable.getColumn("Container")).setFk(new ContainerForeignKey());

        addSpecimenVisitColumn(_schema.getStudy().isDateBased());

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
        addWrapTypeColumn("PrimaryType", "PrimaryTypeId");
        addWrapTypeColumn("DerivativeType", "DerivativeTypeId");
        addWrapTypeColumn("AdditiveType", "AdditiveTypeId");
        addWrapTypeColumn("DerivativeType2", "DerivativeTypeId2");
        addWrapLocationColumn("Clinic", "OriginatingLocationId");
        addWrapColumn(_rootTable.getColumn("SubAdditiveDerivative"));
        addWrapColumn(_rootTable.getColumn("VialCount"));
        addWrapColumn(_rootTable.getColumn("LockedInRequestCount"));
        addWrapColumn(_rootTable.getColumn("AtRepositoryCount"));
        addWrapColumn(_rootTable.getColumn("AvailableCount"));
        addWrapColumn(_rootTable.getColumn("ExpectedAvailableCount"));
        addWrapColumn(_rootTable.getColumn("DrawTimestamp"));
        addWrapColumn(_rootTable.getColumn("SalReceiptDate"));
        addWrapColumn(_rootTable.getColumn("ClassId"));
        addWrapColumn(_rootTable.getColumn("ProtocolNumber"));
        addWrapColumn(_rootTable.getColumn("SpecimenHash")).setHidden(true);

        // Create an ExprColumn to get the max *possible* comments for each specimen.  It's only the possible number
        // (rather than the actual number), because a specimennumber isn't sufficient to identify a row in the specimen
        // summary table; derivative and additive types are required as well.  We use this number so we know if additional
        // (more expensive) queries are required to check for actual comments in the DB for each row.
        SQLFragment sqlFragComments = new SQLFragment("(SELECT CAST(COUNT(*) AS VARCHAR(5)) FROM " +
                StudySchema.getInstance().getTableInfoSpecimenComment() +
                " WHERE SpecimenHash = " + ExprColumn.STR_TABLE_ALIAS + ".SpecimenHash" +
                " AND Container = ?)");
        sqlFragComments.add(getContainer().getId());
        //  Set this column type to string so that exports to excel correctly set the column type as string.
        // (We're using a custom display column to output the text of the comment in this col, even though
        // the SQL expression returns an integer.)
        ColumnInfo commentsCol = addColumn(new ExprColumn(this, "Comments", sqlFragComments, Types.VARCHAR));
        commentsCol.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new CommentDisplayColumn(colInfo);
            }
        });
        // use sql aggregates to 'OR' together the conflict bits of the vials associated with this specimen hash:
        SQLFragment sqlFragConflicts = new SQLFragment("(SELECT CASE WHEN COUNT(QualityControlFlag) = 0 OR " +
                "SUM(CAST(QualityControlFlag AS INT)) = 0 THEN ? ELSE ? END FROM " +
                StudySchema.getInstance().getTableInfoSpecimenComment() +
                " WHERE SpecimenHash = " + ExprColumn.STR_TABLE_ALIAS + ".SpecimenHash" +
                " AND Container = ?)");
        sqlFragConflicts.add(Boolean.FALSE);
        sqlFragConflicts.add(Boolean.TRUE);
        sqlFragConflicts.add(getContainer().getId());
        //  Set this column type to string so that exports to excel correctly set the column type as string.
        // (We're using a custom display column to output the text of the comment in this col, even though
        // the SQL expression returns an integer.)
        addColumn(new ExprColumn(this, "QualityControlFlag", sqlFragConflicts, Types.BOOLEAN));
    }

    public static class CommentDisplayColumn extends DataColumn
    {
        private ColumnInfo _specimenHashColumn;

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
            FieldKey me = getBoundColumn().getFieldKey();
            FieldKey specimenHashKey = new FieldKey(me.getParent(), "SpecimenHash");
            // select the base 'comments' column (our bound column is an exprcolumn that doesn't simply select the base value):
            FieldKey commentsHashKey = new FieldKey(me.getParent(), "Comments");
            Set<FieldKey> fieldKeys = new HashSet<FieldKey>();
            fieldKeys.add(specimenHashKey);
            fieldKeys.add(commentsHashKey);
            Map<FieldKey, ColumnInfo> requiredColumns = QueryService.get().getColumns(getBoundColumn().getParentTable(), fieldKeys);
            _specimenHashColumn = requiredColumns.get(specimenHashKey);
            if (_specimenHashColumn != null)
                columns.add(_specimenHashColumn);
            ColumnInfo col = requiredColumns.get(commentsHashKey);
            if (col != null)
                columns.add(col);
        }

        private Map<String, String> _commentCache;

        private void addComments(Container container, Set<String> hashes, Map<String, List<SpecimenComment>> hashToComments) throws SQLException
        {
            SpecimenComment[] comments = SampleManager.getInstance().getSpecimenCommentForSpecimens(container, hashes);
            for (SpecimenComment comment : comments)
            {
                List<SpecimenComment> commentList = hashToComments.get(comment.getSpecimenHash());
                if (commentList == null)
                {
                    commentList = new ArrayList<SpecimenComment>();
                    hashToComments.put(comment.getSpecimenHash(), commentList);
                }
                commentList.add(comment);
            }

        }

        private Map<String, String> getCommentCache(RenderContext ctx, String lineSeparator) throws SQLException
        {
            ResultSet rs = ctx.getResultSet();
            if (_commentCache == null && rs instanceof Table.TableResultSet)
            {
                Table.TableResultSet tableRs = (Table.TableResultSet) rs;
                Set<String> hashes = new HashSet<String>();
                Map<String, List<SpecimenComment>> hashToComments = new HashMap<String, List<SpecimenComment>>();
                for (Iterator<Map> it = tableRs.iterator(); it.hasNext(); )
                {
                    Map<String, Object> row = (Map<String, Object>) it.next();
                    String maxPossibleCount = (String) row.get("Comments");
                    if (maxPossibleCount != null && !"0".equals(maxPossibleCount))
                        hashes.add((String) row.get("SpecimenHash"));

                    if (hashes.size() >= 1000)
                    {
                        addComments(ctx.getContainer(), hashes, hashToComments);
                        hashes.clear();
                    }
                }
                addComments(ctx.getContainer(), hashes, hashToComments);

                _commentCache = new HashMap<String, String>();
                for (Map.Entry<String, List<SpecimenComment>> entry : hashToComments.entrySet())
                {
                    List<SpecimenComment> commentList = entry.getValue();
                    String formatted = formatCommentText(commentList.toArray(new SpecimenComment[commentList.size()]), lineSeparator);
                    _commentCache.put(entry.getKey(), formatted);
                }
            }
            return _commentCache;
        }

        private String formatCommentText(SpecimenComment[] comments, String lineSeparator)
        {
            StringBuilder builder = new StringBuilder();
            if (comments != null && comments.length > 0)
            {
                Map<String, List<String>> commentToIds = new TreeMap<String, List<String>>();
                for (SpecimenComment comment : comments)
                {
                    if (comment.getComment() != null)
                    {
                        List<String> ids = commentToIds.get(comment.getComment());
                        if (ids == null)
                        {
                            ids = new ArrayList<String>();
                            commentToIds.put(comment.getComment(), ids);
                        }
                        ids.add(comment.getGlobalUniqueId());
                    }
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
            return builder.toString();
        }

        private String getCommentText(RenderContext ctx, String specimenHash, String lineSeparator) throws SQLException
        {
            Map<String, String> commentCache = getCommentCache(ctx, lineSeparator);
            if (commentCache != null)
                return commentCache.get(specimenHash);
            else
            {
                // we must not have a cached resultset, so we couldn't get the full set of comments efficiently; we'll select
                // comments for each row:
                SpecimenComment[] comments = SampleManager.getInstance().getSpecimenCommentForSpecimen(ctx.getContainer(), specimenHash);
                return formatCommentText(comments, lineSeparator);
            }
        }

        private String getDisplayText(RenderContext ctx, String lineSeparator)
        {
            if (_specimenHashColumn == null)
                return "ERROR: SpecimenHash column must be added to query to retrive comment information.";

            String maxPossibleCount = (String) getValue(ctx);
            // the string compare below is a big of a hack, but it's cheaper than converting the string to a number and
            // equally effective.  The column type is string so that exports to excel correctly set the column type as string.
            if (maxPossibleCount != null && !"0".equals(maxPossibleCount))
            {
                try
                {
                    return getCommentText(ctx, ctx.getResultSet().getString("SpecimenHash"), lineSeparator);
                }
                catch (SQLException e)
                {
                    throw new RuntimeSQLException(e);
                }
            }
            return "";
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
