/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

import java.util.*;

/**
 * User: brittp
 * Date: Aug 21, 2006
 * Time: 3:19:04 PM
 */
public class SpecimenTypeSummary
{
    public abstract class TypeCount
    {
        private TypeCount _parent;
        private String _label;
        private Integer _rowId;
        private int _vialCount = 0;

        private TypeCount(SpecimenTypeSummary.TypeCount parent, Integer rowId, String label)
        {
            _parent = parent;
            _rowId = rowId;
            _label = label;
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

        public Integer getRowId()
        {
            return _rowId;
        }

        public TypeCount getParent()
        {
            return _parent;
        }

        public String getFullLabel()
        {
            StringBuilder label = new StringBuilder();
            buildFullLabel(label);
            return label.toString();
        }

        private void buildFullLabel(StringBuilder label)
        {
            if (_parent != null)
            {
                _parent.buildFullLabel(label);
                label.append(", ");
            }
            label.append(getLabel());
        }

        public abstract List<? extends TypeCount> getChildren();

        public abstract String getSpecimenViewFilterColumn();
    }

    private class PrimaryTypeCount extends TypeCount
    {
        private PrimaryTypeCount(Integer rowId, String label)
        {
            super(null, rowId, label);
        }

        public List<? extends TypeCount> getChildren()
        {
            return getDerivatives(this);
        }

        public String getSpecimenViewFilterColumn()
        {
            return "PrimaryType";
        }
    }

    private class DerivativeTypeCount extends TypeCount
    {
        private PrimaryTypeCount _parent;
        private DerivativeTypeCount(SpecimenTypeSummary.PrimaryTypeCount parent, Integer rowId, String label)
        {
            super(parent, rowId, label);
            _parent = parent;
        }

        public List<? extends TypeCount> getChildren()
        {
            return getAdditives(_parent, this);
        }

        public String getSpecimenViewFilterColumn()
        {
            return "DerivativeType";
        }
    }

    private class AdditiveTypeCount extends TypeCount
    {
        private AdditiveTypeCount(SpecimenTypeSummary.DerivativeTypeCount parent, Integer rowId, String label)
        {
            super(parent, rowId, label);
        }

        public List<TypeCount> getChildren()
        {
            return Collections.emptyList();
        }

        public String getSpecimenViewFilterColumn()
        {
            return "AdditiveType";
        }
    }

    private SpecimenTypeSummaryRow[] _rows;

    public SpecimenTypeSummary(SpecimenTypeSummaryRow[] rows)
    {
        _rows = rows;
    }

    private boolean integersEqual(Integer first, Integer second)
    {
        if (first == null && second == null)
            return true;
        if (first == null || second == null)
            return false;
        return first.equals(second);
    }

    public List<? extends TypeCount> getPrimaryTypes()
    {
        List<PrimaryTypeCount> counts = new ArrayList<PrimaryTypeCount>();
        PrimaryTypeCount current = null;
        for (SpecimenTypeSummaryRow row : _rows)
        {
            if (current == null || !integersEqual(row.getPrimaryTypeId(), current.getRowId()))
            {
                current = new PrimaryTypeCount(row.getPrimaryTypeId(), row.getPrimaryType());
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
        Map<String, DerivativeTypeCount> counts = new TreeMap<String, DerivativeTypeCount>();
        DerivativeTypeCount current;
        for (SpecimenTypeSummaryRow row : _rows)
        {
            if (primaryType == null || integersEqual(primaryType.getRowId(), row.getPrimaryTypeId()))
            {
                String key = row.getDerivative() + "/" + row.getDerivativeTypeId();
                current = counts.get(key);
                if (current == null)
                {
                    current = new DerivativeTypeCount(primaryType, row.getDerivativeTypeId(), row.getDerivative());
                    counts.put(key, current);
                }
                current.setVialCount(current.getVialCount() + row.getVialCount().intValue());
            }
        }
        List<DerivativeTypeCount> returnValues = new ArrayList<DerivativeTypeCount>();
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
        Map<String, AdditiveTypeCount> counts = new TreeMap<String, AdditiveTypeCount>();
        AdditiveTypeCount current;
        for (SpecimenTypeSummaryRow row : _rows)
        {
            if ((derivativeType == null || integersEqual(derivativeType.getRowId(), row.getDerivativeTypeId())) &&
                 (primaryType == null || integersEqual(primaryType.getRowId(), row.getPrimaryTypeId())))
            {
                String key = row.getAdditive() + "/" + row.getAdditiveTypeId();
                current = counts.get(key);
                if (current == null)
                {
                    current = new AdditiveTypeCount(derivativeType, row.getAdditiveTypeId(), row.getAdditive());
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
        List<AdditiveTypeCount> returnValues = new ArrayList<AdditiveTypeCount>();
        returnValues.addAll(counts.values());
        return returnValues;
    }
}
