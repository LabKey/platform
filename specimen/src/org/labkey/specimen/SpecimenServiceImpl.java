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

package org.labkey.specimen;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.specimen.DefaultSpecimenTablesTemplate;
import org.labkey.api.specimen.SpecimenColumns;
import org.labkey.api.specimen.SpecimenManagerNew;
import org.labkey.api.specimen.SpecimenSchema;
import org.labkey.api.specimen.Vial;
import org.labkey.api.specimen.importer.SimpleSpecimenImporter;
import org.labkey.api.specimen.importer.SpecimenColumn;
import org.labkey.api.specimen.model.SpecimenTablesProvider;
import org.labkey.api.study.ParticipantVisit;
import org.labkey.api.study.SpecimenChangeListener;
import org.labkey.api.study.SpecimenImportStrategyFactory;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.study.SpecimenTablesTemplate;
import org.labkey.api.study.SpecimenTransform;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.specimen.pipeline.SpecimenReloadJob;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * User: brittp
 * Date: Oct 2, 2007
 * Time: 3:38:28 PM
 */
public class SpecimenServiceImpl implements SpecimenService
{
    private final List<SpecimenImportStrategyFactory> _importStrategyFactories = new CopyOnWriteArrayList<>();
    private final Map<String, SpecimenTransform> _specimenTransformMap = new ConcurrentHashMap<>();
    private final List<SpecimenChangeListener> _changeListeners = new CopyOnWriteArrayList<>();

    private SpecimenRequestCustomizer _specimenRequestCustomizer = new SpecimenRequestCustomizer()
    {
        @Override
        public boolean allowEmptyRequests()
        {
            return false;
        }

        @Override
        public Integer getDefaultDestinationSiteId()
        {
            return null;
        }

        @Override
        public boolean omitTypeGroupingsWhenReporting()
        {
            return false;
        }

        @Override
        public boolean canChangeStatus(User user)
        {
            return true;
        }

        @Override
        public boolean hideRequestWarnings()
        {
            return false;
        }

        @Override
        public HtmlString getSubmittedMessage(Container c, int requestId)
        {
            return HtmlString.EMPTY_STRING;
        }
    };

    private class StudyParticipantVisit implements ParticipantVisit
    {
        private final Container _studyContainer;
        private final String _participantID;
        private final Double _visitID;
        private final String _specimenID;

        private ExpMaterial _material;
        private Date _date;

        public StudyParticipantVisit(Container studyContainer, String specimenID, String participantID, Double visitID, Date date)
        {
            _studyContainer = studyContainer;
            _specimenID = specimenID;
            _participantID = participantID;
            _visitID = visitID;
            _date = date;
        }

        @Override
        public Container getStudyContainer()
        {
            return _studyContainer;
        }

        @Override
        public String getParticipantID()
        {
            return _participantID;
        }

        @Override
        public Double getVisitID()
        {
            return _visitID;
        }

        @Override
        public String getSpecimenID()
        {
            return _specimenID;
        }

        @Override
        public Integer getCohortID()
        {
            throw new UnsupportedOperationException("Not Implemented for StudyParticipantVisit");
        }

        @Override @Nullable
        public ExpMaterial getMaterial(boolean createIfNeeded)
        {
            if (_material == null)
            {
                if (_specimenID != null)
                {
                    Lsid lsid = getSpecimenMaterialLsid(_studyContainer, _specimenID);
                    _material = ExperimentService.get().getExpMaterial(lsid.toString());
                    if (_material == null && createIfNeeded)
                    {
                        _material = ExperimentService.get().createExpMaterial(_studyContainer, lsid.toString(), _specimenID);
                        _material.save(null);
                    }
                }
                else
                {
                    String lsid = new Lsid(ParticipantVisit.ASSAY_RUN_MATERIAL_NAMESPACE, "Folder-" + _studyContainer.getRowId(), "Unknown").toString();
                    _material = ExperimentService.get().getExpMaterial(lsid);
                    if (_material == null && createIfNeeded)
                    {
                        _material = ExperimentService.get().createExpMaterial(_studyContainer, lsid, "Unknown");
                        _material.save(null);
                    }
                }
            }
            return _material;
        }

        @Override
        public Date getDate()
        {
            return _date;
        }

        public void setDate(Date date)
        {
            _date = date;
        }
    }

    @Override
    public ParticipantVisit getSampleInfo(Container studyContainer, User user, String sampleId)
    {
        Vial match = SpecimenManagerNew.get().getVial(studyContainer, user, sampleId);
        if (match != null)
            return new StudyParticipantVisit(studyContainer, sampleId, match.getPtid(), match.getVisitValue(), match.getDrawTimestamp());
        else
            return new StudyParticipantVisit(studyContainer, sampleId, null, null, null);
    }

    @Override
    public Set<ParticipantVisit> getSampleInfo(Container studyContainer, User user, String participantId, Date date)
    {
        if (null != studyContainer && null != StringUtils.trimToNull(participantId) && null != date)
        {
            List<Vial> matches = SpecimenManager.get().getVials(studyContainer, user, participantId, date);
            if (matches.size() > 0)
            {
                Set<ParticipantVisit> result = new HashSet<>();
                for (Vial match : matches)
                {
                    result.add(new StudyParticipantVisit(studyContainer, match.getGlobalUniqueId(), participantId, match.getVisitValue(), match.getDrawTimestamp()));
                }
                return result;
            }
        }
        
        return Collections.singleton(new StudyParticipantVisit(studyContainer, null, participantId, null, date));
    }

    @Override
    public Set<ParticipantVisit> getSampleInfo(Container studyContainer, User user, String participantId, Double visit)
    {
        if (null != studyContainer && null != StringUtils.trimToNull(participantId) && null != visit)
        {
            List<Vial> matches = SpecimenManager.get().getVials(studyContainer, user, participantId, visit);
            if (matches.size() > 0)
            {
                Set<ParticipantVisit> result = new HashSet<>();
                for (Vial match : matches)
                {
                    result.add(new StudyParticipantVisit(studyContainer, match.getGlobalUniqueId(), participantId, match.getVisitValue(), match.getDrawTimestamp()));
                }
                return result;
            }
        }
        return Collections.singleton(new StudyParticipantVisit(studyContainer, null, participantId, visit, null));
    }

    @Override
    public Set<Pair<String, Date>> getSampleInfo(Container studyContainer, User user, boolean truncateTime)
    {
        TableInfo tableInfoSpecimen = SpecimenSchema.get().getTableInfoSpecimen(studyContainer);

        String dateExpr = truncateTime ? tableInfoSpecimen.getSqlDialect().getDateTimeToDateCast("DrawTimestamp") : "DrawTimestamp";
        SQLFragment sql = new SQLFragment("SELECT DISTINCT PTID, " + dateExpr + " AS DrawTimestamp FROM ");
        sql.append(tableInfoSpecimen.getSelectName()).append(";");

        final Set<Pair<String, Date>> sampleInfo = new HashSet<>();

        new SqlSelector(tableInfoSpecimen.getSchema(), sql).forEach(rs -> {
            String participantId = rs.getString("PTID");
            Date drawDate = rs.getDate("DrawTimestamp");
            if (participantId != null && drawDate != null)
                sampleInfo.add(new Pair<>(participantId, drawDate));
        });

        return sampleInfo;
    }

    @Override
    public Set<Pair<String, Double>> getSampleInfo(Container studyContainer, User user)
    {
        TableInfo tableInfoSpecimen = SpecimenSchema.get().getTableInfoSpecimen(studyContainer);

        SQLFragment sql = new SQLFragment("SELECT DISTINCT PTID, VisitValue FROM ");
        sql.append(tableInfoSpecimen.getSelectName()).append(";");

        final Set<Pair<String, Double>> sampleInfo = new HashSet<>();

        new SqlSelector(tableInfoSpecimen.getSchema(), sql).forEach(rs -> {
            String participantId = rs.getString("PTID");
            double visit = rs.getDouble("VisitValue");    // Never returns null
            if (participantId != null)
                sampleInfo.add(new Pair<>(participantId, visit));
        });

        return sampleInfo;
    }

    @Override
    public Lsid getSpecimenMaterialLsid(@NotNull Container studyContainer, @NotNull String id)
    {
        return new Lsid(StudyService.SPECIMEN_NAMESPACE_PREFIX, "Folder-" + studyContainer.getRowId(), id);
    }

    @Override
    public String getActiveSpecimenImporter(@NotNull Container container)
    {
        PropertyManager.PropertyMap props = PropertyManager.getProperties(container, "enabledSpecimenImporter");
        String activeTransform = props.get("active");
        if (null == activeTransform)
            return null;

        // In case module with active transform has been disabled in container
        Collection<SpecimenTransform> transforms = getSpecimenTransforms(container);
        boolean noTransformsActive = transforms.stream().noneMatch(transform -> activeTransform.equals(transform.getName()));

        if (noTransformsActive)
            return null;
        return props.get("active");
    }

    @Override
    public void importSpecimens(User user, Container container, List<Map<String, Object>> rows, boolean merge) throws IOException, ValidationException
    {
        // CONSIDER: move ShowUploadSpecimensAction validation to importer.process()
        SimpleSpecimenImporter importer = new SimpleSpecimenImporter(container, user);
        rows = importer.fixupSpecimenRows(rows);
        importer.process(rows, merge);
    }

    @Override
    public void registerSpecimenImportStrategyFactory(SpecimenImportStrategyFactory factory)
    {
        // Insert at the start (we generally want reverse dependency order)
        _importStrategyFactories.add(0, factory);
    }

    @Override
    public Collection<SpecimenImportStrategyFactory> getSpecimenImportStrategyFactories()
    {
        return _importStrategyFactories;
    }

    @Override
    public void registerSpecimenTransform(SpecimenTransform transform)
    {
        if (!_specimenTransformMap.containsKey(transform.getName()))
            _specimenTransformMap.put(transform.getName(), transform);
        else
            throw new IllegalStateException("A specimen transform implementation with the name: " + transform.getName() + " is already registered");
    }

    @Override
    public Collection<SpecimenTransform> getSpecimenTransforms(Container container)
    {
        List<SpecimenTransform> transforms = new ArrayList<>();

        for (SpecimenTransform transform : _specimenTransformMap.values())
        {
            if (transform.isValid(container))
                transforms.add(transform);
        }
        return transforms;
    }

    @Nullable
    @Override
    public SpecimenTransform getSpecimenTransform(String name)
    {
        return _specimenTransformMap.get(name);
    }

    @Override
    public PipelineJob createSpecimenReloadJob(Container container, User user, SpecimenTransform transform, @Nullable ActionURL url) throws ValidationException
    {
        PipeRoot root = PipelineService.get().findPipelineRoot(container);
        SpecimenReloadJob job = new SpecimenReloadJob(new ViewBackgroundInfo(container, user, url), root, transform.getName());

        job.setExternalImportConfig(transform.getExternalImportConfig(container, user));

        return job;
    }

    @Override
    public void registerSpecimenChangeListener(SpecimenChangeListener listener)
    {
        _changeListeners.add(listener);
    }

    private static final SpecimenTablesTemplate _specimenTablesTemplate = new DefaultSpecimenTablesTemplate();

    @Nullable
    @Override
    public TableInfo getTableInfoVial(Container container)
    {
        return SpecimenSchema.get().getTableInfoVialIfExists(container);
    }

    @Nullable
    @Override
    public TableInfo getTableInfoSpecimen(Container container)
    {
        return SpecimenSchema.get().getTableInfoSpecimenIfExists(container);
    }

    @Nullable
    @Override
    public TableInfo getTableInfoSpecimenEvent(Container container)
    {
        return SpecimenSchema.get().getTableInfoSpecimenEventIfExists(container);
    }

    @Override
    public SpecimenTablesTemplate getSpecimenTablesTemplate()
    {
        return _specimenTablesTemplate;
    }

    @Override
    public Domain getSpecimenVialDomain(Container container, User user)
    {
        SpecimenTablesProvider specimenTablesProvider = new SpecimenTablesProvider(container, user, null);
        return specimenTablesProvider.getDomain("Vial", false);
    }

    @Override
    public Domain getSpecimenEventDomain(Container container, User user)
    {
        SpecimenTablesProvider specimenTablesProvider = new SpecimenTablesProvider(container, user, null);
        return specimenTablesProvider.getDomain("SpecimenEvent", false);
    }

    @Override
    public Map<String, String> getSpecimenImporterTsvColumnMap()
    {
        Map<String, String> colNameMap = new HashMap<>();
        for (SpecimenColumn specimenColumn : SpecimenColumns.BASE_SPECIMEN_COLUMNS)
        {
            colNameMap.put(specimenColumn.getDbColumnName(), specimenColumn.getPrimaryTsvColumnName());
        }
        return colNameMap;
    }

    @Override
    public SpecimenRequestCustomizer getRequestCustomizer()
    {
        return _specimenRequestCustomizer;
    }

    @Override
    public void registerRequestCustomizer(SpecimenRequestCustomizer customizer)
    {
        _specimenRequestCustomizer = customizer;
    }

    @Override
    public void fireSpecimensChanged(Container c, User user, Logger logger)
    {
        for (SpecimenChangeListener l : _changeListeners)
            l.specimensChanged(c, user, logger);
    }
}
