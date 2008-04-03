package org.labkey.core.query;

import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.SimpleAuditViewFactory;
import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.audit.query.ContainerForeignKey;
import org.labkey.api.data.*;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryView;
import org.labkey.api.view.ViewContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Nov 2, 2007
 *
 * Event field documentation:
 *
 * createdBy - User who created the record
 * created - Timestamp
 * comment - record description
 * projectId - the project id
 * container - container id of the domain event
 *
 */
public class ContainerAuditViewFactory extends SimpleAuditViewFactory
{
    private static final ContainerAuditViewFactory _instance = new ContainerAuditViewFactory();

    public static ContainerAuditViewFactory getInstance()
    {
        return _instance;
    }

    private ContainerAuditViewFactory(){}

    public String getEventType()
    {
        return ContainerManager.CONTAINER_AUDIT_EVENT;
    }

    public String getName()
    {
        return "Project and folder events";
    }

    public QueryView createDefaultQueryView(ViewContext context)
    {
        SimpleFilter filter = new SimpleFilter("EventType", ContainerManager.CONTAINER_AUDIT_EVENT);

        AuditLogQueryView view = AuditLogService.get().createQueryView(context, filter, getEventType());
        view.setSort(new Sort("-Date"));
        view.setShowCustomizeViewLinkInButtonBar(true);
        view.setButtonBarPosition(DataRegion.ButtonBarPosition.BOTH);
        
        return view;
    }

    public List<FieldKey> getDefaultVisibleColumns()
    {
        List<FieldKey> columns = new ArrayList();

        columns.add(FieldKey.fromParts("Date"));
        columns.add(FieldKey.fromParts("CreatedBy"));
        columns.add(FieldKey.fromParts("ProjectId"));
        columns.add(FieldKey.fromParts("ContainerId"));
        columns.add(FieldKey.fromParts("Comment"));

        return columns;
    }
}
