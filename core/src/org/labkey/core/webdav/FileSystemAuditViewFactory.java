package org.labkey.core.webdav;

import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.SimpleAuditViewFactory;
import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.data.*;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryView;
import org.labkey.api.view.ViewContext;

import java.util.ArrayList;
import java.util.List;
import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Oct 16, 2007
 *
 * Event field documentation:
 *
 * createdBy - User who created the record
 * created - Timestamp
 * comment - record description
 * projectId - the project id
 * container - container id of the domain event
 * entityId - entity id of the attachment parent
 * key1 - the attachment name
 *
 */
public class FileSystemAuditViewFactory extends SimpleAuditViewFactory
{
    public static final String EVENT_TYPE = "FileSystem";
    private static final FileSystemAuditViewFactory _instance = new FileSystemAuditViewFactory();

    private FileSystemAuditViewFactory(){}

    public static FileSystemAuditViewFactory getInstance()
    {
        return _instance;
    }

    public String getEventType()
    {
        return EVENT_TYPE;
    }

    public String getName()
    {
        return "File events";
    }

    public QueryView createDefaultQueryView(ViewContext context)
    {
        SimpleFilter filter = new SimpleFilter("EventType", EVENT_TYPE);

        AuditLogQueryView view = AuditLogService.get().createQueryView(context, filter, getEventType());
        view.setSort(new Sort("-Date"));
        view.setShowCustomizeViewLinkInButtonBar(true);
        view.setButtonBarPosition(DataRegion.ButtonBarPosition.BOTH);

        return view;
    }

    public List<FieldKey> getDefaultVisibleColumns()
    {
        List<FieldKey> columns = new ArrayList<FieldKey>();

        columns.add(FieldKey.fromParts("Date"));
        columns.add(FieldKey.fromParts("CreatedBy"));
        columns.add(FieldKey.fromParts("Key1"));
        columns.add(FieldKey.fromParts("Key2"));
        columns.add(FieldKey.fromParts("Comment"));

        return columns;
    }

    public void setupTable(TableInfo table)
    {
        ColumnInfo dir = table.getColumn("Key1");
        if (dir != null)
        {
            dir.setCaption("directory");
            dir.setDisplayColumnFactory(new DisplayColumnFactory()
            {
                public DisplayColumn createRenderer(ColumnInfo colInfo)
                {
                    return new DataColumn(colInfo)
                    {
                        public String getName()
                        {
                            return "directory";
                        }
                    };
                }
            });
        }
        ColumnInfo file = table.getColumn("Key2");
        if (file != null)
        {
            file.setCaption("file");
            file.setDisplayColumnFactory(new DisplayColumnFactory()
            {
                public DisplayColumn createRenderer(ColumnInfo colInfo)
                {
                    return new DataColumn(colInfo)
                    {
                        public String getName()
                        {
                            return "file";
                        }
                    };
                }
            });
        }
    }

    public static AuditLogQueryView createAttachmentView(ViewContext context, File dir)
    {
        SimpleFilter filter = new SimpleFilter("ContainerId", context.getContainer().getId());
        filter.addCondition("EventType", EVENT_TYPE);

        AuditLogQueryView view = AuditLogService.get().createQueryView(context, filter, EVENT_TYPE);
        view.setTitle("<b>Directory Log:</b>");
        view.setSort(new Sort("-Date"));
        view.setVisibleColumns(new String[]{"Date", "CreatedBy", "Comment"});
        return view;
    }
}