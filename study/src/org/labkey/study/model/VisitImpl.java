/*
 * Copyright (c) 2009-2019 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.BeanObjectFactory;
import org.labkey.api.data.Container;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.Transient;
import org.labkey.api.query.FieldKey;
import org.labkey.api.study.Cohort;
import org.labkey.api.study.Visit;
import org.labkey.api.util.JunitUtil;
import org.labkey.study.StudySchema;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class VisitImpl extends AbstractStudyEntity<VisitImpl> implements Cloneable, Serializable, Visit
{
    // standard strings to use in URLs etc
    public static final String VISIT_KEY = "visitRowId";
    public static final double DEMOGRAPHICS_VISIT = -1;

    // Sequence numbers and protocol day are currently stored as NUMERIC(15, 4); below are all the related constants.
    private static final int MAX_SCALE = 4;
    private static final NumberFormat SEQUENCE_FORMAT = new DecimalFormat("0.0###");

    private int _rowId = 0;
    private BigDecimal _sequenceMin = BigDecimal.ZERO;
    private BigDecimal _sequenceMax = BigDecimal.ZERO;
    private BigDecimal _protocolDay = null;
    private Character _typeCode;
    private Integer _visitDateDatasetid = 0;
    private Integer _cohortId;
    private int _chronologicalOrder;
    private SequenceHandling _sequenceHandling = SequenceHandling.normal;
    private String _description;
    
    public VisitImpl()
    {
    }

    public VisitImpl(Container container, @NotNull BigDecimal seqMin, String label, Type type)
    {
        this(container, seqMin, seqMin, label, null == type ? null : type.getCode());
    }

    public VisitImpl(Container container, BigDecimal seqMin, BigDecimal seqMax, String label, @Nullable Type type)
    {
        this(container, seqMin, seqMax, label, null == type ? null : type.getCode());
    }

    public VisitImpl(Container container, @NotNull BigDecimal seqMin, @NotNull BigDecimal seqMax, String name, @Nullable Character typeCode)
    {
        setContainer(container);
        setSequenceNumMin(seqMin);
        setSequenceNumMax(seqMax);
        setProtocolDay(seqMin);
        _label = name;
        _typeCode = typeCode;
        _showByDefault = true;
    }

    @Override
    public String getSequenceString()
    {
        return getSequenceString(_sequenceMin, _sequenceMax);
    }


    public static String getSequenceString(BigDecimal min, BigDecimal max)
    {
        if (min.compareTo(max) == 0)
            return formatSequenceNum(min);
        else
            return formatSequenceNum(min) + " - " + formatSequenceNum(max);
    }


    // Formats integers as integers; everything else at four significant digits. That's not consistent with
    // formatSequenceNum(), but it's what we've always done when appending into SQL.
    private static StringBuilder appendSqlSequenceNum(StringBuilder sb, BigDecimal seqnum)
    {
        // Is seqnum an integer?
        if (seqnum.stripTrailingZeros().scale() <= 0)
            seqnum = seqnum.setScale(0, RoundingMode.UNNECESSARY);
        else
            seqnum = seqnum.setScale(MAX_SCALE, RoundingMode.HALF_UP);

        return sb.append(seqnum.toPlainString());
    }


    public StringBuilder appendSqlSequenceNumMin(StringBuilder sb)
    {
        return appendSqlSequenceNum(sb, _sequenceMin);
    }


    public StringBuilder appendSqlSequenceNumMax(StringBuilder sb)
    {
        return appendSqlSequenceNum(sb, _sequenceMax);
    }


    @Override
    public String getDisplayString()
    {
        if (getLabel() != null)
            return getLabel();
        return getSequenceString();
    }


    public void addVisitFilter(SimpleFilter filter)
    {
        filter.addCondition(FieldKey.fromParts("VisitRowId"), getRowId());
    }


    public Character getTypeCode()
    {
        return _typeCode;
    }


    public void setTypeCode(Character typeCode)
    {
        verifyMutability();
        _typeCode = typeCode;
    }


    @Override
    public Type getType()
    {
        if (_typeCode == null)
            return null;
        return Type.getByCode(_typeCode);
    }


    @Override
    public Integer getVisitDateDatasetId()
    {
        return _visitDateDatasetid;
    }

    
    public void setVisitDateDatasetId(Integer visitDateDatasetId)
    {
        _visitDateDatasetid = visitDateDatasetId == null ? 0 : visitDateDatasetId;
    }


    @Transient
    public List<VisitDataset> getVisitDatasets()
    {
        return StudyManager.getInstance().getMapping(this);
    }


    @Override
    public Object getPrimaryKey()
    {
        return getRowId();
    }

    @Override
    public Integer getId()
    {
        return getRowId();
    }

    @Override
    public BigDecimal getSequenceNumMin()
    {
        return _sequenceMin;
    }

    public void setSequenceNumMin(BigDecimal sequenceMin)
    {
        _sequenceMin = VisitImpl.normalizeSequenceNum(sequenceMin);
    }

    public String getFormattedSequenceNumMin()
    {
        return formatSequenceNum(_sequenceMin);
    }

    @Override
    public BigDecimal getSequenceNumMax()
    {
        return _sequenceMax;
    }

    public String getFormattedSequenceNumMax()
    {
        return formatSequenceNum(_sequenceMax);
    }

    public void setSequenceNumMax(BigDecimal sequenceMax)
    {
        _sequenceMax = VisitImpl.normalizeSequenceNum(sequenceMax);
    }

    @Override
    public BigDecimal getProtocolDay()
    {
        return _protocolDay;
    }

    public String getFormattedProtocolDay()
    {
        return null != _protocolDay ? formatSequenceNum(_protocolDay) : "";
    }

    public void setProtocolDay(@Nullable BigDecimal protocolDay)
    {
        _protocolDay = null != protocolDay ? VisitImpl.normalizeSequenceNum(protocolDay) : null;
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        assert _rowId == 0;
        _rowId = rowId;
    }

    public static BigDecimal parseSequenceNum(String s)
    {
        return normalizeSequenceNum(new BigDecimal(s));
    }

    public static BigDecimal getSequenceNum(double seqnum)
    {
        return normalizeSequenceNum(BigDecimal.valueOf(seqnum));
    }

    public static BigDecimal getSequenceNum(int seqnum)
    {
        return normalizeSequenceNum(BigDecimal.valueOf(seqnum));
    }

    public static String formatSequenceNum(BigDecimal bd)
    {
        return SEQUENCE_FORMAT.format(bd);
    }

    public static BigDecimal normalizeSequenceNum(BigDecimal bd)
    {
        return bd.setScale(MAX_SCALE, RoundingMode.HALF_UP);
    }

    @Override
    public Integer getCohortId()
    {
        return _cohortId;
    }

    public void setCohortId(Integer cohortId)
    {
        _cohortId = cohortId;
    }

    @Override
    @Transient
    public Cohort getCohort()
    {
        if (_cohortId == null)
            return null;
        return new TableSelector(StudySchema.getInstance().getTableInfoCohort()).getObject(_cohortId, CohortImpl.class);
    }

    @Override
    public int getChronologicalOrder()
    {
        return _chronologicalOrder;
    }

    @Override
    public void setChronologicalOrder(int chronologicalOrder)
    {
        _chronologicalOrder = chronologicalOrder;
    }

    @Override
    public @NotNull SequenceHandling getSequenceNumHandlingEnum()
    {
        return null == _sequenceHandling ? SequenceHandling.normal : _sequenceHandling;
    }

    public String getSequenceNumHandling()
    {
        return getSequenceNumHandlingEnum().name();
    }

    public void setSequenceNumHandling(String name)
    {
        if (StringUtils.isEmpty(name))
            _sequenceHandling = SequenceHandling.normal;
        else
            _sequenceHandling = SequenceHandling.valueOf(name);
    }

    public Map<String, Object> toMap()
    {
        Map<String, Object> map = new HashMap<>();
        map.put("rowid", getRowId());
        map.put("sequencenummin", getSequenceNumMin());
        map.put("sequencenummax", getSequenceNumMax());
        map.put("label", getLabel());
        map.put("typecode", getTypeCode());
        map.put("container", getContainer());
        map.put("showbydefault", isShowByDefault());
        map.put("displayorder", getDisplayOrder());
        map.put("visitdatedatasetid", getVisitDateDatasetId());
        map.put("cohortid", getCohortId());
        map.put("chronologicalorder", getChronologicalOrder());
        map.put("sequencenumhandling", getSequenceNumHandling());
        map.put("description", getDescription());
        map.put("protocolday", getProtocolDay());

        return map;
    }

    @NotNull
    public static VisitImpl fromMap(Map<String, Object> map, Container container)
    {
        // required properties
        // For now, assume incoming SequenceNums could be BigDecimal or Double. TODO: Change this to BigDecimal always.
        BigDecimal seqNumMin = (BigDecimal)JdbcType.DECIMAL.convert(map.get("sequencenummin"));
        BigDecimal seqNumMax = map.containsKey("sequencenummax") ? (BigDecimal)JdbcType.DECIMAL.convert(map.get("sequencenummax")) : seqNumMin;
        String label = map.containsKey("label") ? (String) map.get("label") : null;
        Character type = map.containsKey("type") ? (Character) map.get("type") : null;

        VisitImpl visit = new VisitImpl(container, seqNumMin, seqNumMax, label, type);

        // optional properties
        if (map.containsKey("rowid"))
            visit.setRowId((int) map.get("rowid"));
        if (map.containsKey("description"))
            visit.setDescription((String) map.get("description"));
        if (map.containsKey("showbydefault"))
            visit.setShowByDefault((boolean) map.get("showbydefault"));
        if (map.containsKey("displayorder"))
            visit.setDisplayOrder((int) map.get("displayorder"));
        if (map.containsKey("chronologicalorder"))
            visit.setChronologicalOrder((int) map.get("chronologicalorder"));
        if (map.containsKey("sequencenumhandling"))
            visit.setSequenceNumHandling((String) map.get("sequencenumhandling"));
        if (map.containsKey("protocolday"))
            visit.setProtocolDay((BigDecimal)JdbcType.DECIMAL.convert(map.get("protocolday")));

        return visit;
    }

    public static VisitImpl merge(VisitImpl target, VisitImpl source, boolean copyRowId)
    {
        VisitImpl _target = target.createMutable();

        if (copyRowId)
            _target.setRowId(source.getRowId());

        _target.setSequenceNumMin(source.getSequenceNumMin());
        _target.setSequenceNumMax(source.getSequenceNumMax());
        _target.setLabel(source.getLabel());
        _target.setTypeCode(source.getTypeCode());
        _target.setContainer(source.getContainer());
        _target.setShowByDefault(source.isShowByDefault());
        _target.setDisplayOrder(source.getDisplayOrder());
        _target.setVisitDateDatasetId(source.getVisitDateDatasetId());
        _target.setCohortId(source.getCohortId());
        _target.setChronologicalOrder(source.getChronologicalOrder());
        _target.setSequenceNumHandling(source.getSequenceNumHandling());
        _target.setDescription(source.getDescription());
        _target.setProtocolDay(source.getProtocolDay());

        return _target;
    }

    static _BeanObjectFactory _f = new _BeanObjectFactory();

    static
    {
        ObjectFactory.Registry.register(VisitImpl.class, _f);
    }

    @Override
    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        verifyMutability();
        _description = description;
    }

    private static class _BeanObjectFactory extends BeanObjectFactory<VisitImpl>
    {
        _BeanObjectFactory()
        {
            super(VisitImpl.class);
            assert !_readableProperties.remove("visitDataSets");
            // WHY does VisitImpl need getRelevantPermissions and getPolicy and getParentResource???? WHY?!!!
            assert !_readableProperties.remove("relevantPermissions");
            assert !_readableProperties.remove("parentResource");
            assert !_readableProperties.remove("policy");
            assert !_readableProperties.remove("sourceModule");
        }
    }

    public static BigDecimal calcDefaultDateBasedProtocolDay(BigDecimal sequenceMin, BigDecimal sequenceMax)
    {
        // Average and round
        return sequenceMin.add(sequenceMax).divide(BigDecimal.valueOf(2)).setScale(0, RoundingMode.HALF_UP);
    }

    public boolean isInRange(BigDecimal seqnum)
    {
        return _sequenceMin.compareTo(seqnum) <= 0 && _sequenceMax.compareTo(seqnum) >= 0;
    }

    public String toString()
    {
        return (null != _label ? _label + " (" : "(") + getSequenceString() + ")";
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VisitImpl visit = (VisitImpl) o;
        return _rowId == visit._rowId && _chronologicalOrder == visit._chronologicalOrder && Objects.equals(_sequenceMin, visit._sequenceMin) && Objects.equals(_sequenceMax, visit._sequenceMax) && Objects.equals(_protocolDay, visit._protocolDay) && Objects.equals(_typeCode, visit._typeCode) && Objects.equals(_visitDateDatasetid, visit._visitDateDatasetid) && Objects.equals(_cohortId, visit._cohortId) && _sequenceHandling == visit._sequenceHandling && Objects.equals(_description, visit._description);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(_rowId, _sequenceMin, _sequenceMax, _protocolDay, _typeCode, _visitDateDatasetid, _cohortId, _chronologicalOrder, _sequenceHandling, _description);
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testFormat()
        {
            VisitImpl v;
            Container c = JunitUtil.getTestContainer();

            v = new VisitImpl(c, BigDecimal.ZERO, BigDecimal.valueOf(0.9999), "label", (Type)null);
            assertEquals("0", v.appendSqlSequenceNumMin(new StringBuilder()).toString());
            assertEquals("0.9999", v.appendSqlSequenceNumMax(new StringBuilder()).toString());
            assertTrue(v.isInRange(BigDecimal.ZERO));
            assertTrue(v.isInRange(BigDecimal.valueOf(0.9999)));
            assertTrue(v.isInRange(BigDecimal.valueOf(0.5)));
            assertTrue(v.isInRange(BigDecimal.valueOf(0.75)));
            assertFalse(v.isInRange(BigDecimal.valueOf(-0.0001)));
            assertFalse(v.isInRange(BigDecimal.ONE));
            assertFalse(v.isInRange(BigDecimal.valueOf(7.0)));
            assertFalse(v.isInRange(BigDecimal.valueOf(123435.0)));

            v = new VisitImpl(c, BigDecimal.ONE, BigDecimal.valueOf(1.09999999999999999), "label", (Type)null);
            assertEquals("1", v.appendSqlSequenceNumMin(new StringBuilder()).toString());
            assertEquals("1.1000", v.appendSqlSequenceNumMax(new StringBuilder()).toString());

            v = new VisitImpl(c, BigDecimal.valueOf(2), BigDecimal.valueOf(2.0099), "label", (Type)null);
            assertEquals("2", v.appendSqlSequenceNumMin(new StringBuilder()).toString());
            assertEquals("2.0099", v.appendSqlSequenceNumMax(new StringBuilder()).toString());

            v = new VisitImpl(c, BigDecimal.valueOf(3), BigDecimal.valueOf(3.0000999999), "label", (Type)null);
            assertEquals("3", v.appendSqlSequenceNumMin(new StringBuilder()).toString());
            assertEquals("3.0001", v.appendSqlSequenceNumMax(new StringBuilder()).toString());

            v = new VisitImpl(c, BigDecimal.valueOf(-1), "label", (Type)null);
            assertEquals("-1", v.appendSqlSequenceNumMin(new StringBuilder()).toString());
            assertEquals("-1", v.appendSqlSequenceNumMax(new StringBuilder()).toString());

            v = new VisitImpl(c, BigDecimal.valueOf(12345678901.1234), BigDecimal.valueOf(99999999999.9999), "label", (Type)null);
            assertEquals("12345678901.1234", v.appendSqlSequenceNumMin(new StringBuilder()).toString());
            assertEquals("99999999999.9999", v.appendSqlSequenceNumMax(new StringBuilder()).toString());
        }
    }
}
