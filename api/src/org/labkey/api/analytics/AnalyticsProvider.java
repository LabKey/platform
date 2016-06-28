package org.labkey.api.analytics;

public interface AnalyticsProvider
{
    String getName();
    String getLabel();
    String getDescription();
    Integer getSortOrder();
}
