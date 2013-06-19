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

import com.google.gwt.xml.client.Element;
import com.google.gwt.xml.client.Document;
import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * User: Mark Igra
 * Date: Jan 25, 2007
 * Time: 3:11:41 PM
 */
public class GWTSampleMeasure extends AbstractXMLSavable implements IsSerializable
{
    private double amount;
    private Unit unit = Unit.ML;
    private GWTSampleType type;

    public GWTSampleMeasure()
    {

    }
    
    public GWTSampleMeasure(double amount, Unit unit, GWTSampleType type)
    {
        this.amount = amount;
        this.unit = unit;
        this.type = type;
    }

    public GWTSampleMeasure(GWTSampleMeasure sm)
    {
        this(sm.amount, sm.unit, sm.type);
    }

    public String toString()
    {
        return formatAmountString(amount) + " " + unit + " " + type;
    }

    public static String formatAmountString(double amount)
    {
        //No number formats in GWT!
        if (amount > 10000)
        {
            int log = (int) (Math.log(amount) / Math.log(10));
            int val = (int) Math.round((amount / Math.pow(10, log - 3)));
            return (val / 1000.0) + "e" + log;
        }
        else
            return Double.toString(amount);


    }
    public int hashCode()
    {
        return new Double(amount).hashCode() ^ (unit.hashCode() << 1) ^ (type.hashCode() << 2);
    }

    public boolean equals(Object o)
    {
        GWTSampleMeasure sm = (GWTSampleMeasure) o;
        return null == o ? false : (sm.amount == amount && sm.unit.equals(unit) && sm.type.equals(type));
    }

    public Element toElement(Document doc)
    {
        return createTag(doc, "amount", new Double(amount), "unit", unit, "type", type);
    }

    public double getAmount()
    {
        return amount;
    }

    public void setAmount(double amount)
    {
        this.amount = amount;
    }

    public Unit getUnit()
    {
        return unit;
    }

    public void setUnit(Unit unit)
    {
        this.unit = unit;
    }

    public GWTSampleType getType()
    {
        return type;
    }

    public void setType(GWTSampleType type)
    {
        this.type = type;
    }


    public static class Unit implements IsSerializable
    {
        public Unit()
        {

        }
        String name;
        String storageName; //3 chars or less support by specimen tools

        private Unit(String name, String storageName)
        {
            this.name = name;
            this.storageName = storageName;
        }

        public String toString()
        {
            return name;
        }

        public int hashCode()
        {
            return name.hashCode();
        }

        public boolean equals(Object x)
        {
            return null == x ? false : ((Unit) x).name.equals(name);
        }

        public String getStorageName()
        {
            return storageName;
        }

        //NOTE: DO NOT CHANGE THE STORAGE NAME, IT SHOULD BE CONSTANT FOR RETRIEVING FROM DATABASE
        public static final Unit ML = new Unit("ml", "ML");
        public static final Unit MICROLITER = new Unit("ul", "UL");
        public static final Unit CELLS = new Unit("cells", "CEL");
        
        public static final Unit[] ALL = new Unit [] {ML, MICROLITER, CELLS};
        public static Unit fromString(String str)
        {
            for (int i = 0; i < ALL.length; i++)
                if (ALL[i].name.equalsIgnoreCase(str))
                    return ALL[i];

            for (int i = 0; i < ALL.length; i++)
                if (ALL[i].storageName.equalsIgnoreCase(str))
                    return ALL[i];

            return null;
        }

    }
}
