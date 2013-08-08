/*
 * Copyright (c) 2009-2013 LabKey Corporation
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

package org.labkey.cbcassay;

import org.labkey.api.data.*;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.study.assay.AbstractTsvAssayProvider;
import org.labkey.api.study.assay.AssayTableMetadata;
import org.labkey.api.study.query.ResultsQueryView;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;
import org.labkey.cbcassay.data.CBCDataDisplayColumn;
import org.labkey.cbcassay.data.CBCDataProperty;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.ListIterator;

/**
 * User: kevink
 * Date: Mar 20, 2009
 */
public final class CBCResultsQueryView extends ResultsQueryView
{
    public CBCResultsQueryView(CBCAssayProvider provider, ExpProtocol protocol, ViewContext context, QuerySettings settings)
    {
        super(protocol, context, settings);
        setShowInsertNewButton(false);
        setShowUpdateColumn(true);
    }

    public DataView createDataView()
    {
        DataView view = super.createDataView();
        view.getDataRegion().setRecordSelectorValueColumns(AbstractTsvAssayProvider.ROW_ID_COLUMN_NAME);
        return view;
    }

    @Override
    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        super.populateButtonBar(view, bar);

        if (canEdit())
        {
            ActionURL url = getViewContext().cloneActionURL();
            url.addParameter(ActionURL.Param.returnUrl, url.getLocalURIString());
            url.setAction(CBCAssayController.EditResultsAction.class);
            ActionButton editButton = new ActionButton("Edit", url);
            bar.add(editButton);
        }
    }

    protected boolean canEdit()
    {
        return getUser().isSiteAdmin() && (getViewContext().hasPermission(UpdatePermission.class) || getViewContext().hasPermission(DeletePermission.class));
    }

    protected boolean canDelete()
    {
        return super.canDelete();
    }

    @Override
    public List<DisplayColumn> getDisplayColumns()
    {
        List<DisplayColumn> columns = super.getDisplayColumns();
        wrapDisplayColumns(_protocol, getViewContext(), columns, false);
        return columns;
    }

    static void wrapDisplayColumns(ExpProtocol protocol, ViewContext context, List<DisplayColumn> columns, boolean editable)
    {
        CBCAssayProvider provider = CBCAssayManager.get().getProvider();
        AssayTableMetadata assayTableMeta = provider.getTableMetadata(protocol);
        FieldKey sampleIdKey = assayTableMeta.getParticipantIDFieldKey();
        FieldKey propertiesKey = FieldKey.fromString("Properties");
        Container container = context.getContainer();

        FieldKey oldSampleIdKey = new FieldKey(propertiesKey, sampleIdKey.getName());

        ListIterator<DisplayColumn> iter = columns.listIterator();
        while (iter.hasNext())
        {
            DisplayColumn column = iter.next();

            // remove [edit] and [details] links
            if (editable && (column instanceof DetailsColumn || column instanceof UrlColumn))
                iter.remove();

            ColumnInfo info = column.getColumnInfo();
            if (info != null && info.getFieldKey() != null)
            {
                FieldKey fieldKey = info.getFieldKey();
                if (editable && (fieldKey.equals(sampleIdKey) || fieldKey.equals(oldSampleIdKey)))
                {
                    iter.set(new SampleIdInputColumn(provider, protocol, info));
                }
                else if (fieldKey.getParent() == null || propertiesKey.equals(fieldKey.getParent()))
                {
                    PropertyDescriptor pd = OntologyManager.getPropertyDescriptor(info.getPropertyURI(), container);
                    if (pd != null)
                    {
                        // XXX: check it has min/max/units properties
                        CBCDataProperty dataprop = new CBCDataProperty(pd);
                        iter.set(new CBCDataDisplayColumn(column, dataprop));
                    }
                }
            }
        }
    }

    public static class SampleIdInputColumn extends DisplayColumnDecorator
    {
        CBCAssayProvider _provider;
        ColumnInfo _columnInfo;
        private String _resultRowIdFieldKey;

        public SampleIdInputColumn(CBCAssayProvider provider, ExpProtocol protocol, ColumnInfo columnInfo)
        {
            super(columnInfo.getRenderer());
            _provider = provider;
            _columnInfo = columnInfo;
            _resultRowIdFieldKey = _provider.getTableMetadata(protocol).getResultRowIdFieldKey().toString();
        }

        @Override
        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            out.write("<input type=\"hidden\" name=\"objectId\" value=\"" + ctx.get(_resultRowIdFieldKey) + "\"/>");
            out.write("<input type=\"text\" name=\"sampleId\" value=\"" + PageFlowUtil.filter(getValue(ctx)) + "\"/>");
        }
    }
}
