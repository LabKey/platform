/*
 * Copyright (c) 2009-2019 LabKey Corporation
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
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AbstractAssayProvider;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DetailsColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.IMultiValuedDisplayColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.ShowRows;
import org.labkey.api.data.SimpleDisplayColumn;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.UpdateColumn;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.gwt.client.ui.PropertyType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reports.ReportService;
import org.labkey.api.security.User;
import org.labkey.api.study.CompletionType;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.ParticipantVisit;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.Visit;
import org.labkey.api.study.actions.StudyPickerColumn;
import org.labkey.api.study.assay.ParticipantVisitImpl;
import org.labkey.api.study.assay.ParticipantVisitResolver;
import org.labkey.api.study.assay.SampleParticipantVisitResolver;
import org.labkey.api.study.assay.StudyParticipantVisitResolver;
import org.labkey.api.study.publish.StudyPublishService;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.UniqueID;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.io.Writer;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User: brittp
 * Date: Jul 30, 2007
 * Time: 10:07:07 AM
 */
public class PublishResultsQueryView extends QueryView
{
    private static final Logger LOG = LogManager.getLogger(PublishResultsQueryView.class);

    private final SimpleFilter _filter;
    private final Container _targetStudyContainer;
    private final boolean _mismatched;
    private final boolean _showSpecimenMatch;
    private final TimepointType _timepointType;
    private final Map<Object, String> _reshowVisits;
    private final Map<Object, String> _reshowDates;
    private final Map<Object, String> _reshowPtids;
    private final Map<Object, String> _reshowTargetStudies;
    private final boolean _includeTimestamp;
    private final Map<StudyPublishService.LinkToStudyKeys, FieldKey> _additionalColumns;
    private final Map<String, Object> _hiddenFormFields;
    private final Set<String> _hiddenColumnCaptions;
    private final FieldKey _objectIdFieldKey;
    private final Dataset.PublishSource _publishSource;

    private List<ActionButton> _buttons = null;

    public PublishResultsQueryView(UserSchema schema, QuerySettings settings, BindException errors,
                                   Dataset.PublishSource publishSource,
                                   FieldKey objectIdFieldKey,
                                   List<Integer> objectIds,
                                   @Nullable Container targetStudyContainer,
                                   Map<Object, String> reshowTargetStudies,
                                   Map<Object, String> reshowVisits,
                                   Map<Object, String> reshowDates,
                                   Map<Object, String> reshowPtids,
                                   boolean mismatched,
                                   boolean showSpecimenMatch,
                                   boolean includeTimestamp,
                                   Map<StudyPublishService.LinkToStudyKeys, FieldKey> additionalColumns,
                                   Map<String, Object> hiddenFormFields,
                                   Set<String> hiddenColumnCaptions)
    {
        super(schema, settings, errors);
        _targetStudyContainer = targetStudyContainer;
        _mismatched = mismatched;
        _showSpecimenMatch = showSpecimenMatch;
        if (_targetStudyContainer != null)
            _timepointType = StudyPublishService.get().getTimepointType(_targetStudyContainer);
        else
            _timepointType = null;
        _publishSource = publishSource;
        _objectIdFieldKey = objectIdFieldKey;
        _filter = new SimpleFilter();
        _filter.addInClause(_objectIdFieldKey, objectIds);
        _reshowPtids = reshowPtids;
        _reshowVisits = reshowVisits;
        _reshowDates = reshowDates;
        _reshowTargetStudies = reshowTargetStudies;
        _includeTimestamp = includeTimestamp;
        _additionalColumns = additionalColumns;
        _hiddenFormFields = hiddenFormFields;
        _hiddenColumnCaptions = hiddenColumnCaptions;

        setViewItemFilter(ReportService.EMPTY_ITEM_LIST);
        getSettings().setMaxRows(Table.ALL_ROWS);
        getSettings().setShowRows(ShowRows.ALL);
    }

    @Override
    public DataView createDataView()
    {
        DataView view = super.createDataView();
        DataRegion dataRegion = view.getDataRegion();

        if (_targetStudyContainer != null)
            dataRegion.addHiddenFormField("targetStudy", _targetStudyContainer.getId());

        dataRegion.addHiddenFormField("attemptPublish", "true");
        if (_filter != null)
        {
            view.getRenderContext().setBaseFilter(_filter);
        }
        else
        {
            view.getRenderContext().setBaseFilter(new SimpleFilter());
        }
        if (getSettings().getContainerFilterName() != null)
            dataRegion.addHiddenFormField("containerFilterName", getSettings().getContainerFilterName());

        // add any action specified form fields
        for (Map.Entry<String, Object> entry : _hiddenFormFields.entrySet())
        {
            dataRegion.addHiddenFormField(entry.getKey(), String.valueOf(entry.getValue()));
        }

        ButtonBar bbar = new ButtonBar();
        if (_buttons != null)
        {
            for (ActionButton button : _buttons)
                bbar.add(button);
        }
        dataRegion.setButtonBar(bbar);

        return view;
    }

    @Override
    protected DataRegion createDataRegion()
    {
        DataRegion dr = new DataRegion();
        configureDataRegion(dr);

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
        for (Iterator<DisplayColumn> it = dr.getDisplayColumns().iterator(); it.hasNext();)
        {
            DisplayColumn current = it.next();
            for (String hiddenColName : _hiddenColumnCaptions)
            {
                if (current.getCaption().endsWith(hiddenColName))
                {
                    current.setVisible(false);
                }
            }
            if (current instanceof DetailsColumn || current instanceof UpdateColumn)
            {
                it.remove();
            }
        }
        dr.setShowRecordSelectors(true);
        dr.setShowSelectMessage(false);
        return dr;
    }

    public static Date convertObjectToDate(Container container, Object dateObject)
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
                date = new Date(DateUtil.parseDateTime(container, (String)dateObject));
            }
            catch (ConversionException ignored) {}
        }
        return date;
    }

    public static String convertObjectToString(Object o)
    {
        return o == null ? null : o.toString();
    }

    public static Double convertObjectToDouble(Object visitIdObject)
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

    public static Object getColumnValue(ColumnInfo col, RenderContext ctx)
    {
        DisplayColumn dc = col.getRenderer();
        if (dc instanceof IMultiValuedDisplayColumn)
        {
            // support for lineage and multivalue columns
            List<Object> values = ((IMultiValuedDisplayColumn)dc).getDisplayValues(ctx);
            if (values.size() == 1)
                return values.get(0);
            else
                LOG.warn("Unable to use the value returned from column : " + col.getName() + " because this multi-value column returned more than a single value.");
        }
        return col.getValue(ctx);
    }

    public static class ResolverHelper
    {
        private final Dataset.PublishSource _publishSource;
        private final Container _targetStudyContainer;
        private final User _user;

        private final ColumnInfo _sourceIdCol;
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
        private final ColumnInfo _sampleIdCol;
        private final Map<Integer, ParticipantVisitResolver> _resolvers = new HashMap<>();

        private Map<Object, String> _reshowVisits;
        private Map<Object, String> _reshowDates;
        private Map<Object, String> _reshowPtids;
        private Map<Object, String> _reshowTargetStudies;

        public ResolverHelper(Container targetStudyContainer,
                              User user,
                              Dataset.PublishSource publishSource,
                              ColumnInfo sourceIdCol, ColumnInfo objectIdCol,
                              ColumnInfo ptidCol, ColumnInfo visitIdCol, ColumnInfo dateCol,
                              ColumnInfo specimenIDCol, ColumnInfo assayMatchCol,
                              ColumnInfo specimenPTIDCol, ColumnInfo specimenVisitCol, ColumnInfo specimenDateCol,
                              ColumnInfo targetStudyCol,
                              ColumnInfo sampleIdCol)
        {
            _publishSource = publishSource;
            _targetStudyContainer = targetStudyContainer;
            _user = user;

            _sourceIdCol = sourceIdCol;
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
            _sampleIdCol = sampleIdCol;
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

        private ParticipantVisitResolver getResolver(RenderContext ctx)
        {
            Integer sourceId = _sourceIdCol != null ? (Integer)_sourceIdCol.getValue(ctx) : null;
            if (sourceId != null && !_resolvers.containsKey(sourceId))
            {
                // resolving ptid/timepoint by specimen or sample ID is only supported for Assays
                if (_publishSource == Dataset.PublishSource.Assay)
                {
                    ExpRun run = ExperimentService.get().getExpRun(sourceId);
                    if (run != null)
                    {
                        if (_specimenIDCol != null)
                            _resolvers.put(sourceId, new StudyParticipantVisitResolver(run.getContainer(), _targetStudyContainer, getUser()));
                        else if (_sampleIdCol != null)
                            _resolvers.put(sourceId, new  SampleParticipantVisitResolver(run.getContainer(), _targetStudyContainer, getUser()));
                    }
                }
            }
            return _resolvers.get(sourceId);
        }

        private ParticipantVisit resolve(RenderContext ctx)
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

            Double visitId = _visitIdCol != null && timepointType == TimepointType.VISIT ? convertObjectToDouble(getColumnValue(_visitIdCol, ctx)) : null;
            Date date = _dateCol != null && timepointType == TimepointType.DATE ? convertObjectToDate(ctx.getContainer(), getColumnValue(_dateCol, ctx)) : null;

            String specimenID = _specimenIDCol == null ? null : convertObjectToString(getColumnValue(_specimenIDCol, ctx));
            String participantID = _ptidCol == null ? null : convertObjectToString(getColumnValue(_ptidCol, ctx));
            String sampleID = _sampleIdCol == null ? null : convertObjectToString(getColumnValue(_sampleIdCol, ctx));

            try
            {
                if (_specimenIDCol != null)
                    return resolver.resolve(specimenID, participantID, visitId, date, targetStudyContainer);
                else
                    return resolver.resolve(sampleID, participantID, visitId, date, targetStudyContainer);
            }
            catch (ExperimentException e)
            {
                // We've added validation to the ThawListListResolver to reject imports if not all rows resolve.
                // Preserving the previous behavior here for the publish case.
                return new ParticipantVisitImpl(specimenID, participantID, visitId, date, resolver.getRunContainer(), targetStudyContainer);
            }
        }

        public Object getUserVisitId(RenderContext ctx)
        {
            if (_reshowVisits != null)
            {
                Object key = ctx.getRow().get(_objectIdCol.getName());
                return _reshowVisits.get(key);
            }
            Double result = _visitIdCol == null ? null : convertObjectToDouble(getColumnValue(_visitIdCol, ctx));
            if (result == null)
            {
                ParticipantVisit pv = resolve(ctx);
                result = pv == null ? null : pv.getVisitID();
            }
            return result;
        }

        public String getUserParticipantId(RenderContext ctx)
        {
            if (_reshowPtids != null)
            {
                Object key = ctx.getRow().get(_objectIdCol.getName());
                return _reshowPtids.get(key);
            }
            
            String result = _ptidCol == null ? null : convertObjectToString(getColumnValue(_ptidCol, ctx));
            if (result == null)
            {
                ParticipantVisit pv = resolve(ctx);
                result = pv == null ? null : pv.getParticipantID();
            }
            return result;
        }

        public Object getUserDate(RenderContext ctx, boolean includeTimestamp)
        {
            if (_reshowDates != null)
            {
                Object key = ctx.getRow().get(_objectIdCol.getName());
                return _reshowDates.get(key);
            }
            Date result = _dateCol == null ? null : convertObjectToDate(ctx.getContainer(), getColumnValue(_dateCol, ctx));
            if (result == null)
            {
                ParticipantVisit pv = resolve(ctx);
                result = pv == null ? null : pv.getDate();
            }
            return includeTimestamp ? DateUtil.formatDateTimeISO8601(result) : DateUtil.formatDateISO8601(result);
        }

        public Container getUserTargetStudy(RenderContext ctx)
        {
            Object result = null;
            if (_reshowTargetStudies != null)
            {
                Object key = ctx.getRow().get(_objectIdCol.getName());
                result = _reshowTargetStudies.get(key);
            }
            if (result == null)
                result = _targetStudyCol == null ? null : convertObjectToString(_targetStudyCol.getValue(ctx));
            if (result == null)
            {
                ParticipantVisit pv = resolve(ctx);
                if (pv != null && pv.getStudyContainer() != null)
                    result = pv.getStudyContainer();
            }

            if (result != null)
            {
                Set<Study> studies = StudyService.get().findStudy(result, null);
                if (!studies.isEmpty())
                    return studies.iterator().next().getContainer();
            }

            return null;
        }

        // Study container -> (ptid, visit)
        private Map<Container, Set<Pair<String, Double>>> _validPtidVisits = null;

        private boolean isValidPtidVisit(Container container, String participantId, Double visit) throws SQLException
        {
            if (container == null || SpecimenService.get() == null)
                return false;

            if (_validPtidVisits == null)
                _validPtidVisits = new HashMap<>();

            Set<Pair<String, Double>> visits = _validPtidVisits.get(container);
            if (visits == null)
            {
                visits = SpecimenService.get().getSampleInfo(container, getUser());
                _validPtidVisits.put(container, visits);
            }

            return visits.contains(new Pair<>(participantId, visit));
        }

        // Study container -> (ptid, date)
        private Map<Container, Set<Pair<String, Date>>> _validPtidDates = null;

        private boolean isValidPtidDate(Container container, String participantId, Date drawDate) throws SQLException
        {
            if (container == null || SpecimenService.get() == null)
                return false;

            if (_validPtidDates == null)
                _validPtidDates = new HashMap<>();

            Set<Pair<String, Date>> dates = _validPtidDates.get(container);
            if (dates == null)
            {
                dates = SpecimenService.get().getSampleInfo(container, getUser(), true);
                _validPtidDates.put(container, dates);
            }

            // Timestamps and dates don't always compare correctly, so make sure we have only dates here:
            if (drawDate instanceof Timestamp)
                drawDate = new Date(drawDate.getTime());

            return dates.contains(new Pair<>(participantId, drawDate));
        }

        /** @return boolean to indicate if the row is considered to match, string with a message to explain why */
        public Pair<Boolean, String> getMatchStatus(RenderContext ctx) throws IOException
        {
            Container targetStudy = getUserTargetStudy(ctx);
            if (targetStudy == null)
                targetStudy = _targetStudyContainer;

            // Bail early if no match can be made.
            if (targetStudy == null)
                return new Pair<>(false, "<p>No target study selected for this row.</p>");

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
                        userInputMatchesTargetSpecimen = Objects.equals(targetSpecimenPTID, userParticipantId) &&
                                Objects.equals(targetSpecimenVisit, userVisitId);
                    }
                    else
                    {
                        userInputMatchesTargetSpecimen = null;
                    }
                }
                else
                {
                    Date userDate = convertObjectToDate(ctx.getContainer(), getUserDate(ctx, false));
                    userInputMatchesASpecimen = isValidPtidDate(targetStudy, userParticipantId, userDate);
                    if (_specimenDateCol != null && _specimenPTIDCol != null && assayAndTargetSpecimenMatch != null)
                    {
                        // Need to grab the study specimen's participant and date
                        String targetSpecimenPTID = convertObjectToString(_specimenPTIDCol.getValue(ctx));
                        Date targetSpecimenDate = convertObjectToDate(ctx.getContainer(), _specimenDateCol.getValue(ctx));
                        if (userDate == null || targetSpecimenDate == null)
                        {
                            // Do a simple object equality on the dates if one or both of them is null
                            userInputMatchesTargetSpecimen = Objects.equals(targetSpecimenPTID, userParticipantId) &&
                                    Objects.equals(userDate, targetSpecimenDate);
                        }
                        else
                        {
                            // All we care about is the date part, not the minutes and seconds, so pull those out
                            Calendar targetSpecimenCal = new GregorianCalendar();
                            targetSpecimenCal.setTime(targetSpecimenDate);
                            Calendar userCal = new GregorianCalendar();
                            userCal.setTime(userDate);

                            userInputMatchesTargetSpecimen = Objects.equals(targetSpecimenPTID, userParticipantId) &&
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

                return new Pair<>(overallStatus, sb.toString());
            }
            catch (SQLException e)
            {
                //noinspection ThrowableInstanceNeverThrown
                throw (IOException)new IOException().initCause(e);
            }
        }

        public Pair<Boolean, String> getSampleMatchStatus(RenderContext ctx)
        {
            Container targetStudy = getUserTargetStudy(ctx);
            if (targetStudy == null)
                targetStudy = _targetStudyContainer;

            // Bail early if no match can be made.
            if (targetStudy == null)
                return new Pair<>(false, "<p>No target study selected for this row.</p>");

            ParticipantVisitResolver resolver = getResolver(ctx);
            boolean isSampleMatched = false;
            if (resolver instanceof SampleParticipantVisitResolver)
            {
                Object sampleId = _sampleIdCol.getValue(ctx);
                if (sampleId instanceof Integer)
                {
                    isSampleMatched = ((SampleParticipantVisitResolver) resolver).isSampleMatched((Integer)sampleId);
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append("<p>The Sample ID in this row does");
            if (!isSampleMatched)
                sb.append(" <strong>not</strong>");
            sb.append(" match a sample in the study.</p>");

            return new Pair<>(isSampleMatched, sb.toString());
        }

        public void addQueryColumns(Set<ColumnInfo> set)
        {
            if (_sourceIdCol != null) { set.add(_sourceIdCol); }
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
        private static final String RENDERED_REQUIRES_COMPLETION = InputColumn.class.getName() + "-requiresScript";

        protected boolean _editable;
        protected String _formElementName;
        private final ActionURL _completionBase;
        protected final ResolverHelper _resolverHelper;

        public InputColumn(String caption, boolean editable, String formElementName, @Nullable ActionURL completionBase, ResolverHelper resolverHelper)
        {
            _editable = editable;
            _formElementName = formElementName;
            _completionBase = completionBase;
            _resolverHelper = resolverHelper;
            setCaption(caption);
        }

        @Override
        public void addQueryColumns(Set<ColumnInfo> set)
        {
            super.addQueryColumns(set);
            if (_resolverHelper != null)
                _resolverHelper.addQueryColumns(set);
        }

        protected @Nullable ActionURL getCompletionBase(RenderContext ctx)
        {
            return _completionBase;
        }

        @Override
        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            if (_editable)
            {
                ActionURL completionBase = getCompletionBase(ctx);
                if (completionBase != null)
                {
                    if (ctx.get(RENDERED_REQUIRES_COMPLETION) == null)
                    {
                        // TODO: Use the same code as AutoCompleteTag.java
                        out.write("<script type=\"text/javascript\">LABKEY.requiresScript(\"completion\");</script>");

                        // wire up the completions div on input tag focus
                        StringBuilder sb = new StringBuilder();

                        sb.append("<script type=\"text/javascript\">\n" +
                                  "   function onCompletionFocus(cmp) {\n" +
                                  "      cmp.removeAttribute('onfocus');\n" +
                                  "      Ext4.create('LABKEY.element.AutoCompletionField', {\n" +
                                  "         renderTo        : cmp.getAttribute('completionid'),\n" +
                                  "         completionUrl   : cmp.getAttribute('completion'),\n" +
                                  "         sharedStore     : true,\n" +
                                  "         fieldId         : cmp.getAttribute('id')\n" +
                                  "      });\n" +
                                  "   }\n" +
                                  "</script>\n");
                        out.write(sb.toString());
                        ctx.put(RENDERED_REQUIRES_COMPLETION, true);
                    }

                    String inputId = "input-tag-" + UniqueID.getRequestScopedUID(ctx.getRequest());
                    String completionId = "auto-complete-div-" + UniqueID.getRequestScopedUID(ctx.getRequest());
                    String value = PageFlowUtil.filter(getValue(ctx));

                    StringBuilder sb = new StringBuilder();

                    // render our own input tag and attach the completions div lazily when the input receives
                    // focus
                    sb.append("<input type=\"text\"");
                    sb.append(" id=\"").append(PageFlowUtil.filter(inputId)).append("\"");
                    sb.append(" name=\"" + _formElementName + "\"");
                    sb.append(" completionid=\"").append(PageFlowUtil.filter(completionId)).append("\"");
                    sb.append(" value=\"" + value + "\"");
                    sb.append(" onfocus=\"onCompletionFocus(this);\"");
                    sb.append(" completion=\"").append(PageFlowUtil.filter(completionBase)).append("\">");

                    // the div we will lazily wire up completions to (needs to be a sibling to the input)
                    sb.append("<div id=\"").append(PageFlowUtil.filter(completionId)).append("\">");

                    out.write(sb.toString());
                }
                else
                {
                    out.write("<input type=\"text\" name=\"" + _formElementName +
                            "\" value=\"" + PageFlowUtil.filter(getValue(ctx)) + "\">");
                }
            }
            else
            {
                out.write("<input type=\"hidden\" name=\"" + _formElementName +
                        "\" value=\"" + PageFlowUtil.filter(getValue(ctx)) + "\">");
            }
        }
    }

    private Container rowTargetStudy(ResolverHelper helper, RenderContext ctx)
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
        protected ActionURL getCompletionBase(RenderContext ctx)
        {
            Container c = rowTargetStudy(_resolverHelper, ctx);
            return PageFlowUtil.urlProvider(StudyUrls.class).getCompletionURL(c, CompletionType.ParticipantId);
        }

        @Override
        protected Object calculateValue(RenderContext ctx)
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
        protected ActionURL getCompletionBase(RenderContext ctx)
        {
            Container c = rowTargetStudy(_resolverHelper, ctx);
            return PageFlowUtil.urlProvider(StudyUrls.class).getCompletionURL(c, CompletionType.VisitId);
        }

        @Override
        protected Object calculateValue(RenderContext ctx)
        {
            return _resolverHelper.getUserVisitId(ctx);
        }
    }

    private static class DateDataInputColumn extends DataInputColumn
    {
        private final boolean _includeTimestamp;

        public DateDataInputColumn(@Nullable ActionURL completionBase, ResolverHelper resolverHelper, ColumnInfo dateCol, boolean includeTimestamp)
        {
            super(AbstractAssayProvider.DATE_PROPERTY_CAPTION, "date",
                    true, completionBase, resolverHelper, dateCol);

            _includeTimestamp = includeTimestamp;
        }

        @Override
        protected Object calculateValue(RenderContext ctx)
        {
            return _resolverHelper.getUserDate(ctx, _includeTimestamp);
        }
    }

    private class TimepointPreviewColumn extends SimpleDisplayColumn
    {
        private final ParticipantIDDataInputColumn _participantColumn;
        private final VisitIDDataInputColumn _visitColumn;
        private final DateDataInputColumn _dateColumn;
        private final TargetStudyInputColumn _targetStudyInputColumn;

        public TimepointPreviewColumn(ParticipantIDDataInputColumn participantColumn, VisitIDDataInputColumn visitColumn, DateDataInputColumn dateColumn, TargetStudyInputColumn targetStudyInputColumn)
        {
            _participantColumn = participantColumn;
            _visitColumn = visitColumn;
            _dateColumn = dateColumn;
            _targetStudyInputColumn = targetStudyInputColumn;
            setName("TimepointPreview");
            setCaption("Timepoint Preview");
        }

        @Override
        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            Object visitObject = _visitColumn.calculateValue(ctx);
            Object dateObject = _dateColumn.calculateValue(ctx);
            Object participantObject = _participantColumn.calculateValue(ctx);
            Object targetStudyObject = _targetStudyInputColumn == null ? null : _targetStudyInputColumn.calculateValue(ctx);
            Container targetStudyContainer = null;
            if (targetStudyObject != null)
            {
                targetStudyContainer = ContainerManager.getForId(targetStudyObject.toString());
            }
            if (targetStudyContainer == null)
            {
                targetStudyContainer = _targetStudyContainer;
            }
            // We may not have either a row-level or a default target study 
            Study study = targetStudyContainer == null ? null : StudyService.get().getStudy(targetStudyContainer);
            Visit visit = null;

            String participantID = convertObjectToString(participantObject);
            Double visitDouble = convertObjectToDouble(visitObject);
            Date dateDate = convertObjectToDate(ctx.getContainer(), dateObject);

            visit = study == null ? null : study.getVisit(participantID, visitDouble, dateDate, true);

            out.write(visit == null ? "" : visit.getDisplayString());
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

        @Override
        protected Object calculateValue(RenderContext ctx)
        {
            Container c = _resolverHelper.getUserTargetStudy(ctx);
            return c == null ? null : c.getId();
        }
    }

    protected List<DisplayColumn> getExtraColumns(Collection<ColumnInfo> selectColumns)
    {
        List<DisplayColumn> columns = new ArrayList<>();
        Set<FieldKey> fieldKeys = new HashSet<>(_additionalColumns.values());
        // remove any null values
        fieldKeys.remove(null);
        
        Map<FieldKey, ColumnInfo> colInfos = QueryService.get().getColumns(getTable(), fieldKeys, selectColumns);
        ColumnInfo objectIdCol = colInfos.get(_objectIdFieldKey);
        if (_additionalColumns.containsKey(StudyPublishService.LinkToStudyKeys.ObjectId))
            objectIdCol = colInfos.get(_additionalColumns.get(StudyPublishService.LinkToStudyKeys.ObjectId));
        ColumnInfo ptidCol = colInfos.get(_additionalColumns.get(StudyPublishService.LinkToStudyKeys.ParticipantId));
        if (ptidCol == null)
        {
            //NOTE: the name of the assay PTID field might not always match ParticipantId.  this allows us to also
            //support PARTICIPANT_CONCEPT_URI
            ptidCol = selectColumns.stream().filter(c -> PropertyType.PARTICIPANT_CONCEPT_URI.equals(c.getConceptURI())).findFirst().orElse(null);
        }

        ColumnInfo sourceIdCol = colInfos.get(_additionalColumns.get(StudyPublishService.LinkToStudyKeys.SourceId));
        ColumnInfo visitIDCol = colInfos.get(_additionalColumns.get(StudyPublishService.LinkToStudyKeys.VisitId));
        ColumnInfo dateCol = colInfos.get(_additionalColumns.get(StudyPublishService.LinkToStudyKeys.Date));
        ColumnInfo specimenIDCol = colInfos.get(_additionalColumns.get(StudyPublishService.LinkToStudyKeys.SpecimenId));
        ColumnInfo matchCol = colInfos.get(_additionalColumns.get(StudyPublishService.LinkToStudyKeys.SpecimenMatch));
        ColumnInfo specimenPTIDCol = colInfos.get(_additionalColumns.get(StudyPublishService.LinkToStudyKeys.SpecimenPtid));
        ColumnInfo specimenVisitCol = colInfos.get(_additionalColumns.get(StudyPublishService.LinkToStudyKeys.SpecimenVisit));
        ColumnInfo specimenDateCol = colInfos.get(_additionalColumns.get(StudyPublishService.LinkToStudyKeys.SpecimenDate));
        ColumnInfo targetStudyCol = colInfos.get(_additionalColumns.get(StudyPublishService.LinkToStudyKeys.TargetStudy));
        ColumnInfo sampleIdCol = colInfos.get(_additionalColumns.get(StudyPublishService.LinkToStudyKeys.SampleId));

        // if visit or date columns don't exist, see if they can be resolved through the standard concept URIs
        List<ColumnInfo> timepointCols = selectColumns.stream()
                .filter(c -> PropertyType.VISIT_CONCEPT_URI.equalsIgnoreCase(c.getConceptURI()))
                .collect(Collectors.toList());

        for (ColumnInfo col : timepointCols)
        {
            if (dateCol == null && col.getJdbcType().isDateOrTime())
                dateCol = col;
            if (visitIDCol == null && col.getJdbcType().isReal())
                visitIDCol = col;
        }

        ResolverHelper resolverHelper = new ResolverHelper(
                _targetStudyContainer, getUser(),
                _publishSource,
                sourceIdCol, objectIdCol, ptidCol, visitIDCol, dateCol, specimenIDCol, matchCol, specimenPTIDCol,
                specimenVisitCol, specimenDateCol, targetStudyCol, sampleIdCol);
        resolverHelper.setReshow(_reshowVisits, _reshowDates, _reshowPtids, _reshowTargetStudies);

        TargetStudyInputColumn targetStudyInputColumn = null;
        if (targetStudyCol != null)
        {
            targetStudyInputColumn = new TargetStudyInputColumn(resolverHelper, targetStudyCol);
            columns.add(targetStudyInputColumn);
        }

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

        if (_showSpecimenMatch)
            columns.add(new ValidParticipantVisitDisplayColumn(resolverHelper, specimenIDCol, sampleIdCol));

        if (sourceIdCol != null && objectIdCol != null)
            columns.add(new SourceDataLinkDisplayColumn(null, resolverHelper, _publishSource, sourceIdCol, objectIdCol));
        else
            throw new IllegalStateException("Both sourceId and objectId columns are required for the view");

        ParticipantIDDataInputColumn participantColumn = new ParticipantIDDataInputColumn(resolverHelper, ptidCol);
        columns.add(participantColumn);

        // UNDONE: If selected ids contain studies of different timepoint types, include both Date and Visit columns and enable and disable the inputs when the study picker changes.
        // For now, just include both Date and Visit columns if the target study isn't known yet.
        VisitIDDataInputColumn visitIDInputColumn = new VisitIDDataInputColumn(resolverHelper, visitIDCol);
        DateDataInputColumn dateInputColumn = new DateDataInputColumn(null, resolverHelper, dateCol, _includeTimestamp);
        if (_timepointType == null || _timepointType == TimepointType.VISIT)
        {
            columns.add(visitIDInputColumn);
        }
        if (_timepointType == null || _timepointType == TimepointType.DATE || _timepointType == TimepointType.CONTINUOUS)
        {
            columns.add(dateInputColumn);
        }

        columns.add(new TimepointPreviewColumn(participantColumn, visitIDInputColumn, dateInputColumn, targetStudyInputColumn));

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
