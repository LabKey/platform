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

import org.labkey.api.data.*;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.*;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;
import org.labkey.api.study.DataSet;
import org.labkey.api.security.User;
import org.labkey.study.model.QCState;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.StudyManager;

import java.util.*;

public class DataSetTable extends FilteredTable
{
    public static final String QCSTATE_ID_COLNAME = "QCState";
    public static final String QCSTATE_LABEL_COLNAME = "QCStateLabel";
    StudyQuerySchema _schema;
    DataSetDefinition _dsd;
    TableInfo _fromTable;

    public DataSetTable(StudyQuerySchema schema, DataSetDefinition dsd)
    {
        super(dsd.getTableInfo(schema.getUser(), schema.getMustCheckPermissions(), false));
        _schema = schema;
        _dsd = dsd;
        ColumnInfo pvColumn = new ParticipantVisitColumn(
                "ParticipantVisit",
                new AliasedColumn(this, "PVParticipant", getRealTable().getColumn("ParticipantId")),
                new AliasedColumn(this, "PVVisit", getRealTable().getColumn("SequenceNum")));
        addColumn(pvColumn);
        pvColumn.setFk(new LookupForeignKey("ParticipantVisit")
        {
            public TableInfo getLookupTableInfo()
            {
                return new ParticipantVisitTable(_schema, null);
            }
        });

        List<FieldKey> defaultVisibleCols = new ArrayList<FieldKey>();

        HashSet<String> standardURIs = new HashSet<String>();
        for (PropertyDescriptor pd :  DataSetDefinition.getStandardPropertiesSet())
            standardURIs.add(pd.getPropertyURI());

        for (ColumnInfo baseColumn : getRealTable().getColumns())
        {
            String name = baseColumn.getName();
            if ("ParticipantId".equalsIgnoreCase(name))
            {
                ColumnInfo column = new AliasedColumn(this, "ParticipantId", baseColumn);
                //column.setFk(new QueryForeignKey(_schema, "Participant", "RowId", "RowId"));

                column.setFk(new QueryForeignKey(_schema, "Participant", "ParticipantId", "ParticipantId")
                {
                    public StringExpression getURL(ColumnInfo parent) {
                        ActionURL base = new ActionURL(StudyController.ParticipantAction.class, _schema.getContainer());
                        base.addParameter(DataSetDefinition.DATASETKEY, Integer.toString(_dsd.getDataSetId()));
                        return new DetailsURL(base, "participantId", parent.getFieldKey());
                    }
                });
                addColumn(column);
                if (isVisibleByDefault(column))
                    defaultVisibleCols.add(FieldKey.fromParts(column.getName()));
            }
            else if (getRealTable().getColumn(baseColumn.getName() + OORDisplayColumnFactory.OORINDICATOR_COLUMN_SUFFIX) != null)
            {
                // If this is the value column that goes with an OORIndicator, add the special OOR options
                OORDisplayColumnFactory.addOORColumns(this, baseColumn, getRealTable().getColumn(baseColumn.getName() +
                        OORDisplayColumnFactory.OORINDICATOR_COLUMN_SUFFIX));
                if (isVisibleByDefault(baseColumn))
                    defaultVisibleCols.add(FieldKey.fromParts(baseColumn.getName()));
            }
            else if (baseColumn.getName().toLowerCase().endsWith(OORDisplayColumnFactory.OORINDICATOR_COLUMN_SUFFIX.toLowerCase()) &&
                    getRealTable().getColumn(baseColumn.getName().substring(0, baseColumn.getName().length() - OORDisplayColumnFactory.OORINDICATOR_COLUMN_SUFFIX.length())) != null)
            {
                // If this is an OORIndicator and there's a matching value column in the same table, don't add this column
            }
            else if (baseColumn.getName().equalsIgnoreCase("SequenceNum") && _schema.getStudy().isDateBased())
            {
                addWrapColumn(baseColumn);
                //Don't add to visible cols...
            }
            else if (baseColumn.getName().equalsIgnoreCase(QCSTATE_ID_COLNAME))
            {
                ColumnInfo qcStateColumn = new AliasedColumn(this, QCSTATE_ID_COLNAME, baseColumn);
                qcStateColumn.setFk(new LookupForeignKey("RowId")
                    {
                        public TableInfo getLookupTableInfo()
                        {
                            return new QCStateTable(_schema);
                        }
                    });

                qcStateColumn.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    public DisplayColumn createRenderer(ColumnInfo colInfo)
                    {
                        return new QCStateDisplayColumn(colInfo);
                    }
                });

                addColumn(qcStateColumn);
                if (StudyManager.getInstance().showQCStates(_schema.getContainer()))
                    defaultVisibleCols.add(FieldKey.fromParts(baseColumn.getName()));
                else
                    qcStateColumn.setHidden(true);
            }
            else
            {
                ColumnInfo col = addWrapColumn(baseColumn);

                // When copying a column, the hidden bit is not propagated, so we need to do it manually
                if (baseColumn.isHidden())
                    col.setHidden(true);
                
                String propertyURI = col.getPropertyURI();
                if (null != propertyURI && !standardURIs.contains(propertyURI))
                {
                    PropertyDescriptor pd = OntologyManager.getPropertyDescriptor(propertyURI, schema.getContainer());
                    if (null != pd && pd.getLookupQuery() != null)
                        col.setFk(new PdLookupForeignKey(schema.getUser(), pd));
                    
                    if (pd != null && pd.getPropertyType() == PropertyType.MULTI_LINE)
                    {
                        col.setDisplayColumnFactory(new DisplayColumnFactory() {
                            public DisplayColumn createRenderer(ColumnInfo colInfo)
                            {
                                DataColumn dc = new DataColumn(colInfo);
                                dc.setPreserveNewlines(true);
                                return dc;
                            }
                        });
                    }
                }
                if (isVisibleByDefault(col))
                    defaultVisibleCols.add(FieldKey.fromParts(col.getName()));
            }
        }
        ColumnInfo lsidColumn = getColumn("LSID");
        lsidColumn.setHidden(true);
        lsidColumn.setKeyField(true);
        getColumn("SourceLSID").setHidden(true);
        setDefaultVisibleColumns(defaultVisibleCols);
    }

    private class QCStateDisplayColumn extends DataColumn
    {
        private Map<Integer, QCState> _qcStateCache;
        public QCStateDisplayColumn(ColumnInfo col)
        {
            super(col);
        }

        public String getFormattedValue(RenderContext ctx)
        {
            Object value = getValue(ctx);
            StringBuilder formattedValue = new StringBuilder(super.getFormattedValue(ctx));
            if (value != null && value instanceof Integer)
            {
                QCState state = getStateCache(ctx).get((Integer) value);
                if (state != null && state.getDescription() != null)
                    formattedValue.append(PageFlowUtil.helpPopup("QC State " + state.getLabel(), state.getDescription()));
            }
            return formattedValue.toString();
        }

        private Map<Integer, QCState> getStateCache(RenderContext ctx)
        {
            if (_qcStateCache == null)
            {
                _qcStateCache = new HashMap<Integer, QCState>();
                QCState[] states = StudyManager.getInstance().getQCStates(ctx.getContainer());
                for (QCState state : states)
                    _qcStateCache.put(state.getRowId(), state);
            }
            return _qcStateCache;
        }
    }

    private static final Set<String> defaultHiddenCols = new CaseInsensitiveHashSet("VisitRowId", "Created", "Modified", "lsid");
    private boolean isVisibleByDefault(ColumnInfo col)
    {
        if (_dsd.isKeyPropertyManaged() && col.getName().equals(_dsd.getKeyPropertyName()))
            return false;
        return (!col.isHidden() && !col.isUnselectable() && !defaultHiddenCols.contains(col.getName()));
    }


    protected TableInfo getFromTable()
    {
        if (_fromTable == null)
        {
            _fromTable = _dsd.getTableInfo(_schema.getUser(), _schema.getMustCheckPermissions(), true);
        }
        return _fromTable;
    }

    public DataSet getDatasetDefinition()
    {
        return _dsd;
    }

    /**
     * In order to discourage the user from selecting data from deeply nested datasets, we hide
     * the "ParticipantID" and "ParticipantVisit" columns when the user could just as easily find
     * the same data further up the tree.
     */
    public void hideParticipantLookups()
    {
        getColumn("ParticipantID").setHidden(true);
        getColumn("ParticipantVisit").setHidden(true);
    }

    @Override
    public boolean isMetadataOverrideable()
    {
        return false;
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        User user = _schema.getUser();
        DataSet def = getDatasetDefinition();
        if (!def.canWrite(user))
            throw new RuntimeException("User is not allowed to update dataset: " + getName());
        return new DatasetUpdateService(def.getDataSetId());
    }
}
