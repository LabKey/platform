/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.publish.StudyPublishService;

import java.io.IOException;
import java.io.Writer;
import java.sql.SQLException;
import java.util.List;

/**
 * User: Karl Lum
 * Date: Oct 10, 2007
 */
public class PublishedRecordQueryView extends DatasetQueryView
{
    private final String _sourceLsid;
    private final int _publishSourceId;
    private final int _recordCount;
    @Nullable
    private final Dataset.PublishSource _publishSource;

    public PublishedRecordQueryView(UserSchema schema, DatasetQuerySettings settings, String sourceLsid,
                                    @Nullable Dataset.PublishSource source, int publishSourceId, int recordCount)
    {
        super(schema, settings, null);
        _sourceLsid = sourceLsid;
        _publishSource = source;
        _publishSourceId = publishSourceId;
        _recordCount = recordCount;

        if (_sourceLsid != null)
        {
            SimpleFilter filter = new SimpleFilter();

            filter.addCondition(FieldKey.fromParts("SourceLSID"), _sourceLsid, CompareType.EQUAL);
            getSettings().getBaseFilter().addAllClauses(filter);
        }
    }

    @Override
    protected TableInfo createTable()
    {
        TableInfo table = getSchema().getTable(getSettings().getQueryName(), getContainerFilter(), true, true);
        var sourceLsidCol = table.getColumn("SourceLSID");
        if (sourceLsidCol != null)
            ((BaseColumnInfo)sourceLsidCol).setHidden(false);
        table.setLocked(true);

        return table;
    }

    @Override
    protected DataRegion createDataRegion()
    {
        DataRegion rgn = new PublishedRecordDataRegion();
        configureDataRegion(rgn);
        return rgn;
    }

    private class PublishedRecordDataRegion extends DataRegion
    {
        private static final String MISSING_ROWS_MSG = "%s rows that were previously linked in this event have been recalled (or deleted)." +
                " The audit record(s) of the deleted rows can be found in the <a href=\"%s\">link to study history view</a>, or the" +
                " study dataset history view.";

        private int _count;

        @Override
        protected int renderTableContents(RenderContext ctx, Writer out, boolean showRecordSelectors, List<DisplayColumn> renderers) throws SQLException, IOException
        {
            _count = super.renderTableContents(ctx, out, showRecordSelectors, renderers);
            return _count;
        }

        @Override
        protected void renderFormEnd(RenderContext ctx, Writer out) throws IOException
        {
            super.renderFormEnd(ctx, out);
            if (_count < _recordCount)
            {
                Container c = _publishSource == null ? null : _publishSource.resolveSourceLsidContainer(_sourceLsid, null);
                if (c != null)
                {
                    if (_count == 0)
                        out.write(String.format(MISSING_ROWS_MSG, "All", StudyPublishService.get().getPublishHistory(c, _publishSource, _publishSourceId)));
                    else
                        out.write(String.format(MISSING_ROWS_MSG, _recordCount - _count, StudyPublishService.get().getPublishHistory(c, _publishSource, _publishSourceId)));
                }
            }
        }
   }
}

