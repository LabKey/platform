/*
 * Copyright (c) 2012 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.study.CohortFilter.Type;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.StudyManager;

import java.util.List;
import java.util.Set;

/**
 * User: adam
 * Date: 8/28/12
 * Time: 3:54 PM
 *
 * NOTE (MAB): with 13.1 we use regular URL filters for dataregions and NOT use CohortFilter to append additional filters to the query.
 * CohortFilter can still be used for non-data region usages, such as overview or reporting, but these should probably migrate to using
 * ParticipantGroup filtering UI
 */
public class CohortFilterFactory
{
    public static enum Params
    {
        cohortFilterType{
            @Override
            void apply(Config c, Pair<String, String> entry)
            {
                c.oldstyle = true;
                c.setType(entry.getValue());
            }
        },
        cohortId{
            @Override
            void apply(Config c, Pair<String, String> entry)
            {
                c.oldstyle = true;
                c.setCohortId(entry.getValue());
            }
        },
        cohortEnrolled{
            @Override
            void apply(Config c, Pair<String, String> entry)
            {
                c.oldstyle = true;
                c.setEnrolled(entry.getValue());
            }
        };

        abstract void apply(Config c, Pair<String,String> value);
    }

    public static final SingleCohortFilter UNASSIGNED = new SingleCohortFilter.UnassignedCohort();

    public static ActionURL clearURLParameters(Study study, ActionURL url, String dataregion)
    {
        if (!StringUtils.isEmpty(dataregion))
        {
            Set<String> names = url.getParameterMap().keySet();
            for (String name : names)
                if (isCohortFilterParameterName(study, name, dataregion))
                    url.deleteParameter(name);
        }
        url.deleteParameter(Params.cohortFilterType);
        url.deleteParameter(Params.cohortId);
        url.deleteParameter(Params.cohortEnrolled);
        return url;
    }


    public static class Config
    {
        Type type;
        Integer cohortId;
        String label;
        Boolean enrolled;

        public Type getType()
        {
            return type;
        }

        public void setType(String s)
        {
            Type type = null;
            try { type = Type.valueOf(s); } catch (Exception x) {/* */}
            setType(type);
        }

        public void setType(Type type)
        {
            if (null == type)
                return;
            if (null != this.type)
                this.ambiguous = true;
            this.type = type;
        }

        public Integer getCohortId()
        {
            return cohortId;
        }

        public void setCohortId(String s)
        {
            Integer i = null;
            try { i = Integer.parseInt(s); } catch (Exception x) {/* */}
            setCohortId(i);
        }

        public void setCohortId(Integer cohortId)
        {
            if (null == cohortId)
                return;
            if (null != this.label || null != this.cohortId)
                this.ambiguous = true;
            this.cohortId = cohortId;
        }

        public String getLabel()
        {
            return label;
        }

        public void setLabel(String label)
        {
            if (null == label)
                return;
            if (null != this.label || null != this.cohortId)
                this.ambiguous = true;
            this.label = label;
        }

        public Boolean getEnrolled()
        {
            return enrolled;
        }

        public void setEnrolled(String s)
        {
            Boolean b = null;
            try { b = ConvertHelper.convert(s, Boolean.class); } catch (Exception x) {/* */}
            setEnrolled(b);
        }

        public void setEnrolled(Boolean enrolled)
        {
            if (null == enrolled)
                return;
            if (null != this.enrolled)
                ambiguous = true;
            this.enrolled = enrolled;
        }

        // detect whether the filter might not have been created via the cohort picker
        boolean oldstyle=false;
        boolean ambiguous=false;
    }


    static enum CohortFilterField
    {
        currentCohortLabel("ParticipantId/Cohort/Label"){
            @Override
            void apply(Config c, Pair<String,String> entry)
            {
                c.setType(Type.PTID_CURRENT);
                c.label = entry.getValue();
            }
        },
        currentCohortRowId("ParticipantId/Cohort/RowId"){
            @Override
            void apply(Config c, Pair<String,String> entry)
            {
                c.setType(Type.PTID_CURRENT);
                try { c.cohortId = Integer.parseInt(entry.getValue()); } catch (Exception x) {/* */}
            }
        },
        currentCohortEnrolled("ParticipantId/Cohort/Enrolled"){
            @Override
            void apply(Config c, Pair<String,String> entry)
            {
                c.setType(Type.PTID_CURRENT);
                applyEnrolled(c, entry);
            }
        },
        initialCohortLabel("ParticipantId/InitialCohort/Label"){
            @Override
            void apply(Config c, Pair<String,String> entry)
            {
                c.setType(Type.PTID_INITIAL);
                c.label = entry.getValue();
            }
        },
        initialCohortRowId("ParticipantId/InitialCohort/RowId"){
            @Override
            void apply(Config c, Pair<String,String> entry)
            {
                c.setType(Type.PTID_INITIAL);
                try { c.cohortId = Integer.parseInt(entry.getValue()); } catch (Exception x) {/* */}
            }
        },
        initialCohortEnrolled("ParticipantId/InitialCohort/Enrolled"){
            @Override
            void apply(Config c, Pair<String,String> entry)
            {
                c.setType(Type.PTID_INITIAL);
                applyEnrolled(c, entry);
            }
        },
        visitCohortLabel("ParticipantVisit/Cohort/Label"){
            @Override
            void apply(Config c, Pair<String,String> entry)
            {
                c.setType(Type.DATA_COLLECTION);
                c.label = entry.getValue();
            }
        },
        visitCohortRowId("ParticipantVisit/Cohort/RowId"){
            @Override
            void apply(Config c, Pair<String,String> entry)
            {
                c.setType(Type.DATA_COLLECTION);
                try { c.cohortId = Integer.parseInt(entry.getValue()); } catch (Exception x) {/* */}
            }
        },
        visitCohortEnrolled("ParticipantVisit/Cohort/Enrolled"){
            @Override
            void apply(Config c, Pair<String,String> entry)
            {
                c.setType(Type.DATA_COLLECTION);
                applyEnrolled(c, entry);
            }
        },
        // NOTE: I believe CollectionCohort is redundant with ParticipantVisit/Cohort
        collectionCohortLabel("CollectionCohort/Label"){
            @Override
            void apply(Config c, Pair<String,String> entry)
            {
                c.setType(Type.DATA_COLLECTION);
                c.label = entry.getValue();
            }
        },
        collectionCohortRowId("CollectionCohort/RowId"){
            @Override
            void apply(Config c, Pair<String,String> entry)
            {
                c.setType(Type.DATA_COLLECTION);
                applyEnrolled(c, entry);
            }
        },
        collectionCohortEnrolled("CollectionCohort/Enrolled"){
            @Override
            void apply(Config c, Pair<String,String> entry)
            {
                c.setType(Type.DATA_COLLECTION);
                applyEnrolled(c, entry);
            }
        };

        final FieldKey fk;

        CohortFilterField(String s)
        {
            fk = FieldKey.decode(s);
        }

        boolean matches(FieldKey fk, String op)
        {
            if  (null != op)
            {
                if (!"~eq".equals(op) && !(fk.getName().equals("Enrolled") && "~neqornull".equals(op)))
                    return false;
            }
           return this.fk.equals(fk) || this.fk.getName().equals("RowId") && this.fk.getParent().equals(fk);
        }

        private static void applyEnrolled(Config c, Pair<String,String> entry)
        {
            Boolean b = null;
            try { b = ConvertHelper.convert(entry.getValue(), Boolean.class); } catch (Exception x) {/* */}
            if (null != b)
            {
                if (entry.getKey().endsWith("~neqornull"))
                    c.setEnrolled(!b);
                else
                    c.setEnrolled(b);
            }
        }

        abstract void apply(Config c, Pair<String,String> value);
    }



    public static boolean isCohortFilterParameterName(Study study, String name, String dataregion)
    {
        for (Params param : Params.values())
        {
            if (name.equals(param.name()))
                return true;
        }
        if (StringUtils.isEmpty(dataregion) || !StringUtils.startsWithIgnoreCase(name, dataregion + "."))
            return false;
        int tilde = name.indexOf('~');
        if (-1 == tilde || -1 == name.indexOf('/'))
            return false;
        String field = name.substring(dataregion.length()+1, tilde);
        FieldKey fk = _normalizeParticipantFieldKey(study, field);
        for (CohortFilterField ff : CohortFilterField.values())
        {
            if (ff.matches(fk, null))
                return true;
        }
        return false;
    }


    public static boolean parseCohortUrlParameter(ActionURL url, Study study, String dataregion, Config config /* out */)
    {
        for (Pair<String,String> entry : url.getParameters())
        {
            String name = entry.getKey();
            if (Params.cohortId.name().equals(name))
            {
                Params.cohortId.apply(config, entry);
                continue;
            }
            if (Params.cohortFilterType.name().equals(name))
            {
                Params.cohortFilterType.apply(config, entry);
                continue;
            }
            if (Params.cohortEnrolled.name().equals(name))
            {
                Params.cohortEnrolled.apply(config, entry);
                continue;
            }

            if (StringUtils.isEmpty(dataregion) || !StringUtils.startsWithIgnoreCase(name, dataregion + "."))
                continue;
            int tilde = name.indexOf('~');
            if (-1 == tilde || -1 == name.indexOf('/'))
                return false;

            String op = name.substring(tilde);
            String field = name.substring(dataregion.length() + 1, tilde);
            FieldKey fk = _normalizeParticipantFieldKey(study, field);

            for (CohortFilterField ff : CohortFilterField.values())
            {
                if (ff.matches(fk, op))
                    ff.apply(config, entry);
            }
        }
        return config.type != null && (config.cohortId != null || config.enrolled == Boolean.TRUE || config.label != null);
    }


    private static FieldKey _normalizeParticipantFieldKey(Study study, String s)
    {
        String subject = study.getSubjectColumnName();
        String subjectVisit = StudyService.get().getSubjectVisitTableName(study.getContainer());

        FieldKey fk = FieldKey.decode(s);
        List<String> parts = fk.getParts();
        if (parts.size() > 0)
        {
            String first = parts.get(0);
            if (StringUtils.equalsIgnoreCase(first, subject))
            {
                parts.set(0, "ParticipantId");
                fk = FieldKey.fromParts(parts);
            }
            else if (StringUtils.equalsIgnoreCase(first, subjectVisit))
            {
                parts.set(0, "ParticipantVisit");
                fk = FieldKey.fromParts(parts);
            }
        }
        return fk;
    }


    /*
     * Use this version for cohort filters not associated with a dataregion (e.g. overview and specimen report)
     * CONSIDER: migrate to participantgroup picker?
     */
    public static @Nullable CohortFilter getFromURL(Container c, User user, ActionURL url)
    {
        return getFromURL(c, user, url, null);
    }


    public static @Nullable CohortFilter getFromURL(Container c, User user, ActionURL url, @Nullable String dataregion)
    {
        // If you're not allowed to see cohorts then we shouldn't filter by anything
        if (!StudyManager.getInstance().showCohorts(c, user))
            return null;

        Study study = StudyManager.getInstance().getStudy(c);
        if (null == study)
            return null;

        Config config =  new Config();
        if (!parseCohortUrlParameter(url, study, dataregion, config))
            return null;

        if (null != config.type)
        {
            if (null != config.cohortId || null != config.label)
            {
                if (config.type == UNASSIGNED.getType() && config.label==null && (config.cohortId == null || config.cohortId == UNASSIGNED.getCohortId()))
                    return UNASSIGNED;

                return new SingleCohortFilter(config);
            }
            else if (config.enrolled == Boolean.TRUE)
            {
                return getEnrolledCohortFilter(c, user, config);
            }
        }

        return null;
    }

    // If BOTH enrolled and unenrolled cohorts exist, then return a MultipleCohortFilter that selects the enrolled and
    // unassigned participants. Otherwise, return null, which means all participants will be shown.

    public static @Nullable CohortFilter getEnrolledCohortFilter(Container c, User user, Type type)
    {
        Config config = new Config();
        config.type = type;
        config.enrolled = Boolean.TRUE;
        return getEnrolledCohortFilter(c, user, config);
    }

    public static @Nullable CohortFilter getEnrolledCohortFilter(Container c, User user, Config config)
    {
        boolean unenrolled = false;
        boolean enrolled = false;

        for (CohortImpl cohort : StudyManager.getInstance().getCohorts(c, user))
        {
            if (cohort.isEnrolled())
                enrolled = true;
            else
                unenrolled = true;
        }

        if (unenrolled && enrolled)
            return new MultipleCohortFilter(config, true, "enrolled or unassigned");
        else
            return null;
    }
}
