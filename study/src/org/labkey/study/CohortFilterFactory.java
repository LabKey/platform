/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
import org.labkey.api.study.CohortFilter;
import org.labkey.api.study.CohortFilter.Type;
import org.labkey.api.study.Config;
import org.labkey.api.study.Params;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.StudyManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * NOTE (MAB): with 13.1 we use regular URL filters for dataregions and NOT use CohortFilter to append additional filters to the query.
 * CohortFilter can still be used for non-data region usages, such as overview or reporting, but these should probably migrate to using
 * ParticipantGroup filtering UI
 */
public class CohortFilterFactory
{
    enum CohortFilterField
    {
        currentCohortLabel("ParticipantId/Cohort/Label"){
            @Override
            void apply(Config c, Pair<String,String> entry)
            {
                c.setType(Type.PTID_CURRENT);
                c.setLabel(entry.getValue());
            }
        },
        currentCohortRowId("ParticipantId/Cohort/RowId"){
            @Override
            void apply(Config c, Pair<String,String> entry)
            {
                c.setType(Type.PTID_CURRENT);
                try { c.setCohortId(Integer.parseInt(entry.getValue())); } catch (Exception x) {/* */}
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
                c.setLabel(entry.getValue());
            }
        },
        initialCohortRowId("ParticipantId/InitialCohort/RowId"){
            @Override
            void apply(Config c, Pair<String,String> entry)
            {
                c.setType(Type.PTID_INITIAL);
                try { c.setCohortId(Integer.parseInt(entry.getValue())); } catch (Exception x) {/* */}
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
                c.setLabel(entry.getValue());
            }
        },
        visitCohortRowId("ParticipantVisit/Cohort/RowId"){
            @Override
            void apply(Config c, Pair<String,String> entry)
            {
                c.setType(Type.DATA_COLLECTION);
                try { c.setCohortId(Integer.parseInt(entry.getValue())); } catch (Exception x) {/* */}
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
                c.setLabel(entry.getValue());
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

        void applyIfMatches(Config c, Pair<String,String> entry)
        {
            String name = entry.getKey();
            int tilde = name.indexOf("~");
            String op = -1==tilde ? null : name.substring(tilde);
            if  (null != op)
            {
                if (!"~eq".equals(op) && !(fk.getName().equals("Enrolled") && "~neqornull".equals(op)))
                {
                    c.setAmbiguous(true);
                    return;
                }
            }
            apply(c, entry);
        }

        abstract void apply(Config c, Pair<String,String> entry);
    }


    public static final SingleCohortFilter UNASSIGNED = new SingleCohortFilter.UnassignedCohort();


    // this is to speed up url parameter inspection
    private static final HashMap<FieldKey,Object> cohortParameters= new HashMap<>();
    static
    {
        for (Params p : Params.values())
            cohortParameters.put(new FieldKey(null, p.name()), p);
        for (CohortFilterField f : CohortFilterField.values())
        {
            cohortParameters.put(f.fk, f);
            if (f.fk.getName().equals("RowId"))
                cohortParameters.put(f.fk.getParent(), f);
        }
    }



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


    // return non-null if this is part of a cohort filter specification (or user filter on the same fields)
    // does not check matches(), because isCohortFilterParameterName() wants to know about any filters it may want to clear
    private static FieldKey _matchCohortFilterParameter(Study study, String name, String dataregion)
    {
        int start = 0;
        if (name.contains("."))
        {
            if (!StringUtils.startsWithIgnoreCase(name, dataregion + "."))
                return null;
            start = dataregion.length()+1;
        }
        int tilde = name.indexOf('~');
        String op = null;
        if (tilde == -1)
            tilde = name.length();
        else
            op = name.substring(tilde);
        String field = name.substring(start, tilde);
        FieldKey fk = _normalizeParticipantFieldKey(study, field);

        Object o = cohortParameters.get(fk);
        if (null == o)
            return null;
        return (o instanceof CohortFilterField || null==op) ? fk : null;
    }


    public static boolean isCohortFilterParameterName(Study study, String name, String dataregion)
    {
        return null != _matchCohortFilterParameter(study, name, dataregion);
    }


    public static boolean parseCohortUrlParameter(ActionURL url, Study study, String dataregion, Config config /* out */)
    {
        List<Pair<String, String>> entries = url.getParameters();
        for (Pair<String,String> entry : entries)
        {
            FieldKey fieldKey = _matchCohortFilterParameter(study, entry.getKey(), dataregion);
            if (null == fieldKey)
                continue;
            Object o = cohortParameters.get(fieldKey);
            if (o instanceof Params)
            {
                ((Params)o).apply(config, entry);
            }
            else if (o instanceof CohortFilterField)
            {
                ((CohortFilterField)o).applyIfMatches(config, entry);
            }
            /*
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
                continue;

            String op = name.substring(tilde);
            String field = name.substring(dataregion.length() + 1, tilde);
            FieldKey fk = _normalizeParticipantFieldKey(study, field);

            for (CohortFilterField ff : CohortFilterField.values())
            {
                if (ff.matches(fk, op))
                {
                    ff.apply(config, entry);
                    break;
                }
            }
            */
        }
        if ((config.getLabel() != null || config.getCohortId() != null) && config.getEnrolled() == Boolean.TRUE)
            config.setAmbiguous(true);
        return config.getType() != null && (config.getCohortId() != null || config.getEnrolled() == Boolean.TRUE || config.getLabel() != null);
    }


    private static FieldKey _normalizeParticipantFieldKey(Study study, String s)
    {
        if (StringUtils.isEmpty(s))
            return null;

        String subject = study.getSubjectColumnName();
        String subjectVisit = StudyService.get().getSubjectVisitTableName(study.getContainer());

        FieldKey fk;
        try
        {
            fk = FieldKey.decode(s);
        }
        catch (IllegalArgumentException badFieldKey)
        {
            return null; // Don't choke on malformed URL parameter
        }
        List<String> parts = fk.getParts();
        if (parts.size() > 0)
        {
            String first = parts.get(0);
            if (StringUtils.equalsIgnoreCase(first, subject))
            {
                ArrayList<String> t = new ArrayList<>(parts);
                t.set(0, "ParticipantId");
                fk = FieldKey.fromParts(t);
            }
            else if (StringUtils.equalsIgnoreCase(first, subjectVisit))
            {
                ArrayList<String> t = new ArrayList<>(parts);
                t.set(0, "ParticipantVisit");
                fk = FieldKey.fromParts(t);
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

        if (null != config.getType() && !config.getAmbiguous())
        {
            if (null != config.getCohortId() || null != config.getLabel())
            {
                if (config.getType() == UNASSIGNED.getType() && config.getLabel() == null && (config.getCohortId() == null || config.getCohortId() == UNASSIGNED.getCohortId()))
                    return UNASSIGNED;

                return new SingleCohortFilter(config);
            }
            else if (config.getEnrolled() == Boolean.TRUE)
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
        config.setType(type);
        config.setEnrolled(Boolean.TRUE);
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
