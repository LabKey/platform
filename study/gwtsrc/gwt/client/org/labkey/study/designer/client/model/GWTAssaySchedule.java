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
public class GWTAssaySchedule implements Schedule, IsSerializable
{
    List<GWTTimepoint> timepoints = new ArrayList<GWTTimepoint>();
    List<GWTAssayDefinition> assays = new ArrayList<GWTAssayDefinition>();
    Map<GWTAssayDefinition,Map<GWTTimepoint,GWTAssayNote>> assaySchedule = new HashMap<GWTAssayDefinition,Map<GWTTimepoint,GWTAssayNote>> ();
    private String description;

    public GWTAssaySchedule()
    {

    }

    public void setAssayPerformed(GWTAssayDefinition ad, GWTTimepoint tp, boolean perform)
    {
        Map<GWTTimepoint, GWTAssayNote> tpatMap = assaySchedule.get(ad);
        if (null == tpatMap)
        {
            tpatMap = new HashMap<GWTTimepoint,GWTAssayNote>();
            assaySchedule.put(ad, tpatMap);
        }
        if (perform)
            tpatMap.put(tp, new GWTAssayNote(ad));
        else
            tpatMap.remove(tp);
    }

    public void setAssayPerformed(GWTAssayDefinition ad, GWTTimepoint tp, GWTAssayNote gwtAssayNote)
    {
        if (!timepoints.contains(tp))
        {
            timepoints.add(tp);
            Collections.sort(timepoints, new GWTTimepoint.TimepointComparator());
        }
        
        Map<GWTTimepoint, GWTAssayNote> tpatMap = assaySchedule.get(ad);
        if (null == tpatMap)
        {
            tpatMap = new HashMap<GWTTimepoint, GWTAssayNote>();
            assaySchedule.put(ad, tpatMap);
        }
        tpatMap.put(tp, gwtAssayNote);
    }


    public boolean isAssayPerformed(GWTAssayDefinition ad, GWTTimepoint tp)
    {
        return null != getAssayPerformed(ad, tp);
    }

    public GWTAssayNote getAssayPerformed(GWTAssayDefinition ad, GWTTimepoint tp)
    {
        Map<GWTTimepoint, GWTAssayNote> tpatMap = assaySchedule.get(ad);
        if (null == tpatMap)
            return null;

        return tpatMap.get(tp);
    }

    public void addAssay(GWTAssayDefinition ad)
    {
        assays.add(ad);
    }

    public void removeAssay(GWTAssayDefinition ad)
    {
        assays.remove(ad);
        assaySchedule.remove(ad);
    }

    public List<GWTAssayDefinition> getAssays()
    {
        return assays;
    }

    public GWTAssayDefinition getAssay(int i)
    {
        return assays.get(i);
    }

    public GWTAssayDefinition findAssayByName(String name)
    {
        for (GWTAssayDefinition assayDefinition : assays)
        {
            if (assayDefinition.getAssayName().equalsIgnoreCase(name))
                return assayDefinition;
        }
        return null;
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

    public void removeTimepoint(GWTTimepoint tp)
    {
        for (GWTAssayDefinition assayDef : assaySchedule.keySet())
        {
            Map<GWTTimepoint, GWTAssayNote> tpatMap = assaySchedule.get(assayDef);
            if (null != tpatMap)
                tpatMap.remove(tp);
        }
        timepoints.remove(tp);
    }

    public List<GWTTimepoint> getTimepoints()
    {
        return timepoints;
    }

    public GWTTimepoint getTimepoint(int i)
    {
        return timepoints.get(i);
    }

//    public Element toElement(Document doc)
//    {
//        Element el = doc.createElement(XMLUtils.tagName(this));
//        Element elAssayTimes = doc.createElement(new GWTAssayTime().pluralTagName());
//        for (Iterator iterator = schedule.iterator(); iterator.hasNext();)
//        {
//            GWTAssayTime assayTime = (GWTAssayTime) iterator.next();
//            elAssayTimes.appendChild(assayTime.toElement(doc));
//        }
//        el.appendChild(elAssayTimes);
//
//        return el;
//    }
//
//    public GWTAssaySchedule(Element el, List/*<AssayDefinition> */ assays)
//    {
//        GWTAssayTime at = new GWTAssayTime();
//        Element elAssayTimes = XMLUtils.getChildElement(el, at.pluralTagName());
//        NodeList nl = elAssayTimes.getElementsByTagName(at.tagName());
//        for (int i = 0; i < nl.getLength(); i++)
//        {
//            Element elAssayTime = (Element) nl.item(i);
//            schedule.add(new GWTAssayTime(elAssayTime, assays));
//        }
//    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }
}
