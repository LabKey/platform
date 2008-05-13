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

package org.labkey.study.designer.client.model;

import com.google.gwt.xml.client.Element;
import com.google.gwt.xml.client.Document;
import com.google.gwt.user.client.rpc.IsSerializable;

import java.util.Comparator;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Dec 4, 2006
 * Time: 9:58:11 PM
 * To change this template use File | Settings | File Templates.
 */
public class GWTTimepoint implements Comparable, IsSerializable
{
    public static final Unit DAYS = new Unit("days", 1);
    public static final Unit WEEKS = new Unit("weeks", 7);
    public static final Unit MONTHS = new Unit("months", 30);
    public static final Unit[] UNIT_CHOICES = {DAYS, WEEKS};

    private String name;
    private Unit unit = DAYS;
    private int days;

    public GWTTimepoint()
    {

    }
    
    public GWTTimepoint(String name, int numUnits, Unit displayUnit)
    {
        this.name = name;
        this.unit = displayUnit == null ? DAYS : displayUnit;
        this.days = numUnits * unit.daysPerUnit;
    }

    public String toString()
    {
        return (name  != null ? name + ": " : "") + unit.daysAsUnit(days) + " " + unit.name;
    }

    public int compareTo(Object o)
    {
        GWTTimepoint p = (GWTTimepoint) o;
        if (null == p)
            return -1;

        if (days - p.days == 0)
            return name.compareTo(p.name);
        
        return days - p.days;
    }

    public boolean equals(Object o)
    {
        if (this == o) return true;

        GWTTimepoint that = (GWTTimepoint) o;

        if (days != that.days) return false;
        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        if (!unit.equals(that.unit)) return false;

        return true;
    }

    public int hashCode()
    {
        int result;
        result = (name != null ? name.hashCode() : 0);
        result = 31 * result + unit.hashCode();
        result = 31 * result + days;
        return result;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public Unit getUnit()
    {
        return unit;
    }

    public void setUnit(Unit unit)
    {
        this.unit = unit;
    }

    public int getDays()
    {
        return days;
    }

    public void setDays(int days)
    {
        this.days = days;
    }

//    public Element toElement(Document doc)
//    {
//        return createTag(doc, "name", name, "days", new Integer(days), "displayUnit", unit);
//    }
//
//    public GWTTimepoint(Element el)
//    {
//        this(el.getAttribute("name"), Integer.parseInt(el.getAttribute("days")), Unit.fromString(el.getAttribute("displayUnit")));
//    }
//
    public static class Unit implements IsSerializable
    {
        public Unit()
        {
            
        }
        public static Unit fromString(String unit)
        {
            for (int i = 0; i < UNIT_CHOICES.length; i++)
                if (UNIT_CHOICES[i].name.equalsIgnoreCase(unit))
                    return UNIT_CHOICES[i];

            return DAYS;
        }
        
        private Unit(String name, int daysPerUnit)
        {
            this.name = name;
            this.daysPerUnit = daysPerUnit;
        }

        public int daysAsUnit(double days)
        {
            return (int) days/daysPerUnit;
        }

        public double unitsAsDays(double units)
        {
            return units * daysPerUnit;
        }

        public String toString() {
            return name;
        }
        
        public String name;
        public int daysPerUnit;
    }

    //Need this since bad bug in google's sorting...
    public static class TimepointComparator implements Comparator
    {
        public int compare(Object o1, Object o2)
        {
            return ((GWTTimepoint) o1).compareTo(o2);
        }
    }
}
