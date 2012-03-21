/*
 * Copyright (c) 2007-2012 LabKey Corporation
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

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang3.ObjectUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.*;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.reports.ReportService;
import org.labkey.api.security.User;
import org.labkey.api.study.ParticipantVisit;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.actions.StudyPickerColumn;
import org.labkey.api.study.assay.*;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.DataView;

import java.io.IOException;
import java.io.Writer;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;

/**
 * User: brittp
 * Date: Jul 30, 2007
 * Time: 10:07:07 AM
 */
public class PublishResultsQueryView extends ResultsQueryView
{
    private SimpleFilter _filter;
    private List<ActionButton> _buttons;
    private final Container _targetStudyContainer;
    private final DefaultValueSource _defaultValueSource;
    private final boolean _mismatched;
    private final TimepointType _timepointType;
    private Map<Object, String> _reshowVisits;
    private Map<Object, String> _reshowDates;
    private Map<Object, String> _reshowPtids;
    private Map<Object, String> _reshowTargetStudies;

    public enum DefaultValueSource
    {
        Assay
        {
            public FieldKey getParticipantIDFieldKey(AssayTableMetadata tableMetadata)
            {
                return tableMetadata.getParticipantIDFieldKey();
            }
            public FieldKey getVisitIDFieldKey(AssayTableMetadata tableMetadata, TimepointType type)
            {
                return tableMetadata.getVisitIDFieldKey(type);
            }
        },
        Specimen
        {
            public FieldKey getParticipantIDFieldKey(AssayTableMetadata tableMetadata)
            {
                return new FieldKey(tableMetadata.getSpecimenIDFieldKey(), "ParticipantID");
            }
            public FieldKey getVisitIDFieldKey(AssayTableMetadata tableMetadata, TimepointType type)
            {
                if (type == TimepointType.VISIT)
                {
                    return new FieldKey(tableMetadata.getSpecimenIDFieldKey(), "Visit");
                }
                else
                {
                    return new FieldKey(tableMetadata.getSpecimenIDFieldKey(), "DrawTimestamp");
                }
            }
        },
        UserSpecified
        {
            public FieldKey getParticipantIDFieldKey(AssayTableMetadata tableMetadata)
            {
                return null;
            }
            public FieldKey getVisitIDFieldKey(AssayTableMetadata tableMetadata, TimepointType type)
            {
                return null;
            }
        };

        public abstract FieldKey getParticipantIDFieldKey(AssayTableMetadata tableMetadata);
        public abstract FieldKey getVisitIDFieldKey(AssayTableMetadata tableMetadata, TimepointType type);
    }

    public PublishResultsQueryView(ExpProtocol protocol, AssaySchema schema, QuerySettings settings,
                                   List<Integer> objectIds,
                                   @Nullable Container targetStudyContainer,
                                   Map<Object, String> reshowTargetStudies,
                                   Map<Object, String> reshowVisits,
                                   Map<Object, String> reshowDates,
                                   Map<Object, String> reshowPtids,
                                   DefaultValueSource defaultValueSource, boolean mismatched)
    {
        super(protocol, schema, settings);
        _targetStudyContainer = targetStudyContainer;
        _defaultValueSource = defaultValueSource;
        _mismatched = mismatched;
        if (_targetStudyContainer != null)
            _timepointType = AssayPublishService.get().getTimepointType(_targetStudyContainer);
        else
            _timepointType = null;
        _filter = new SimpleFilter();
        AssayProvider provider = AssayService.get().getProvider(protocol);
        _filter.addInClause(provider.getTableMetadata().getResultRowIdFieldKey().toString(), objectIds);
        _reshowPtids = reshowPtids;
        _reshowVisits = reshowVisits;
        _reshowDates = reshowDates;
        _reshowTargetStudies = reshowTargetStudies;
        setViewItemFilter(ReportService.EMPTY_ITEM_LIST);

        getSettings().setMaxRows(Table.ALL_ROWS);
        getSettings().setShowRows(ShowRows.ALL);
    }

    public DataView createDataView()
    {
        DataView view = super.createDataView();
        if (_targetStudyContainer != null)
            view.getDataRegion().addHiddenFormField("targetStudy", _targetStudyContainer.getId());
        view.getDataRegion().addHiddenFormField("attemptPublish", "true");
        if (_filter != null)
        {
            view.getRenderContext().setBaseFilter(_filter);
        }
        else
        {
            view.getRenderContext().setBaseFilter(new SimpleFilter());
        }
        if (getSettings().getContainerFilterName() != null)
            view.getDataRegion().addHiddenFormField("containerFilterName", getSettings().getContainerFilterName());

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
        DataRegion dr = new DataRegion();
        initializeDataRegion(dr);
        dr.setShowFilters(false);
        dr.setSortable(false);
        dr.setShowPagination(true);
        Map<FieldKey,ColumnInfo> cols = dr.getSelectColumns();
        List<DisplayColumn> extraColumns = getExtraColumns(cols.values());
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
        Set<String> hiddenColNames = getHiddenColumnCaptions();
        for (Iterator<DisplayColumn> it = dr.getDisplayColumns().iterator(); it.hasNext();)
        {
            DisplayColumn current = it.next();
            for (String hiddenColName : hiddenColNames)
            {
                if (current.getCaption().endsWith(hiddenColName))
                {
                    current.setVisible(false);
                }
            }
            if (current instanceof DetailsColumn)
            {
                it.remove();
            }
        }
        dr.setShowRecordSelectors(true);
        dr.setShowSelectMessage(false);
        return dr;
    }

    protected Set<String> getHiddenColumnCaptions()
    {
        HashSet<String> hidden = new HashSet<String>(Collections.singleton("Assay Match"));
        if (_targetStudyDomainProperty != null && _targetStudyDomainProperty.first != ExpProtocol.AssayDomainTypes.Result)
            hidden.add(AbstractAssayProvider.TARGET_STUDY_PROPERTY_CAPTION);
        return hidden;
    }

    public static class ResolverHelper
    {
        private final ExpProtocol _protocol;
        private final Container _targetStudyContainer;
        private final User _user;

        private final ColumnInfo _runIdCol;
        private final ColumnInfo _objectIdCol;
        private final ColumnInfo _ptidCol;
        private final ColumnInfo _visitIdCol;
        private final ColumnInfo _dateCol;
        private final ColumnInfo _specimenIDCol;
        private final ColumnInfo _assayMatchCol;
        private final ColumnInfo _specimenPTIDCol;
        private final ColumnInfo _specimenVisitCol;
        private final ColumnInfo _specimenDateCol;
        private final ColumnInfo _targetStudyCol;
        private Map<Integer, ParticipantVisitResolver> _resolvers = new HashMap<Integer, ParticipantVisitResolver>();

        private Map<Object, String> _reshowVisits;
        private Map<Object, String> _reshowDates;
        private Map<Object, String> _reshowPtids;
        private Map<Object, String> _reshowTargetStudies;

        public ResolverHelper(ExpProtocol protocol,
                              Container targetStudyContainer,
                              User user,
                              ColumnInfo runIdCol, ColumnInfo objectIdCol,
                              ColumnInfo ptidCol, ColumnInfo visitIdCol, ColumnInfo dateCol,
                              ColumnInfo specimenIDCol, ColumnInfo assayMatchCol,
                              ColumnInfo specimenPTIDCol, ColumnInfo specimenVisitCol, ColumnInfo specimenDateCol,
                              ColumnInfo targetStudyCol)
        {
            _protocol = protocol;
            _targetStudyContainer = targetStudyContainer;
            _user = user;

            _runIdCol = runIdCol;
            _objectIdCol = objectIdCol;
            _ptidCol = ptidCol;
            _visitIdCol = visitIdCol;
            _dateCol = dateCol;
            _specimenIDCol = specimenIDCol;
            _assayMatchCol = assayMatchCol;
            _specimenPTIDCol = specimenPTIDCol;
            _specimenVisitCol = specimenVisitCol;
            _specimenDateCol = specimenDateCol;
            _targetStudyCol = targetStudyCol;
        }

        private User getUser()
        {
            return _user;
        }

        private void setReshow(Map<Object, String> reshowVisits, Map<Object, String> reshowDates, Map<Object, String> reshowPtids, Map<Object, String> reshowTargetStudies)
        {
            _reshowVisits = reshowVisits;
            _reshowDates = reshowDates;
            _reshowPtids = reshowPtids;
            _reshowTargetStudies = reshowTargetStudies;
        }

        public ParticipantVisitResolver getResolver(RenderContext ctx) throws IOException
        {
            Integer runId = (Integer)_runIdCol.getValue(ctx);
            if (runId != null && !_resolvers.containsKey(runId))
            {
                ExpRun run = ExperimentService.get().getExpRun(runId.intValue());
                ExpExperiment batch = AssayService.get().findBatch(run);
                Collection<ObjectProperty> properties = new ArrayList<ObjectProperty>(run.getObjectProperties().values());
                if (batch != null)
                {
                    properties.addAll(batch.getObjectProperties().values());
                }

                AssayProvider provider = AssayService.get().getProvider(_protocol);
                for (ObjectProperty property : properties)
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
                            //noinspection ThrowableInstanceNeverThrown
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

            Study targetStudy = null;
            if (_targetStudyContainer == null)
            {
                Object resultsDomainTargetStudyValue = _targetStudyCol == null ? null : _targetStudyCol.getValue(ctx);
                if (resultsDomainTargetStudyValue != null)
                {
                    Set<Study> studies = StudyService.get().findStudy(resultsDomainTargetStudyValue, null);
                    if (!studies.isEmpty())
                        targetStudy = studies.iterator().next();
                }
            }

            TimepointType timepointType = targetStudy == null ? null : targetStudy.getTimepointType();
            Container targetStudyContainer = targetStudy == null ? null : targetStudy.getContainer();

            Object visitID = _visitIdCol != null && timepointType == TimepointType.VISIT ? _visitIdCol.getValue(ctx) : null;
            Object date = _dateCol != null && timepointType == TimepointType.DATE ? _dateCol.getValue(ctx) : null;

            String specimenID = _specimenIDCol == null ? null : (_specimenIDCol.getValue(ctx) == null ? null : _specimenIDCol.getValue(ctx).toString());
            String participantID = _ptidCol == null ? null : (_ptidCol.getValue(ctx) == null ? null : _ptidCol.getValue(ctx).toString());

            return resolver.resolve(specimenID, participantID, visitID instanceof Double ? (Double) visitID : null, date instanceof Date ? (Date)date : null, targetStudyContainer);
        }

        public Object getUserVisitId(RenderContext ctx) throws IOException
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

        public String getUserParticipantId(RenderContext ctx) throws IOException
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

        public Object getUserDate(RenderContext ctx) throws IOException
        {
            if (_reshowDates != null)
            {
                Object key = ctx.getRow().get(_objectIdCol.getName());
                return _reshowDates.get(key);
            }
            Date result = _dateCol == null ? null : (_dateCol.getValue(ctx) instanceof Date ? (Date)_dateCol.getValue(ctx) : null);
            if (result == null)
            {
                ParticipantVisit pv = resolve(ctx);
                result = pv == null ? null : pv.getDate();
            }
            return result;
        }

        public Container getUserTargetStudy(RenderContext ctx) throws IOException
        {
            Object result = null;
            if (_reshowTargetStudies != null)
            {
                Object key = ctx.getRow().get(_objectIdCol.getName());
                result = _reshowTargetStudies.get(key);
            }
            if (result == null)
                result = _targetStudyCol == null ? null : (_targetStudyCol.getValue(ctx) == null ? null : _targetStudyCol.getValue(ctx).toString());
            if (result == null)
            {
                ParticipantVisit pv = resolve(ctx);
                if (pv != null && pv.getStudyContainer() != null)
                    result = pv.getStudyContainer();
            }

            if (result != null)
            {
                Set<Study> studies = StudyService.get().findStudy(result, null);
                if (studies != null && !studies.isEmpty())
                    return studies.iterator().next().getContainer();
            }

            return null;
        }

        // Study container -> (ptid, visit)
        private Map<Container, Set<Pair<String, Double>>> _validPtidVisits = null;

        private boolean isValidPtidVisit(Container container, String participantId, Double visit) throws SQLException
        {
            if (container == null)
                return false;

            if (_validPtidVisits == null)
                _validPtidVisits = new HashMap<Container, Set<Pair<String, Double>>>();

            Set<Pair<String, Double>> visits = _validPtidVisits.get(container);
            if (visits == null)
            {
                visits = SpecimenService.get().getSampleInfo(container);
                _validPtidVisits.put(container, visits);
            }

            return visits.contains(new Pair<String, Double>(participantId, visit));
        }

        // Study container -> (ptid, date)
        private Map<Container, Set<Pair<String, Date>>> _validPtidDates = null;

        private boolean isValidPtidDate(Container container, String participantId, Date drawDate) throws SQLException
        {
            if (container == null)
                return false;

            if (_validPtidDates == null)
                _validPtidDates = new HashMap<Container, Set<Pair<String, Date>>>();

            Set<Pair<String, Date>> dates = _validPtidDates.get(container);
            if (dates == null)
            {
                dates = SpecimenService.get().getSampleInfo(container, true);
                _validPtidDates.put(container, dates);
            }

            // Timestamps and dates don't always compare correctly, so make sure we have only dates here:
            if (drawDate instanceof Timestamp)
                drawDate = new Date(drawDate.getTime());

            return dates.contains(new Pair<String, Date>(participantId, drawDate));
        }

        /** @return boolean to indicate if the row is considered to match, string with a message to explain why */
        public Pair<Boolean, String> getMatchStatus(RenderContext ctx) throws IOException
        {
            Container targetStudy = getUserTargetStudy(ctx);
            if (targetStudy == null)
                targetStudy = _targetStudyContainer;

            // Bail early if no match can be made.
            if (targetStudy == null)
                return new Pair<Boolean, String>(false, "<p>No target study selected for this row.</p>");

            Study study = StudyService.get().getStudy(targetStudy);
            TimepointType timepointType = study.getTimepointType();

            // First, figure out if we can match the specimen ID to the study
            Boolean assayAndTargetSpecimenMatch;
            if (_assayMatchCol == null)
            {
                assayAndTargetSpecimenMatch = null;
            }
            else
            {
                assayAndTargetSpecimenMatch = (Boolean)_assayMatchCol.getValue(ctx);
            }

            // Whether the input from the form matches up with the specimen from the assay row's specimen ID
            Boolean userInputMatchesTargetSpecimen;

            try
            {
                // See if the value in the form matches up with at least one specimen
                boolean userInputMatchesASpecimen;

                String userParticipantId = getUserParticipantId(ctx);
                if (timepointType == TimepointType.VISIT)
                {
                    Double userVisitId = convertObjectToDouble(getUserVisitId(ctx));
                    userInputMatchesASpecimen = isValidPtidVisit(targetStudy, userParticipantId, userVisitId);
                    if (_specimenVisitCol != null && _specimenPTIDCol != null && assayAndTargetSpecimenMatch != null)
                    {
                        // Need to grab the study specimen's participant and visit
                        String targetSpecimenPTID = convertObjectToString(_specimenPTIDCol.getValue(ctx));
                        Double targetSpecimenVisit = convertObjectToDouble(_specimenVisitCol.getValue(ctx));
                        userInputMatchesTargetSpecimen = ObjectUtils.equals(targetSpecimenPTID, userParticipantId) &&
                                ObjectUtils.equals(targetSpecimenVisit, userVisitId);
                    }
                    else
                    {
                        userInputMatchesTargetSpecimen = null;
                    }
                }
                else
                {
                    Date userDate = convertObjectToDate(getUserDate(ctx));
                    userInputMatchesASpecimen = isValidPtidDate(targetStudy, userParticipantId, userDate);
                    if (_specimenDateCol != null && _specimenPTIDCol != null && assayAndTargetSpecimenMatch != null)
                    {
                        // Need to grab the study specimen's participant and date
                        String targetSpecimenPTID = convertObjectToString(_specimenPTIDCol.getValue(ctx));
                        Date targetSpecimenDate = convertObjectToDate(_specimenDateCol.getValue(ctx));
                        if (userDate == null || targetSpecimenDate == null)
                        {
                            // Do a simple object equality on the dates if one or both of them is null
                            userInputMatchesTargetSpecimen = ObjectUtils.equals(targetSpecimenPTID, userParticipantId) &&
                                    ObjectUtils.equals(userDate, targetSpecimenDate);
                        }
                        else
                        {
                            // All we care about is the date part, not the minutes and seconds, so pull those out
                            Calendar targetSpecimenCal = new GregorianCalendar();
                            targetSpecimenCal.setTime(targetSpecimenDate);
                            Calendar userCal = new GregorianCalendar();
                            userCal.setTime(userDate);

                            userInputMatchesTargetSpecimen = ObjectUtils.equals(targetSpecimenPTID, userParticipantId) &&
                                    userCal.get(Calendar.YEAR) == targetSpecimenCal.get(Calendar.YEAR) &&
                                    userCal.get(Calendar.MONTH) == targetSpecimenCal.get(Calendar.MONTH) &&
                                    userCal.get(Calendar.DAY_OF_MONTH) == targetSpecimenCal.get(Calendar.DAY_OF_MONTH);
                        }
                    }
                    else
                    {
                        // We don't have enough information to compare to the study specimens
                        userInputMatchesTargetSpecimen = null;
                    }
                }
                // Overall match is defined by either:
                // Having no way to match to a specimen by ID and having the user's form entry match to a specimen OR
                // having the user's form match up against the specimen pointed to by the specimen ID
                boolean overallStatus = userInputMatchesASpecimen && (userInputMatchesTargetSpecimen == null || Boolean.TRUE.equals(userInputMatchesTargetSpecimen));

                // Build up the smallest useful message about why the match looks good or bad
                StringBuilder sb = new StringBuilder();
                if (userInputMatchesTargetSpecimen == null || !userInputMatchesTargetSpecimen.booleanValue())
                {
                    // Only bother with this if we couldn't match up by specimen ID.
                    sb.append("<p>Specimens were");
                    if (!userInputMatchesASpecimen)
                    {
                        sb.append(" <strong>not</strong>");
                    }
                    sb.append(" collected for the Participant ID and ");
                    sb.append(timepointType == TimepointType.VISIT ? "Visit ID" : "Date");
                    sb.append(" shown in this row.</p>");
                }

                // If we found a specimen based on the Specimen ID
                if (userInputMatchesTargetSpecimen != null)
                {
                    sb.append("The Participant ID, ");
                    sb.append(timepointType == TimepointType.VISIT ? "Visit ID" : "Date");
                    sb.append(", and Specimen ID in this row ");
                    if (userInputMatchesTargetSpecimen.booleanValue())
                    {
                        sb.append("matches");
                    }
                    else
                    {
                        sb.append("does <strong>not</strong> match");
                    }
                    sb.append(" a vial in the study.</p>");
                }
                else if (_specimenIDCol != null && _specimenIDCol.getValue(ctx) != null)
                {
                    // Otherwise, if the assay row has a specimen ID let the user know what we couldn't resolve it
                    sb.append("<p>The Specimen ID in this row does <strong>not</strong> match a vial in the study.</p>");
                }

                return new Pair<Boolean, String>(overallStatus, sb.toString());
            }
            catch (SQLException e)
            {
                //noinspection ThrowableInstanceNeverThrown
                throw (IOException)new IOException().initCause(e);
            }
        }

        private Date convertObjectToDate(Object dateObject)
        {
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
            return date;
        }

        public String convertObjectToString(Object o)
        {
            return o == null ? null : o.toString();
        }

        private Double convertObjectToDouble(Object visitIdObject)
        {
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
            return visitId;
        }

        public void addQueryColumns(Set<ColumnInfo> set)
        {
            if (_runIdCol != null) { set.add(_runIdCol); }
            if (_ptidCol != null) { set.add(_ptidCol); }
            if (_visitIdCol != null) { set.add(_visitIdCol); }
            if (_dateCol != null) { set.add(_dateCol); }
            if (_objectIdCol != null) { set.add(_objectIdCol); }
            if (_specimenIDCol != null) { set.add(_specimenIDCol); }
            if (_targetStudyCol != null) { set.add(_targetStudyCol); }
        }

    }

    public static abstract class InputColumn extends SimpleDisplayColumn
    {
        private static String RENDERED_REQUIRES_COMPLETION = InputColumn.class.getName() + "-requiresScript";

        protected boolean _editable;
        protected String _formElementName;
        private String _completionBase;
        protected final ResolverHelper _resolverHelper;

        public InputColumn(String caption, boolean editable, String formElementName, String completionBase, ResolverHelper resolverHelper)
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
            if (_resolverHelper != null)
                _resolverHelper.addQueryColumns(set);
        }

        protected String getCompletionBase(RenderContext ctx) throws IOException
        {
            return _completionBase;
        }

        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            if (_editable)
            {
                String completionBase = getCompletionBase(ctx);
                if (completionBase != null)
                {
                    if (ctx.get(RENDERED_REQUIRES_COMPLETION) == null)
                    {
                        out.write("<script type=\"text/javascript\">LABKEY.requiresScript(\"completion.js\");</script>");
                        ctx.put(RENDERED_REQUIRES_COMPLETION, true);
                    }
                    out.write("<input type=\"text\" name=\"" + _formElementName + "\"\n" +
                            "value=\"" + PageFlowUtil.filter(getValue(ctx)) + "\"\n" +
                            "onKeyDown=\"return ctrlKeyCheck(event);\"\n" +
                            "onBlur=\"hideCompletionDiv();\"\n" +
                            "autocomplete=\"off\"\n");
                    try
                    {
                        out.write("tabindex=\"" + ctx.getResultSet().getRow() + "\"\n");
                    }
                    catch (SQLException e) {}
                    out.write("onKeyUp=\"return handleChange(this, event, '" + completionBase + "');\">");
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

    private Container rowTargetStudy(ResolverHelper helper, RenderContext ctx)
            throws IOException
    {
        return _targetStudyContainer != null ? _targetStudyContainer : helper.getUserTargetStudy(ctx);
    }

    private class ParticipantIDDataInputColumn extends DataInputColumn
    {
        public ParticipantIDDataInputColumn(ResolverHelper resolverHelper, ColumnInfo ptidCol)
        {
            super(AbstractAssayProvider.PARTICIPANTID_PROPERTY_CAPTION, "participantId",
                    true, null, resolverHelper, ptidCol);
        }

        @Override
        protected String getCompletionBase(RenderContext ctx) throws IOException
        {
            Container c = rowTargetStudy(_resolverHelper, ctx);
            return SpecimenService.get().getCompletionURLBase(c, SpecimenService.CompletionType.ParticipantId);
        }

        protected Object calculateValue(RenderContext ctx) throws IOException
        {
            return _resolverHelper.getUserParticipantId(ctx);
        }
    }

    private class VisitIDDataInputColumn extends DataInputColumn
    {
        public VisitIDDataInputColumn(ResolverHelper resolverHelper, ColumnInfo visitIDCol)
        {
            super(AbstractAssayProvider.VISITID_PROPERTY_CAPTION, "visitId",
                        true, null, resolverHelper, visitIDCol);
        }

        @Override
        protected String getCompletionBase(RenderContext ctx) throws IOException
        {
            Container c = rowTargetStudy(_resolverHelper, ctx);
            return SpecimenService.get().getCompletionURLBase(c, SpecimenService.CompletionType.VisitId);
        }

        protected Object calculateValue(RenderContext ctx) throws IOException
        {
            return _resolverHelper.getUserVisitId(ctx);
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
            return _resolverHelper.getUserDate(ctx);
        }
    }

    private class ObjectIDDataInputColumn extends DataInputColumn
    {
        public ObjectIDDataInputColumn(String completionBase, ResolverHelper resolverHelper, ColumnInfo objectIdCol)
        {
            super("Object ID", "objectId", false, completionBase, resolverHelper, objectIdCol);
        }

        protected Object calculateValue(RenderContext ctx)
        {
            if (_requiredColumn == null)
                return null;
            return ctx.getRow().get(_requiredColumn.getAlias());
        }
    }

    // UNDONE: merge UploadWizardAction.InputDisplayColumn and PublishResultsQueryView.DataInputColumn
    private class TargetStudyInputColumn extends StudyPickerColumn
    {
        ResolverHelper _resolverHelper;

        public TargetStudyInputColumn(ResolverHelper resolverHelper, ColumnInfo targetStudyCol)
        {
            super(targetStudyCol, "targetStudy");
            _resolverHelper = resolverHelper;
        }

        protected Object calculateValue(RenderContext ctx)
        {
            try
            {
                Container c = _resolverHelper.getUserTargetStudy(ctx);
                return c == null ? null : c.getId();
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
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

    Pair<ExpProtocol.AssayDomainTypes, DomainProperty> _targetStudyDomainProperty = null;

    protected List<DisplayColumn> getExtraColumns(Collection<ColumnInfo> selectColumns)
    {
        AssayProvider provider = AssayService.get().getProvider(_protocol);
        List<DisplayColumn> columns = new ArrayList<DisplayColumn>();

        _targetStudyDomainProperty = provider.findTargetStudyProperty(_protocol);

        AssayTableMetadata tableMetadata = provider.getTableMetadata();
        FieldKey runIdFieldKey = tableMetadata.getRunRowIdFieldKeyFromResults();
        FieldKey objectIdFieldKey = tableMetadata.getResultRowIdFieldKey();
        FieldKey assayPTIDFieldKey = _defaultValueSource.getParticipantIDFieldKey(tableMetadata);
        FieldKey assayVisitIDFieldKey = _defaultValueSource.getVisitIDFieldKey(tableMetadata, TimepointType.VISIT);
        FieldKey assayDateFieldKey = _defaultValueSource.getVisitIDFieldKey(tableMetadata, TimepointType.DATE);
        FieldKey specimenIDFieldKey = tableMetadata.getSpecimenIDFieldKey();
        FieldKey matchFieldKey = new FieldKey(tableMetadata.getSpecimenIDFieldKey(), AbstractAssayProvider.ASSAY_SPECIMEN_MATCH_COLUMN_NAME);
        FieldKey specimenPTIDFieldKey = new FieldKey(new FieldKey(specimenIDFieldKey, "Specimen"), "ParticipantID");
        FieldKey specimenVisitFieldKey = new FieldKey(new FieldKey(new FieldKey(specimenIDFieldKey, "Specimen"), "Visit"), "SequenceNumMin");
        FieldKey specimenDateFieldKey = new FieldKey(new FieldKey(specimenIDFieldKey, "Specimen"), "DrawTimestamp");
        Set<FieldKey> fieldKeys = new HashSet<FieldKey>(Arrays.asList(runIdFieldKey, objectIdFieldKey, assayPTIDFieldKey, assayVisitIDFieldKey, assayDateFieldKey, specimenIDFieldKey, matchFieldKey, specimenPTIDFieldKey, specimenVisitFieldKey, specimenDateFieldKey));

        // Add the TargetStudy FieldKey only if it exists on the Result domain.
        FieldKey targetStudyFieldKey = null;
        if (_targetStudyDomainProperty != null && _targetStudyDomainProperty.first == ExpProtocol.AssayDomainTypes.Result)
        {
            targetStudyFieldKey = tableMetadata.getTargetStudyFieldKey(provider, _protocol);
            fieldKeys.add(targetStudyFieldKey);
        }

        // In case the assay definition doesn't have all the fields
        fieldKeys.remove(null);
        
        Map<FieldKey, ColumnInfo> colInfos = QueryService.get().getColumns(getTable(), fieldKeys, selectColumns);
        ColumnInfo objectIdCol = colInfos.get(objectIdFieldKey);
        ColumnInfo assayPTIDCol = colInfos.get(assayPTIDFieldKey);
        ColumnInfo runIdCol = colInfos.get(runIdFieldKey);
        ColumnInfo assayVisitIDCol = colInfos.get(assayVisitIDFieldKey);
        ColumnInfo assayDateCol = colInfos.get(assayDateFieldKey);
        ColumnInfo specimenIDCol = colInfos.get(specimenIDFieldKey);
        ColumnInfo matchCol = colInfos.get(matchFieldKey);
        ColumnInfo specimenPTIDCol = colInfos.get(specimenPTIDFieldKey);
        ColumnInfo specimenVisitCol = colInfos.get(specimenVisitFieldKey);
        ColumnInfo specimenDateCol = colInfos.get(specimenDateFieldKey);
        ColumnInfo targetStudyCol = colInfos.get(targetStudyFieldKey);

        ResolverHelper resolverHelper = new ResolverHelper(
                _protocol, _targetStudyContainer, getUser(),
                runIdCol, objectIdCol, assayPTIDCol, assayVisitIDCol, assayDateCol, specimenIDCol, matchCol, specimenPTIDCol, specimenVisitCol, specimenDateCol, targetStudyCol);
        resolverHelper.setReshow(_reshowVisits, _reshowDates, _reshowPtids, _reshowTargetStudies);

        if (targetStudyCol != null)
            columns.add(new TargetStudyInputColumn(resolverHelper, targetStudyCol));

        if (specimenPTIDCol != null)
        {
            // This is ugly but we need to hold on to a reference to this ColumnInfo and make sure that it's in the select list
            DataColumn c = new DataColumn(specimenPTIDCol);
            c.setVisible(false);
            columns.add(c);
            // We eliminate duplicate columns later based on labels, so make sure these have unique ones
            c.setCaption("Specimen PTID - hidden");
        }
        if (specimenVisitCol != null)
        {
            // This is ugly but we need to hold on to a reference to this ColumnInfo and make sure that it's in the select list
            DataColumn c = new DataColumn(specimenVisitCol);
            c.setVisible(false);
            columns.add(c);
            // We eliminate duplicate columns later based on labels, so make sure these have unique ones
            c.setCaption("Specimen Visit - hidden");
        }
        if (specimenDateCol != null)
        {
            // This is ugly but we need to hold on to a reference to this ColumnInfo and make sure that it's in the select list
            DataColumn c = new DataColumn(specimenDateCol);
            c.setVisible(false);
            columns.add(c);
            // We eliminate duplicate columns later based on labels, so make sure these have unique ones
            c.setCaption("Specimen Date - hidden");
        }
        if (matchCol != null)
        {
            // This is ugly but we need to hold on to a reference to this ColumnInfo and make sure that it's in the select list
            DataColumn c = new DataColumn(matchCol);
            c.setVisible(false);
            columns.add(c);
            // We eliminate duplicate columns later based on labels, so make sure these have unique ones
            c.setCaption("Specimen Match - hidden");
        }

        columns.add(new ValidParticipantVisitDisplayColumn(resolverHelper));

        columns.add(new RunDataLinkDisplayColumn(_protocol, runIdCol));

        columns.add(new ObjectIDDataInputColumn(null, resolverHelper, objectIdCol));

        columns.add(new ParticipantIDDataInputColumn(resolverHelper, assayPTIDCol));

        // UNDONE: If selected ids contain studies of different timepoint types, include both Date and Visit columns and enable and disable the inputs when the study picker changes.
        // For now, just include both Date and Visit columns if the target study isn't known yet.
        if (_timepointType == null || _timepointType == TimepointType.VISIT)
            columns.add(new VisitIDDataInputColumn(resolverHelper, assayVisitIDCol));
        if (_timepointType == null || _timepointType == TimepointType.DATE || _timepointType == TimepointType.CONTINUOUS)
            columns.add(new DateDataInputColumn(null, resolverHelper, assayDateCol));

        if (_mismatched)
        {
            if (matchCol != null)
            {
                columns.add(new DataColumn(matchCol));
            }
        }

        return columns;
    }

    public void setButtons(List<ActionButton> buttons)
    {
        _buttons = buttons;
    }
}
