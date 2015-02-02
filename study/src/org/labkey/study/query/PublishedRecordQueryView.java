/*
 * Copyright (c) 2007-2015 LabKey Corporation
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

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.study.assay.AssayPublishService;
import org.labkey.study.model.QCStateSet;
import org.labkey.study.model.StudyManager;

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
    private String _sourceLsid;
    private int _protocolId;
    private int _recordCount;

    public PublishedRecordQueryView(UserSchema schema, DatasetQuerySettings settings, String sourceLsid, int protocolId, int recordCount)
    {
        super(schema, settings, null);
        _sourceLsid = sourceLsid;
        _protocolId = protocolId;
        _recordCount = recordCount;

        setQcStateSet(getStateSet(schema.getContainer()));
        if (_sourceLsid != null)
        {
            SimpleFilter filter = new SimpleFilter();

            filter.addCondition(FieldKey.fromParts("SourceLSID"), _sourceLsid, CompareType.EQUAL);
            getSettings().getBaseFilter().addAllClauses(filter);
        }
    }

    private static QCStateSet getStateSet(Container container)
    {
        if (StudyManager.getInstance().showQCStates(container))
            return QCStateSet.getAllStates(container);
        else
            return null;
    }

    protected TableInfo createTable()
    {
        TableInfo table = super.createTable();
        ColumnInfo sourceLsidCol = table.getColumn("SourceLSID");
        if (sourceLsidCol != null)
            sourceLsidCol.setHidden(false);
        return table;
    }

    protected DataRegion createDataRegion()
    {
        DataRegion rgn = new PublishedRecordDataRegion(_recordCount, _protocolId, _sourceLsid);
        configureDataRegion(rgn);
        return rgn;
    }

    private static class PublishedRecordDataRegion extends DataRegion
    {
        private static final String MISSING_ROWS_MSG = "%s rows that were previously copied in this event have been recalled (or deleted)." +
                " The audit record(s) of the deleted rows can be found in the <a href=\"%s\">copy-to-study history view</a>, or the" +
                " study dataset history view.";

        private int _recordCount;
        private int _protocolId;
        private int _count;
        private String _sourceLsid;

        public PublishedRecordDataRegion(int recordCount, int protocolId, String sourceLsid)
        {
            _recordCount = recordCount;
            _protocolId = protocolId;
            _sourceLsid = sourceLsid;
        }

        @Override
        protected int renderTableContents(RenderContext ctx, Writer out, boolean showRecordSelectors, List<DisplayColumn> renderers) throws SQLException, IOException
        {
            _count = super.renderTableContents(ctx, out, showRecordSelectors, renderers);
            return _count;
        }

        protected void renderFormEnd(RenderContext ctx, Writer out) throws IOException
        {
            super.renderFormEnd(ctx, out);
            if (_count < _recordCount)
            {
                ExpRun expRun = ExperimentService.get().getExpRun(_sourceLsid);
                Container c = ctx.getContainer();
                if (expRun != null && expRun.getContainer() != null)
                    c = expRun.getContainer();
                ExpProtocol protocol = ExperimentService.get().getExpProtocol(_protocolId);
                if (_count == 0)
                    out.write(String.format(MISSING_ROWS_MSG, "All", AssayPublishService.get().getPublishHistory(c, protocol)));
                else

                    out.write(String.format(MISSING_ROWS_MSG, _recordCount - _count, AssayPublishService.get().getPublishHistory(c, protocol)));
            }
        }
   }
}

