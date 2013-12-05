/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

package org.labkey.experiment.controllers.exp;

import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleDisplayColumn;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.GridView;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.springframework.validation.BindException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: jeckels
* Date: Dec 19, 2007
*/
public class ProtocolSuccessorPredecessorView extends GridView
{
    private Map<String, ExpProtocol> _protocolCache = new HashMap<>();

    protected ProtocolSuccessorPredecessorView(String parentProtocolLSID, int actionSequence, Container c, String lsidSelectColumn, String sequenceSelectColumn, String filterColumn, String title)
    {
        super(new DataRegion(), (BindException)null);
        TableInfo ti = ExperimentServiceImpl.get().getTinfoProtocolActionPredecessorLSIDView();
        List<ColumnInfo> cols = ti.getColumns(lsidSelectColumn, sequenceSelectColumn);
        getDataRegion().setColumns(cols);
        getDataRegion().getDisplayColumn(lsidSelectColumn).setVisible(false);
        getDataRegion().addDisplayColumn(0, new ProtocolNameDisplayColumn(lsidSelectColumn, _protocolCache, "Name"));
        getDataRegion().addDisplayColumn(new ProtocolDescriptionDisplayColumn(lsidSelectColumn, _protocolCache));
        getDataRegion().getDisplayColumn(0).setURL(new ActionURL(ExperimentController.ProtocolPredecessorsAction.class, c) + "?ParentLSID=" + parentProtocolLSID + "&Sequence=${" + sequenceSelectColumn + "}");
        getDataRegion().getDisplayColumn(0).setTextAlign("left");

        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("ParentProtocolLSID"), parentProtocolLSID, CompareType.EQUAL);
        filter.addCondition(filterColumn, actionSequence, CompareType.EQUAL);
        setFilter(filter);

        setSort(new Sort("ActionSequence"));

        getDataRegion().setButtonBar(ButtonBar.BUTTON_BAR_EMPTY);

        setTitle(title);
    }

    private static class ProtocolNameDisplayColumn extends AbstractProtocolDisplayColumn
    {
        public ProtocolNameDisplayColumn(String lsidColumnName, Map<String, ExpProtocol> protocolCache, String columnName)
        {
            super(lsidColumnName, protocolCache);
            setCaption(columnName);
            setWidth("250");
        }

        protected String getProtocolValue(ExpProtocol protocol)
        {
            return PageFlowUtil.filter(protocol.getName());
        }
    }

    private static class ProtocolDescriptionDisplayColumn extends AbstractProtocolDisplayColumn
    {
        public ProtocolDescriptionDisplayColumn(String lsidColumnName, Map<String, ExpProtocol> protocolCache)
        {
            super(lsidColumnName, protocolCache);
            setCaption("Description");
            setWidth("50%");
        }

        protected String getProtocolValue(ExpProtocol protocol)
        {
            return PageFlowUtil.filter(protocol.getProtocolDescription());
        }
    }

    private abstract static class AbstractProtocolDisplayColumn extends SimpleDisplayColumn
    {
        private final String _lsidColumnName;
        private final Map<String, ExpProtocol> _protocolCache;

        public AbstractProtocolDisplayColumn(String lsidColumnName, Map<String, ExpProtocol> protocolCache)
        {
            _lsidColumnName = lsidColumnName;
            _protocolCache = protocolCache;
        }

        protected ExpProtocol getProtocol(RenderContext ctx)
        {
            String lsid = (String) ctx.get(_lsidColumnName);
            ExpProtocol protocol = _protocolCache.get(lsid);
            if (protocol == null)
            {
                protocol = ExperimentService.get().getExpProtocol(lsid);
                _protocolCache.put(lsid, protocol);
            }
            return protocol;
        }

        public String getValue(RenderContext ctx)
        {
            ExpProtocol protocol = getProtocol(ctx);
            if (protocol != null)
            {
                return getProtocolValue(protocol);
            }
            else
            {
                return "(Unknown)";
            }
        }

        protected abstract String getProtocolValue(ExpProtocol protocol);
    }


}
