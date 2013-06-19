/*
 * Copyright (c) 2010-2013 LabKey Corporation
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

package gwt.client.org.labkey.study.designer.client.model;

import com.google.gwt.user.client.rpc.IsSerializable;

import java.util.*;

/**
 * User: Mark Igra
 * Date: Dec 14, 2006
 * Time: 1:34:20 PM
 */
public class GWTImmunizationSchedule implements Schedule, IsSerializable
{
    Map<GWTCohort,Map<GWTTimepoint,GWTImmunization>> schedule = new HashMap<GWTCohort,Map<GWTTimepoint,GWTImmunization>>();
    List<GWTTimepoint> timepoints = new ArrayList<GWTTimepoint>();

    public  GWTImmunizationSchedule()
    {
        
    }

    public boolean equals(Object o)
    {
        if (null == o)
            return false;

        GWTImmunizationSchedule is = (GWTImmunizationSchedule) o;
        return schedule.equals(is.schedule) && timepoints.equals(is.timepoints);
    }

    public void setImmunization(GWTCohort group, GWTTimepoint tp, GWTImmunization i)
    {
        if (!timepoints.contains(tp))
            timepoints.add(tp);
        
        Map<GWTTimepoint,GWTImmunization> tpimMap = schedule.get(group);
        if (null == tpimMap)
        {
            tpimMap = new HashMap<GWTTimepoint,GWTImmunization>();
            schedule.put(group, tpimMap);
        }
        tpimMap.put(tp, i);
    }


    public void removeImmunization(GWTCohort group, GWTTimepoint tp)
    {
        Map<GWTTimepoint,GWTImmunization> tpimMap = schedule.get(group);
        if (null == tpimMap)
            return;

        tpimMap.remove(tp);

    }

    public GWTImmunization getImmunization(GWTCohort group, GWTTimepoint tp)
    {
        Map<GWTTimepoint,GWTImmunization> tpimMap = schedule.get(group);
        if (null == tpimMap)
            return null;

        return tpimMap.get(tp);
    }

    public void removeGroup(GWTCohort group)
    {
        schedule.remove(group);
    }

    public void removeTimepoint(GWTTimepoint tp)
    {
        for (GWTCohort group : schedule.keySet())
        {
            Map<GWTTimepoint,GWTImmunization> tpimMap = schedule.get(group);
            if (null == tpimMap)
                continue;

            tpimMap.remove(tp);
        }
        timepoints.remove(tp);
    }

    public List<GWTTimepoint> getTimepoints()
    {
        return timepoints;
    }

    public GWTTimepoint getTimepoint(int i)
    {
        if (i >= timepoints.size())
            return null;

        return timepoints.get(i);
    }
    public void addTimepoint(GWTTimepoint tp)
    {
        timepoints.add(tp);
    }

    public void addTimepoint(int index, GWTTimepoint tp)
    {
        timepoints.add(index, tp);
    }

    public void removeTimepoint(int index)
    {
        GWTTimepoint tp = timepoints.get(index);
        removeTimepoint(tp);
    }

    public void removeImmunogen(GWTImmunogen immunogen)
    {
        for (GWTCohort gwtCohort : schedule.keySet())
        {
            for (GWTTimepoint gwtTimepoint : timepoints)
            {
                GWTImmunization im = getImmunization(gwtCohort, gwtTimepoint);
                if (null != im)
                    im.immunogens.remove(immunogen);
            }
        }
    }

    public void removeAdjuvant(GWTAdjuvant adjuvant)
    {
        for (GWTCohort gwtCohort : schedule.keySet())
        {
            for (GWTTimepoint gwtTimepoint : timepoints)
            {
                GWTImmunization im = getImmunization(gwtCohort, gwtTimepoint);
                if (null != im)
                    im.adjuvants.remove(adjuvant);
            }
        }
    }

//    private static final String IMMUNIZATION_EVENT_TAG_NAME = "ImmunizationEvent";
//    public Element toElement(Document doc)
//    {
//        Element el = createTag(doc);
//        for (Iterator iterator = schedule.keySet().iterator(); iterator.hasNext();)
//        {
//            GWTCohort group = (GWTCohort) iterator.next();
//            Map/*<Timepoint,Immunization>*/ groupMap = (Map) schedule.get(group);
//            for (Iterator iterator1 = groupMap.keySet().iterator(); iterator1.hasNext();)
//            {
//                GWTTimepoint timepoint = (GWTTimepoint) iterator1.next();
//                Element elImmunizationEvent = doc.createElement(IMMUNIZATION_EVENT_TAG_NAME);
//                elImmunizationEvent.setAttribute("groupName", group.name);
//                elImmunizationEvent.appendChild(timepoint.toElement(doc));
//                GWTImmunization immunization = getImmunization(group, timepoint);
//                elImmunizationEvent.appendChild(immunization.toElement(doc));
//
//                el.appendChild(elImmunizationEvent);
//            }
//        }
//        return el;
//    }
//
//    public GWTImmunizationSchedule(Element el, GWTStudyDefinition def)
//    {
//        List/*<Cohort>*/ groups = def.groups;
//        Map/*<String, Cohort>*/ groupMap = new HashMap/*<String, Cohort>*/();
//        for (int i = 0; i < groups.size(); i++)
//        {
//            GWTCohort cohort = (GWTCohort) groups.get(i);
//            groupMap.put(cohort.name, cohort);
//        }
//
//        NodeList nl = el.getElementsByTagName(IMMUNIZATION_EVENT_TAG_NAME);
//        for (int i = 0; i < nl.getLength(); i++)
//        {
//            Element elEvent = (Element) nl.item(i);
//            String groupName = elEvent.getAttribute("groupName");
//            GWTCohort group = (GWTCohort) groupMap.get(groupName);
//            if (null == group)
//                throw new IllegalArgumentException("Group name " + groupName + " not found in group list");
//
//            Map/*<Timepoint,Immunization>*/ tpimMap = (Map) schedule.get(group);
//            if (null == tpimMap)
//            {
//                tpimMap = new HashMap();
//                schedule.put(group, tpimMap);
//            }
//            GWTTimepoint tp = new GWTTimepoint(XMLUtils.getChildElement(elEvent, new GWTTimepoint().tagName()));
//            GWTImmunization im = new GWTImmunization(XMLUtils.getChildElement(elEvent, new GWTImmunization().tagName()), def);
//            tpimMap.put(tp, im);
//        }
//
//    }
}
