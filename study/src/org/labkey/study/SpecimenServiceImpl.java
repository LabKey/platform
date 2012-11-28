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

package org.labkey.study;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Table;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.study.ParticipantVisit;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.study.importer.SimpleSpecimenImporter;
import org.labkey.study.model.Specimen;
import org.labkey.study.controllers.samples.AutoCompleteAction;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * User: brittp
 * Date: Oct 2, 2007
 * Time: 3:38:28 PM
 */
public class SpecimenServiceImpl implements SpecimenService.Service
{
    private class StudyParticipantVisit implements ParticipantVisit
    {
        private Container _studyContainer;
        private String _participantID;
        private Double _visitID;
        private String _specimenID;
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

        public String getParticipantID()
        {
            return _participantID;
        }

        public Double getVisitID()
        {
            return _visitID;
        }

        public String getSpecimenID()
        {
            return _specimenID;
        }

        public Integer getCohortID()
        {
            throw new UnsupportedOperationException("Not Implemented for StudyParticipantVisit");
        }

        public ExpMaterial getMaterial()
        {
            if (_material == null)
            {
                if (_specimenID != null)
                {
                    Lsid lsid = getSpecimenMaterialLsid(_studyContainer, _specimenID);
                    _material = ExperimentService.get().getExpMaterial(lsid.toString());
                    if (_material == null)
                    {
                        _material = ExperimentService.get().createExpMaterial(_studyContainer, lsid.toString(), _specimenID);
                        _material.save(null);
                    }
                }
                else
                {
                    String lsid = new Lsid(ParticipantVisit.ASSAY_RUN_MATERIAL_NAMESPACE, "Folder-" + _studyContainer.getRowId(), "Unknown").toString();
                    _material = ExperimentService.get().getExpMaterial(lsid);
                    if (_material == null)
                    {
                        _material = ExperimentService.get().createExpMaterial(_studyContainer, lsid, "Unknown");
                        _material.save(null);
                    }
                }
            }
            return _material;
        }

        public Date getDate()
        {
            return _date;
        }

        public void setDate(Date date)
        {
            _date = date;
        }
    }

    public ParticipantVisit getSampleInfo(Container studyContainer, String sampleId) throws SQLException
    {
        Specimen match = SampleManager.getInstance().getSpecimen(studyContainer, sampleId);
        if (match != null)
            return new StudyParticipantVisit(studyContainer, sampleId, match.getPtid(), match.getVisitValue(), match.getDrawTimestamp());
        else
            return new StudyParticipantVisit(studyContainer, sampleId, null, null, null);
    }

    public Set<ParticipantVisit> getSampleInfo(Container studyContainer, String participantId, Date date) throws SQLException
    {
        if (null != studyContainer && null != StringUtils.trimToNull(participantId) && null != date)
        {
            Specimen[] matches = SampleManager.getInstance().getSpecimens(studyContainer, participantId, date);
            if (matches.length > 0)
            {
                Set<ParticipantVisit> result = new HashSet<ParticipantVisit>();
                for (Specimen match : matches)
                {
                    result.add(new StudyParticipantVisit(studyContainer, match.getGlobalUniqueId(), participantId, match.getVisitValue(), match.getDrawTimestamp()));
                }
                return result;
            }
        }
        
        return Collections.<ParticipantVisit>singleton(new StudyParticipantVisit(studyContainer, null, participantId, null, date));
    }

    public Set<ParticipantVisit> getSampleInfo(Container studyContainer, String participantId, Double visit) throws SQLException
    {
        if (null != studyContainer && null != StringUtils.trimToNull(participantId) && null != visit)
        {
            Specimen[] matches = SampleManager.getInstance().getSpecimens(studyContainer, participantId, visit);
            if (matches.length > 0)
            {
                Set<ParticipantVisit> result = new HashSet<ParticipantVisit>();
                for (Specimen match : matches)
                {
                    result.add(new StudyParticipantVisit(studyContainer, match.getGlobalUniqueId(), participantId, match.getVisitValue(), match.getDrawTimestamp()));
                }
                return result;
            }
        }
        return Collections.<ParticipantVisit>singleton(new StudyParticipantVisit(studyContainer, null, participantId, visit, null));
    }

    public String getCompletionURLBase(Container studyContainer, SpecimenService.CompletionType type)
    {
        if (studyContainer == null)
            return null;

        ActionURL url = new ActionURL(AutoCompleteAction.class, studyContainer);
        url.addParameter("type", type.name());
        return url.getLocalURIString() + "&prefix=";
    }

    public Set<Pair<String, Date>> getSampleInfo(Container studyContainer, boolean truncateTime) throws SQLException
    {
        String dateExpr = truncateTime ? StudySchema.getInstance().getSqlDialect().getDateTimeToDateCast("DrawTimestamp") : "DrawTimestamp";
        SQLFragment sql = new SQLFragment("SELECT DISTINCT PTID, " + dateExpr + " AS DrawTimestamp FROM " +
            StudySchema.getInstance().getTableInfoSpecimen() + " WHERE Container = ?;", studyContainer.getId());

        Set<Pair<String, Date>> sampleInfo = new HashSet<Pair<String, Date>>();
        ResultSet rs = null;
        try
        {
            rs = Table.executeQuery(StudySchema.getInstance().getSchema(), sql);
            while (rs.next())
            {
                String participantId = rs.getString("PTID");
                Date drawDate = rs.getDate("DrawTimestamp");
                if (participantId != null && drawDate != null)
                sampleInfo.add(new Pair<String, Date>(participantId, drawDate));
            }
        }
        finally
        {
            if (rs != null)
                try { rs.close(); } catch (SQLException e) {}
        }
        return sampleInfo;
    }

    public Set<Pair<String, Double>> getSampleInfo(Container studyContainer) throws SQLException
    {
        SQLFragment sql = new SQLFragment("SELECT DISTINCT PTID, VisitValue FROM " +
            StudySchema.getInstance().getTableInfoSpecimen() + " WHERE Container = ?;", studyContainer.getId());

        Set<Pair<String, Double>> sampleInfo = new HashSet<Pair<String, Double>>();
        ResultSet rs = null;
        try
        {
            rs = Table.executeQuery(StudySchema.getInstance().getSchema(), sql);
            while (rs.next())
            {
                String participantId = rs.getString("PTID");
                Double visit = rs.getDouble("VisitValue");
                if (participantId != null && visit != null)
                sampleInfo.add(new Pair<String, Double>(participantId, visit));
            }
        }
        finally
        {
            if (rs != null)
                try { rs.close(); } catch (SQLException e) {}
        }
        return sampleInfo;
    }

    public Lsid getSpecimenMaterialLsid(Container studyContainer, String id)
    {
        return new Lsid(StudyService.SPECIMEN_NAMESPACE_PREFIX, "Folder-" + studyContainer.getRowId(), id);
    }

    @Override
    public void importSpecimens(User user, Container container, List<Map<String, Object>> rows, boolean merge) throws SQLException, IOException, ValidationException
    {
        // CONSIDER: move ShowUploadSpecimensAction validation to importer.process()
        SimpleSpecimenImporter importer = new SimpleSpecimenImporter();
        rows = importer.fixupSpecimenRows(rows);
        importer.process(user, container, rows, merge);
    }
}
