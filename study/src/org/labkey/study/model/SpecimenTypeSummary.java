/*
 * Copyright (c) 2006-2014 LabKey Corporation
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

package org.labkey.study.model;

import org.labkey.api.data.Container;
import org.labkey.api.view.ActionURL;
import org.labkey.study.controllers.specimen.SpecimenController;

import java.util.*;

/**
 * User: brittp
 * Date: Aug 21, 2006
 * Time: 3:19:04 PM
 */
public class SpecimenTypeSummary
{
    public static abstract class TypeCount
    {
        private Container _container;
        private TypeCount _parent;
        private String _label;
        private Integer _id;
        private int _vialCount = 0;

        private TypeCount(Container container, SpecimenTypeSummary.TypeCount parent, String label, Integer id)
        {
            _container = container;
            _parent = parent;
            _label = label;
            _id = id;
        }

        public int getVialCount()
        {
            return _vialCount;
        }

        public void setVialCount(int vialCount)
        {
            _vialCount = vialCount;
        }

        public String getLabel()
        {
            return _label;
        }

        public Integer getId()
        {
            return _id;
        }

        public TypeCount getParent()
        {
            return _parent;
        }

        public String geDisplayLabel()
        {
            StringBuilder label = new StringBuilder();
            buildDisplayLabel(label);
            return label.toString();
        }

        private void buildDisplayLabel(StringBuilder label)
        {
            if (_parent != null)
            {
                _parent.buildDisplayLabel(label);
                label.append(", ");
            }
            String currentLable = getLabel();
            label.append(currentLable != null ? currentLable : "[unknown]");
        }

        public ActionURL getURL()
        {
            ActionURL url = new ActionURL(SpecimenController.SamplesAction.class, _container);
            addFilterParameters("SpecimenDetail", url);
            url.addParameter("showVials", "true");
            return url;
        }

        private void addFilterParameters(String dataRegionName, ActionURL url)
        {
            url.addParameter(dataRegionName + "." + getSpecimenViewFilterColumn() + "~eq", getLabel());
            if (_parent != null)
                _parent.addFilterParameters(dataRegionName, url);
        }

        public abstract List<? extends TypeCount> getChildren();

        public abstract String getSpecimenViewFilterColumn();
    }

    private class PrimaryTypeCount extends TypeCount
    {
        private PrimaryTypeCount(Container container, String label, Integer id)
        {
            super(container, null, label, id);
        }

        public List<? extends TypeCount> getChildren()
        {
            return getDerivatives(this);
        }

        public String getSpecimenViewFilterColumn()
        {
            return "PrimaryType/Description";
        }
    }

    private class DerivativeTypeCount extends TypeCount
    {
        private PrimaryTypeCount _parent;
        private DerivativeTypeCount(Container container, SpecimenTypeSummary.PrimaryTypeCount parent, String label, Integer id)
        {
            super(container, parent, label, id);
            _parent = parent;
        }

        public List<? extends TypeCount> getChildren()
        {
            return getAdditives(_parent, this);
        }

        public String getSpecimenViewFilterColumn()
        {
            return "DerivativeType/Description";
        }
    }

    private class AdditiveTypeCount extends TypeCount
    {
        private AdditiveTypeCount(Container container, SpecimenTypeSummary.DerivativeTypeCount parent, String label, Integer id)
        {
            super(container, parent, label, id);
        }

        public List<TypeCount> getChildren()
        {
            return Collections.emptyList();
        }

        public String getSpecimenViewFilterColumn()
        {
            return "AdditiveType/Description";
        }
    }

    private Container _container;
    private SpecimenTypeSummaryRow[] _rows;

    public SpecimenTypeSummary(Container container, SpecimenTypeSummaryRow[] rows)
    {
        _container = container;
        _rows = rows;
    }

    private boolean safeEqual(Object first, Object second)
    {
        if (first == null && second == null)
            return true;
        if (first == null || second == null)
            return false;
        return first.equals(second);
    }

    public List<? extends TypeCount> getPrimaryTypes()
    {
        List<PrimaryTypeCount> counts = new ArrayList<>();
        PrimaryTypeCount current = null;
        for (SpecimenTypeSummaryRow row : _rows)
        {
            if (current == null || !safeEqual(row.getPrimaryType(), current.getLabel()))
            {
                current = new PrimaryTypeCount(_container, row.getPrimaryType(), row.getPrimaryTypeId());
                counts.add(current);
            }
            current.setVialCount(current.getVialCount() + row.getVialCount().intValue());
        }
        return counts;
    }

    public List<? extends TypeCount> getDerivatives()
    {
        return getDerivatives(null);
    }

    public List<? extends TypeCount> getDerivatives(PrimaryTypeCount primaryType)
    {
        Map<String, DerivativeTypeCount> counts = new TreeMap<>();
        DerivativeTypeCount current;
        for (SpecimenTypeSummaryRow row : _rows)
        {
            if (primaryType == null || safeEqual(primaryType.getLabel(), row.getPrimaryType()))
            {
                String key = row.getDerivative() + "/" + row.getDerivative();
                current = counts.get(key);
                if (current == null)
                {
                    current = new DerivativeTypeCount(_container, primaryType, row.getDerivative(), row.getDerivativeTypeId());
                    counts.put(key, current);
                }
                current.setVialCount(current.getVialCount() + row.getVialCount().intValue());
            }
        }
        List<DerivativeTypeCount> returnValues = new ArrayList<>();
        returnValues.addAll(counts.values());
        return returnValues;
    }

    public List<? extends TypeCount> getAdditives()
    {
        return getAdditives(null);
    }

    public List<? extends TypeCount> getAdditives(DerivativeTypeCount derivativeType)
    {
        return getAdditives(null, derivativeType);
    }

    public List<? extends TypeCount> getAdditives(PrimaryTypeCount primaryType, DerivativeTypeCount derivativeType)
    {
        Map<String, AdditiveTypeCount> counts = new TreeMap<>();
        AdditiveTypeCount current;
        for (SpecimenTypeSummaryRow row : _rows)
        {
            if ((derivativeType == null || safeEqual(derivativeType.getLabel(), row.getDerivative())) &&
                 (primaryType == null || safeEqual(primaryType.getLabel(), row.getPrimaryType())))
            {
                String key = row.getAdditive() + "/" + row.getAdditive();
                current = counts.get(key);
                if (current == null)
                {
                    current = new AdditiveTypeCount(_container, derivativeType, row.getAdditive(), row.getAdditiveTypeId());
                    counts.put(key, current);
                }
                current.setVialCount(current.getVialCount() + row.getVialCount().intValue());
            }
        }
        if (counts.size() == 1)
        {
            String label = counts.values().iterator().next().getLabel();
            if (label == null || label.length() == 0 || "None".equalsIgnoreCase(label))
                return Collections.emptyList();
        }
        List<AdditiveTypeCount> returnValues = new ArrayList<>();
        returnValues.addAll(counts.values());
        return returnValues;
    }

    public boolean isVialCountZero()
    {
        for (SpecimenTypeSummaryRow row : _rows)
            if (row.getVialCount() > 0)
                return false;
        return true;
    }
}
