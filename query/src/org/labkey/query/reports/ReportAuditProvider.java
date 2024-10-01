package org.labkey.query.reports;


import org.jetbrains.annotations.NotNull;
import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.data.Container;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.reports.report.ReportDB;
import org.labkey.api.reports.report.ReportDescriptor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ReportAuditProvider extends AbstractAuditTypeProvider
{
    private static final String EVENT_NAME = "ReportEvent";
    private static final String COLUMN_NAME_REPORT_ID = "ReportId";
    private static final String COLUMN_NAME_REPORT_NAME = "ReportName";
    private static final String COLUMN_NAME_REPORT_KEY = "ReportKey";
    private static final String COLUMN_NAME_REPORT_TYPE = "ReportType";


    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CONTAINER));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_IMPERSONATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_REPORT_ID));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_REPORT_NAME));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_REPORT_KEY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_REPORT_TYPE));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_COMMENT));
    }

    @Override
    protected AbstractAuditDomainKind getDomainKind()
    {
        return new ReportAuditDomainKind();
    }

    @Override
    public String getEventName()
    {
        return EVENT_NAME;
    }

    @Override
    public String getLabel()
    {
        return "Report events";
    }

    @Override
    public String getDescription()
    {
        return "Events related to the creation and modification of reports and charts.";
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>) ReportAuditEvent.class;
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }

    public static class ReportAuditEvent extends AuditTypeEvent
    {
        private int reportId;
        private String reportName;
        private String reportKey;
        private String reportType;

        public ReportAuditEvent()
        {
        }

        public ReportAuditEvent(@NotNull ReportDB report, @NotNull ReportDescriptor descriptor, Container container, String comment)
        {
            this(report.getRowId(), descriptor, container, comment);
            this.reportKey = report.getReportKey();
        }

        public ReportAuditEvent(int reportId, @NotNull ReportDescriptor descriptor, Container container, String comment)
        {
            super(EVENT_NAME, container, comment);
            this.reportId = reportId;
            this.reportName = descriptor.getReportName();
            this.reportKey = descriptor.getReportKey();
            this.reportType = descriptor.getReportType();
        }

        public int getReportId()
        {
            return reportId;
        }

        public void setReportId(int reportId)
        {
            this.reportId = reportId;
        }

        public String getReportName()
        {
            return reportName;
        }

        public void setReportName(String reportName)
        {
            this.reportName = reportName;
        }

        public String getReportKey()
        {
            return reportKey;
        }

        public void setReportKey(String reportKey)
        {
            this.reportKey = reportKey;
        }

        public String getReportType()
        {
            return reportType;
        }

        public void setReportType(String reportType)
        {
            this.reportType = reportType;
        }

        @Override
        public Map<String, Object> getAuditLogMessageElements()
        {
            Map<String, Object> elements = super.getAuditLogMessageElements();
            elements.put("reportId", reportId);
            elements.put("reportName", reportName);
            elements.put("reportKey", reportKey);
            elements.put("reportType", reportType);
            return elements;
        }

    }

    public static class ReportAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "ReportAuditDomain";
        public static String NAMESPACE_PREFIX = "Report-" + NAME;

        private final Set<PropertyDescriptor> fields;

        public ReportAuditDomainKind()
        {
            super(EVENT_NAME);

            fields = new LinkedHashSet<>();
            fields.add(createPropertyDescriptor(COLUMN_NAME_REPORT_ID, PropertyType.INTEGER, "Report Id", null, true));
            fields.add(createPropertyDescriptor(COLUMN_NAME_REPORT_NAME, PropertyType.STRING, "Report Name", null, true));
            fields.add(createPropertyDescriptor(COLUMN_NAME_REPORT_KEY, PropertyType.STRING, "Report Key", null, false));
            fields.add(createPropertyDescriptor(COLUMN_NAME_REPORT_TYPE, PropertyType.STRING, "Report Type", null, true));
        }

        @Override
        protected String getNamespacePrefix()
        {
            return NAMESPACE_PREFIX;
        }

        @Override
        public Set<PropertyDescriptor> getProperties()
        {
            return fields;
        }

        @Override
        public String getKindName()
        {
            return NAME;
        }
    }
}
