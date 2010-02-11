/*
 * Copyright (c) 2009-2010 LabKey Corporation
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

import org.labkey.api.study.assay.AssayTableMetadata;
import org.labkey.api.study.query.ResultsQueryView;
import org.labkey.api.study.assay.AbstractAssayProvider;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.FieldKey;
import org.labkey.api.data.*;
import org.labkey.api.security.ACL;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.cbcassay.data.CBCDataProperty;
import org.labkey.cbcassay.data.CBCDataDisplayColumn;
import org.labkey.cbcassay.data.DisplayColumnDecorator;

import java.util.List;
import java.util.ListIterator;
import java.io.Writer;
import java.io.IOException;

/**
 * User: kevink
 * Date: Mar 20, 2009
 */
public final class CBCResultsQueryView extends ResultsQueryView
{
    public CBCResultsQueryView(ExpProtocol protocol, ViewContext context, QuerySettings settings)
    {
        super(protocol, context, settings);
        setShowInsertNewButton(false);
        setShowUpdateColumn(true);
    }

    public DataView createDataView()
    {
        DataView view = super.createDataView();
        view.getDataRegion().setRecordSelectorValueColumns("ObjectId");
        return view;
    }

    @Override
    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        super.populateButtonBar(view, bar);

        if (canEdit())
        {
            ActionURL url = getViewContext().cloneActionURL();
            url.addParameter("returnURL", url.getLocalURIString());
            url.setAction(CBCAssayController.EditResultsAction.class);
            ActionButton editButton = new ActionButton("Edit", url);
            bar.add(editButton);
        }
    }

    protected boolean canEdit()
    {
        return getUser().isAdministrator() && getViewContext().hasPermission(ACL.PERM_UPDATE | ACL.PERM_DELETE);
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
        AssayTableMetadata assayTableMeta = provider.getTableMetadata();
        FieldKey sampleIdKey = assayTableMeta.getParticipantIDFieldKey();
        FieldKey propertiesKey = FieldKey.fromString("Properties");
        Container container = context.getContainer();
        String dataDomainURI = AbstractAssayProvider.getDomainURIForPrefix(protocol, ExpProtocol.ASSAY_DOMAIN_DATA);

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
                if (editable && fieldKey.equals(sampleIdKey))
                {
                    iter.set(new SampleIdInputColumn(provider, info));
                }
                else if (propertiesKey.equals(fieldKey.getParent()))
                {
                    // XXX: this is nasty, but I couldn't find a better way to get the ColumnInfo's name
                    // XXX: ColumnInfo.getPropertyURI() doesn't contain the domainURI#name as I expected.
                    String name = fieldKey.getName();
                    String propertyUri = dataDomainURI + "#" + name;

                    PropertyDescriptor pd = OntologyManager.getPropertyDescriptor(propertyUri, container);
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

        public SampleIdInputColumn(CBCAssayProvider provider, ColumnInfo columnInfo)
        {
            super(columnInfo.getRenderer());
            _provider = provider;
            _columnInfo = columnInfo;
            _resultRowIdFieldKey = _provider.getTableMetadata().getResultRowIdFieldKey().toString();
        }

        @Override
        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            out.write("<input type=\"hidden\" name=\"objectId\" value=\"" + ctx.get(_resultRowIdFieldKey) + "\"/>");
            out.write("<input type=\"text\" name=\"sampleId\" value=\"" + PageFlowUtil.filter(getValue(ctx)) + "\">");
        }
    }

}
