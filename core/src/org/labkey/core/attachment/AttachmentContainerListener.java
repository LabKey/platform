package org.labkey.core.attachment;

import org.apache.logging.log4j.Logger;
import org.labkey.api.collections.CsvSet;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager.ContainerListener;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ContainerUtil;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.logging.LogHelper;

import java.util.List;
import java.util.stream.Collectors;

public class AttachmentContainerListener implements ContainerListener
{
    private static final Logger LOG = LogHelper.getLogger(AttachmentContainerListener.class, "Reporting orphaned attachments");

    private record Orphan(String documentName, String parentType){}

    @Override
    public void containerDeleted(Container c, User user)
    {
        TableInfo table = CoreSchema.getInstance().getTableInfoDocuments();
        // Log orphaned attachments in this container, but in dev mode only, since this is for our testing. Also, we
        // don't yet offer a way to delete orphaned attachments via the UI, so it's not helpful to inform admins.
        if (AppProps.getInstance().isDevMode())
        {
            // Find all attachments in this container that don't use the container itself as the parent (since those
            // are the responsibility of this container listener).
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Container"), c.getId());
            filter.addCondition(FieldKey.fromParts("Parent"), c.getId(), CompareType.NEQ);
            List<Orphan> orphans = new TableSelector(table, new CsvSet("DocumentName, ParentType"), filter, null).getArrayList(Orphan.class);
            if (!orphans.isEmpty())
            {
                LOG.error("Found {} in this container, which likely indicates a problem with a delete method or a container listener.", StringUtilsLabKey.pluralize(orphans.size(), "orphaned attachment"));

                final String message;
                if (orphans.size() > 20)
                {
                    orphans = orphans.subList(0, 20);
                    message = "The first 20";
                }
                else
                {
                    message = "All";
                }

                LOG.error("{} detected orphans are listed below:\n{}", message, orphans.stream().map(Record::toString).collect(Collectors.joining("\n")));
            }
        }
        ContainerUtil.purgeTable(CoreSchema.getInstance().getTableInfoDocuments(), c, null);
        AttachmentCache.removeAttachments(c);
    }
}
