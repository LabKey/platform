/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.api.study.query;

import org.labkey.api.data.*;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.study.ParticipantVisit;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.assay.*;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.DateUtil;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.reports.ReportService;
import org.apache.commons.beanutils.ConversionException;

import java.io.IOException;
import java.io.Writer;
import java.sql.SQLException;
import java.util.*;

/**
 * User: brittp
 * Date: Jul 30, 2007
 * Time: 10:07:07 AM
 */
public class PublishRunDataQueryView extends RunDataQueryView
{
    private SimpleFilter _filter;
    private List<ActionButton> _buttons;
    private final Container _targetStudyContainer;
    private final TimepointType _timepointType;
    private Map<Object, String> _reshowVisits;
    private Map<Object, String> _reshowPtids;

    public PublishRunDataQueryView(ExpProtocol protocol, ViewContext context, QuerySettings settings,
                                   List<Integer> objectIds, Container targetStudyContainer)
    {
        this(protocol, context, settings, objectIds, targetStudyContainer, null, null);
    }

    public PublishRunDataQueryView(ExpProtocol protocol, ViewContext context, QuerySettings settings,
                                   List<Integer> objectIds, Container targetStudyContainer,
                                   Map<Object, String> reshowVisits, Map<Object, String> reshowPtids)
    {
        super(protocol, context, settings);
        _targetStudyContainer = targetStudyContainer;
        _timepointType = AssayPublishService.get().getTimepointType(_targetStudyContainer);
        _filter = new SimpleFilter();
        AssayProvider provider = AssayService.get().getProvider(protocol);
        _filter.addInClause(provider.getDataRowIdFieldKey().toString(), objectIds);
        _reshowPtids = reshowPtids;
        _reshowVisits = reshowVisits;
        setViewItemFilter(ReportService.EMPTY_ITEM_LIST);
    }

    protected DataView createDataView()
    {
        DataView view = super.createDataView();
        view.getDataRegion().addHiddenFormField("targetStudy", _targetStudyContainer.getId());
        view.getDataRegion().addHiddenFormField("attemptPublish", "true");
        if (_filter != null)
        {
            SimpleFilter filter = (SimpleFilter) view.getRenderContext().getBaseFilter();
            if (filter != null)
                filter.addAllClauses(_filter);
            else
                filter = _filter;
            view.getRenderContext().setBaseFilter(filter);
        }

        if (_buttons != null)
        {
            ButtonBar bbar = new ButtonBar();
            for (ActionButton button : _buttons)
                bbar.add(button);
            view.getDataRegion().setButtonBar(bbar);
        }
        else
            view.getDataRegion().setButtonBar(ButtonBar.BUTTON_BAR_EMPTY);

        return view;
    }

    protected DataRegion createDataRegion()
    {
        DataRegion dr = super.createDataRegion();
        dr.setShowFilters(false);
        dr.setSortable(false);
        List<DisplayColumn> extraColumns = getExtraColumns();
        int idx = 0;
        for (DisplayColumn extra : extraColumns)
        {
            String captionMatchColName = null;
            for (Iterator<DisplayColumn> it = dr.getDisplayColumns().iterator(); it.hasNext() && captionMatchColName == null;)
            {
                DisplayColumn current = it.next();
                if (current.getCaption().equalsIgnoreCase(extra.getCaption()))
                    captionMatchColName = current.getName();
            }
            if (captionMatchColName != null)
                dr.removeColumns(captionMatchColName);
            dr.addDisplayColumn(idx++, extra);
        }
        Set<String> hiddenCols = getHiddenColumnCaptions();
        for (Iterator<DisplayColumn> it = dr.getDisplayColumns().iterator(); it.hasNext();)
        {
            DisplayColumn current = it.next();
            if (hiddenCols.contains(current.getCaption()))
                it.remove();
        }
        return dr;
    }

    protected Set<String> getHiddenColumnCaptions()
    {
        return Collections.singleton(AbstractAssayProvider.TARGET_STUDY_PROPERTY_CAPTION);
    }

    public class ResolverHelper
    {
        private final ColumnInfo _runIdCol;
        private final ColumnInfo _objectIdCol;
        private final ColumnInfo _ptidCol;
        private final ColumnInfo _visitIdCol;
        private final ColumnInfo _specimenIDCol;
        private Map<Integer, ParticipantVisitResolver> _resolvers = new HashMap<Integer, ParticipantVisitResolver>();

        public ResolverHelper(ColumnInfo runIdCol, ColumnInfo objectIdCol, ColumnInfo ptidCol, ColumnInfo visitIdCol, ColumnInfo specimenIDCol)
        {
            _runIdCol = runIdCol;
            _objectIdCol = objectIdCol;
            _ptidCol = ptidCol;
            _visitIdCol = visitIdCol;
            _specimenIDCol = specimenIDCol;
        }

        public ParticipantVisitResolver getResolver(RenderContext ctx) throws IOException
        {
            Integer runId = (Integer)_runIdCol.getValue(ctx);
            if (runId != null && !_resolvers.containsKey(runId))
            {
                ExpRun run = ExperimentService.get().getExpRun(runId.intValue());
                AssayProvider provider = AssayService.get().getProvider(_protocol);
                for (ObjectProperty property : run.getObjectProperties().values())
                {
                    if (AbstractAssayProvider.PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME.equals(property.getName()))
                    {
                        ParticipantVisitResolverType resolverType = AbstractAssayProvider.findType(property.getStringValue(), provider.getParticipantVisitResolverTypes());
                        try
                        {
                            _resolvers.put(runId, resolverType.createResolver(run, _targetStudyContainer, getUser()));

                        }
                        catch (ExperimentException e)
                        {
                            throw (IOException)new IOException().initCause(e);
                        }
                    }
                }
            }
            return _resolvers.get(runId);
        }

        private ParticipantVisit resolve(RenderContext ctx) throws IOException
        {
            ParticipantVisitResolver resolver = getResolver(ctx);
            if (resolver == null)
            {
                return null;
            }

            Object visitID = _visitIdCol == null ? null : _visitIdCol.getValue(ctx);
            String specimenID = _specimenIDCol == null ? null : (_specimenIDCol.getValue(ctx) == null ? null : _specimenIDCol.getValue(ctx).toString());
            String participantID = _ptidCol == null ? null : (_ptidCol.getValue(ctx) == null ? null : _ptidCol.getValue(ctx).toString());
            return resolver.resolve(specimenID, participantID, visitID instanceof Double ? (Double) visitID : null, visitID instanceof Date ? (Date)visitID : null);
        }

        public Object getVisitId(RenderContext ctx) throws IOException
        {
            if (_reshowVisits != null)
            {
                Object key = ctx.getRow().get(_objectIdCol.getName());
                return _reshowVisits.get(key);
            }
            Double result = _visitIdCol == null ? null : (_visitIdCol.getValue(ctx) instanceof Double ? (Double)_visitIdCol.getValue(ctx) : null);
            if (result == null)
            {
                ParticipantVisit pv = resolve(ctx);
                result = pv == null ? null : pv.getVisitID();
            }
            return result;
        }

        public String getParticipantId(RenderContext ctx) throws IOException
        {
            if (_reshowPtids != null)
            {
                Object key = ctx.getRow().get(_objectIdCol.getName());
                return _reshowPtids.get(key);
            }
            
            String result = _ptidCol == null ? null : (_ptidCol.getValue(ctx) == null ? null : _ptidCol.getValue(ctx).toString());
            if (result == null)
            {
                ParticipantVisit pv = resolve(ctx);
                result = pv == null ? null : pv.getParticipantID();
            }
            return result;
        }

        public boolean hasMatch(RenderContext ctx) throws IOException
        {
            try
            {
                ParticipantVisit pv;
                if (_timepointType == TimepointType.VISIT)
                {
                    Object visitIdObject = getVisitId(ctx);
                    Double visitId = null;
                    if (visitIdObject instanceof Number)
                    {
                        visitId = ((Number)visitIdObject).doubleValue();
                    }
                    else if (visitIdObject instanceof String)
                    {
                        try
                        {
                            visitId = Double.parseDouble((String)visitIdObject);
                        }
                        catch (NumberFormatException e) {}
                    }
                    pv = SpecimenService.get().getSampleInfo(_targetStudyContainer, getParticipantId(ctx), visitId);
                }
                else
                {
                    Object dateObject = getDate(ctx);
                    Date date = null;
                    if (dateObject instanceof Date)
                    {
                        date = (Date)dateObject;
                    }
                    else if (dateObject instanceof String)
                    {
                        try
                        {
                            date = new Date(DateUtil.parseDateTime((String)dateObject));
                        }
                        catch (ConversionException e) {}
                    }
                    pv = SpecimenService.get().getSampleInfo(_targetStudyContainer, getParticipantId(ctx), date);
                }
                return pv.getSpecimenID() != null;
            }
            catch (SQLException e)
            {
                throw (IOException)new IOException().initCause(e);
            }
        }

        public void addQueryColumns(Set<ColumnInfo> set)
        {
            if (_runIdCol != null) { set.add(_runIdCol); }
            if (_ptidCol != null) { set.add(_ptidCol); }
            if (_visitIdCol != null) { set.add(_visitIdCol); }
            if (_objectIdCol != null) { set.add(_objectIdCol); }
            if (_specimenIDCol != null) { set.add(_specimenIDCol); }
        }

        public Object getDate(RenderContext ctx) throws IOException
        {
            if (_reshowVisits != null)
            {
                Object key = ctx.getRow().get(_objectIdCol.getName());
                return _reshowVisits.get(key);
            }
            Date result = _visitIdCol == null ? null : (_visitIdCol.getValue(ctx) instanceof Date ? (Date)_visitIdCol.getValue(ctx) : null);
            if (result == null)
            {
                ParticipantVisit pv = resolve(ctx);
                result = pv == null ? null : pv.getDate();
            }
            return result;
        }
    }

    private abstract class InputColumn extends SimpleDisplayColumn
    {
        protected boolean _editable;
        protected String _formElementName;
        private String _completionBase;
        protected final ResolverHelper _resolverHelper;
        private boolean _completionJsNeeded = true;

        private InputColumn(String caption, boolean editable, String formElementName, String completionBase, ResolverHelper resolverHelper)
        {
            _editable = editable;
            _formElementName = formElementName;
            _completionBase = completionBase;
            _resolverHelper = resolverHelper;
            setCaption(caption);
        }

        public void addQueryColumns(Set<ColumnInfo> set)
        {
            super.addQueryColumns(set);
            _resolverHelper.addQueryColumns(set);
        }

        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            if (_editable)
            {
                if (_completionBase != null)
                {
                    if (_completionJsNeeded)
                    {
                        out.write("<script type=\"text/javascript\">LABKEY.requiresScript(\"completion.js\");</script>");
                        _completionJsNeeded = false;
                    }
                    out.write("<input type=\"text\" name=\"" + _formElementName + "\"\n" +
                            "value=\"" + PageFlowUtil.filter(getValue(ctx)) + "\"\n" +
                            "onKeyDown=\"return ctrlKeyCheck(event);\"\n" +
                            "onBlur=\"hideCompletionDiv();\"\n" +
                            "autocomplete=\"off\"\n" +
                            "onKeyUp=\"return handleChange(this, event, '" + _completionBase + "');\">");
                }
                else
                {
                    out.write("<input type=\"text\" name=\"" + _formElementName +
                            "\" value=\"" + PageFlowUtil.filter(getValue(ctx)) + "\">");
                }
            }
            else
            {
                super.renderGridCellContents(ctx, out);
                out.write("<input type=\"hidden\" name=\"" + _formElementName +
                        "\" value=\"" + PageFlowUtil.filter(getValue(ctx)) + "\">");
            }
        }
    }

    private class ParticipantIDDataInputColumn extends DataInputColumn
    {
        public ParticipantIDDataInputColumn(String completionBase, ResolverHelper resolverHelper, ColumnInfo ptidCol)
        {
            super(AbstractAssayProvider.PARTICIPANTID_PROPERTY_CAPTION, "participantId",
                    true, completionBase, resolverHelper, ptidCol);
        }

        protected Object calculateValue(RenderContext ctx) throws IOException
        {
            return _resolverHelper.getParticipantId(ctx);
        }
    }

    private class VisitIDDataInputColumn extends DataInputColumn
    {
        public VisitIDDataInputColumn(String completionBase, ResolverHelper resolverHelper, ColumnInfo visitIDCol)
        {
            super(AbstractAssayProvider.VISITID_PROPERTY_CAPTION, "visitId",
                        true, completionBase, resolverHelper, visitIDCol);
        }

        protected Object calculateValue(RenderContext ctx) throws IOException
        {
            return _resolverHelper.getVisitId(ctx);
        }
    }

    private class DateDataInputColumn extends DataInputColumn
    {
        public DateDataInputColumn(String completionBase, ResolverHelper resolverHelper, ColumnInfo dateCol)
        {
            super(AbstractAssayProvider.DATE_PROPERTY_CAPTION, "date",
                        true, completionBase, resolverHelper, dateCol);
        }

        protected Object calculateValue(RenderContext ctx) throws IOException
        {
            return _resolverHelper.getDate(ctx);
        }
    }

    private class ObjectIDDataInputColumn extends DataInputColumn
    {
        public ObjectIDDataInputColumn(String completionBase, ResolverHelper resolverHelper, ColumnInfo objectIdCol)
        {
            super("Object ID", DataRegion.SELECT_CHECKBOX_NAME, false, completionBase, resolverHelper, objectIdCol);
        }

        protected Object calculateValue(RenderContext ctx)
        {
            if (_requiredColumn == null)
                return null;
            return ctx.getRow().get(_requiredColumn.getAlias());
        }
    }

    private abstract class DataInputColumn extends InputColumn
    {
        protected ColumnInfo _requiredColumn;

        public DataInputColumn(String caption, String formElementName, boolean editable, String completionBase, ResolverHelper resolverHelper,
                               ColumnInfo requiredColumn)
        {
            super(caption, editable, formElementName, completionBase, resolverHelper);
            _requiredColumn = requiredColumn;
        }

        protected abstract Object calculateValue(RenderContext ctx) throws IOException;

        public Object getValue(RenderContext ctx)
        {
            try
            {
                return calculateValue(ctx);
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
//            if (_requiredColumn == null)
//                return null;
//            return ctx.getRow().get(_requiredColumn.getAlias());
        }
    }

    protected List<DisplayColumn> getExtraColumns()
    {
        AssayProvider provider = AssayService.get().getProvider(_protocol);
        List<DisplayColumn> columns = new ArrayList<DisplayColumn>();

        FieldKey runIdFieldKey = provider.getRunIdFieldKeyFromDataRow();
        FieldKey objectIdFieldKey = provider.getDataRowIdFieldKey();
        FieldKey ptidFieldKey = provider.getParticipantIDFieldKey();
        FieldKey visitIDFieldKey = provider.getVisitIDFieldKey(_targetStudyContainer);
        FieldKey specimenIDFieldKey = provider.getSpecimenIDFieldKey();
        Set<FieldKey> fieldKeys = new HashSet<FieldKey>(Arrays.asList(runIdFieldKey, objectIdFieldKey, ptidFieldKey, visitIDFieldKey, specimenIDFieldKey));

        // In case the assay definition doesn't have all the fields
        fieldKeys.remove(null);
        Map<FieldKey, ColumnInfo> colInfos = QueryService.get().getColumns(getTable(), fieldKeys);
//        assert colInfos.size() == fieldKeys.size();
        ColumnInfo objectIdCol = colInfos.get(objectIdFieldKey);
        ColumnInfo ptidCol = colInfos.get(ptidFieldKey);
        ColumnInfo runIdCol = colInfos.get(runIdFieldKey);
        ColumnInfo visitIdCol = colInfos.get(visitIDFieldKey);
        ColumnInfo specimenIDCol = colInfos.get(specimenIDFieldKey);

        ResolverHelper resolverHelper = new ResolverHelper(runIdCol, objectIdCol, ptidCol, visitIdCol, specimenIDCol);

        columns.add(new ValidParticipantVisitDisplayColumn(resolverHelper));

        columns.add(new ObjectIDDataInputColumn(null, resolverHelper, objectIdCol));

        String ptidCompletionBase = SpecimenService.get().getCompletionURLBase(_targetStudyContainer,
                SpecimenService.CompletionType.ParticipantId);
        columns.add(new ParticipantIDDataInputColumn(ptidCompletionBase, resolverHelper, ptidCol));

        String visitCompletionBase = SpecimenService.get().getCompletionURLBase(_targetStudyContainer,
                SpecimenService.CompletionType.VisitId);
        if (_timepointType == TimepointType.VISIT)
            columns.add(new VisitIDDataInputColumn(visitCompletionBase, resolverHelper, visitIdCol));
        else
            columns.add(new DateDataInputColumn(null, resolverHelper, visitIdCol));

        return columns;
    }

    protected boolean showControls()
    {
        return false;
    }

    public void setButtons(List<ActionButton> buttons)
    {
        _buttons = buttons;
    }
}
