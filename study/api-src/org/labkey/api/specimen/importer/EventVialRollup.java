package org.labkey.api.specimen.importer;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.JdbcType;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.specimen.SpecimenEvent;
import org.labkey.api.specimen.SpecimenEventManager;

import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;

public enum EventVialRollup implements Rollup
{
    EventVialLatest
    {
        @Override
        public List<String> getPatterns()
        {
            return Arrays.asList("%", "Latest%");
        }

        @Override
        public Object getRollupResult(List<SpecimenEvent> events, String eventColName, JdbcType fromType, JdbcType toType)
        {
            // Input is SpecimenEvent list
            if (null == events || events.isEmpty())
                return null;
            return SpecimenEventManager.getLastEvent(events).get(eventColName);
        }

        @Override
        public boolean isTypeConstraintMet(JdbcType from, JdbcType to)
        {
            return from.equals(to) || canPromoteNumeric(from, to);
        }
    },
    EventVialFirst
    {
        @Override
        public List<String> getPatterns()
        {
            return Arrays.asList("First%");
        }

        @Override
        public Object getRollupResult(List<SpecimenEvent> events, String eventColName, JdbcType fromType, JdbcType toType)
        {
            // Input is SpecimenEvent list
            if (null == events || events.isEmpty())
                return null;
            return SpecimenEventManager.getFirstEvent(events).get(eventColName);
        }

        @Override
        public boolean isTypeConstraintMet(JdbcType from, JdbcType to)
        {
            return from.equals(to) || canPromoteNumeric(from, to);
        }
    },
    EventVialLatestNonBlank
    {
        @Override
        public List<String> getPatterns()
        {
            return Arrays.asList("LatestNonBlank%");
        }

        @Override
        public Object getRollupResult(List<SpecimenEvent> events, String eventColName, JdbcType fromType, JdbcType toType)
        {
            // Input is SpecimenEvent list
            if (null == events || events.isEmpty())
                return null;
            ListIterator<SpecimenEvent> iterator = events.listIterator(events.size());
            Object result = null;
            while (iterator.hasPrevious())
            {
                SpecimenEvent event = iterator.previous();
                if (null == event)
                    continue;
                result = event.get(eventColName);
                if (null == result)
                    continue;
                if (result instanceof String && StringUtils.isBlank((String) result))
                    continue;
                break;
            }
            return result;
        }

        @Override
        public boolean isTypeConstraintMet(JdbcType from, JdbcType to)
        {
            return from.equals(to) || canPromoteNumeric(from, to);
        }
    },
    EventVialCombineAll
    {
        @Override
        public List<String> getPatterns()
        {
            return Arrays.asList("Combine%");
        }

        @Override
        public Object getRollupResult(List<SpecimenEvent> events, String eventColName, JdbcType fromType, JdbcType toType)
        {
            // Input is SpecimenEvent list
            if (null == events || events.isEmpty())
                return null;
            Object result = null;

            if (toType.isText())
            {
                for (SpecimenEvent event : events)
                {
                    if (null != event)
                    {
                        Object value = event.get(eventColName);
                        if (null != value)
                        {
                            if (value instanceof String)
                            {
                                if (!StringUtils.isBlank((String) value))
                                {
                                    if (null == result)
                                        result = value;
                                    else
                                        result = result + ", " + value;
                                }
                            }
                            else
                            {
                                throw new IllegalStateException("Expected String type.");
                            }
                        }
                    }
                }
            }
            else if (toType.isNumeric())
            {
                for (SpecimenEvent event : events)
                {
                    if (null != event)
                    {
                        Object value = event.get(eventColName);
                        if (null != value)
                        {
                            if (null == result)
                                result = value;
                            else
                                result = JdbcType.add(result, value, toType);
                        }
                    }
                }
            }
            else
                throw new IllegalStateException("CombineAll rollup is supported on String or Numeric types only.");

            return result;
        }

        @Override
        public boolean isTypeConstraintMet(JdbcType from, JdbcType to)
        {
            return (from.equals(to) || canPromoteNumeric(from, to)) && (from.isNumeric() || from.isText());
        }
    };

    // Gets the field value from a particular object in the list (used for event -> vial rollups)
    public abstract Object getRollupResult(List<SpecimenEvent> objs, String colName, JdbcType fromType, JdbcType toType);

    @Override
    public boolean match(PropertyDescriptor from, PropertyDescriptor to, boolean allowTypeMismatch)
    {
        for (String pattern : getPatterns())
        {
            if (pattern.replace("%", from.getName()).equalsIgnoreCase(to.getName()) && (allowTypeMismatch || isTypeConstraintMet(from.getJdbcType(), to.getJdbcType())))
                return true;
        }
        return false;
    }
}
