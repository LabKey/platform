package org.labkey.core.query;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.RowIdQueryUpdateService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.DOM;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ShortURLRecord;
import org.labkey.api.view.ShortURLService;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.core.admin.AdminController;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ShortUrlTableInfo extends FilteredTable<CoreQuerySchema>
{
    private static final String SHORT_URL_COL = "shorturl";
    private static final String FULL_URL_COL = "fullurl";
    private static final String ROWID_COL = "rowid";
    private static final String TO_CLIPBOARD_COL = "CopyToClipboard";

    public ShortUrlTableInfo(@NotNull CoreQuerySchema userSchema)
    {
        super(CoreSchema.getInstance().getTableInfoShortURL(), userSchema);
        wrapAllColumns(true);
        setInsertURL(LINK_DISABLER);
        setImportURL(LINK_DISABLER);

        DetailsURL updateUrl = new DetailsURL(new ActionURL(AdminController.UpdateShortURLAction.class, getContainer()),
                Collections.singletonMap("shortURL", SHORT_URL_COL));
        setUpdateURL(updateUrl);

        var fullUrlCol = getMutableColumn(FieldKey.fromParts(FULL_URL_COL));
        if (fullUrlCol != null ) fullUrlCol.setLabel("Target URL");

        // Hyperlinked short URL column
        var shortUrlCol = getMutableColumn(FieldKey.fromParts(SHORT_URL_COL));
        if (shortUrlCol != null)
        {
            shortUrlCol.setDisplayColumnFactory(new DisplayColumnFactory()
            {
                @Override
                public DisplayColumn createRenderer(ColumnInfo colInfo)
                {
                    return new DataColumn(colInfo)
                    {
                        @Override
                        public String renderURL(RenderContext ctx)
                        {
                            String shortUrl = (String) getValue(ctx);
                            return shortUrl != null ? ShortURLRecord.renderShortURL(shortUrl) : super.renderURL(ctx);
                        }
                    };
                }
            });
        }

        // Copy to Clipboard column
        var copyToClipboardCol = addWrapColumn(TO_CLIPBOARD_COL, getRealTable().getColumn(SHORT_URL_COL));
        copyToClipboardCol.setLabel("");
        copyToClipboardCol.setDisplayColumnFactory(colInfo -> new DataColumn(colInfo)
        {
            @Override
            public @NotNull Set<ClientDependency> getClientDependencies()
            {
                Set<ClientDependency> dependencies = super.getClientDependencies();
                dependencies.add(ClientDependency.fromPath("internal/clipboard/clipboard-1.5.9.min.js"));
                return dependencies;
            }

            @Override
            public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
            {
                String shortUrl = (String) getValue(ctx);
                Integer rowId = ctx.get(FieldKey.fromParts(ROWID_COL), Integer.class);
                if (shortUrl != null && rowId != null)
                {
                    var elementId = "copyToClipboardId" + rowId;
                    PageFlowUtil.iconLink("fa fa-clipboard", "copy to clipboard")
                            .onClick("return false;")
                            .id(elementId)
                            .attributes(Collections.singletonMap("data-clipboard-text", ShortURLRecord.renderShortURL(shortUrl)))
                            .appendTo(out);
                    DOM.SCRIPT(HtmlString.unsafe("new Clipboard('#" + elementId + "');")).appendTo(out);
                }
                else
                {
                    super.renderGridCellContents(ctx, out);
                }
            }

            @Override
            public void addQueryFieldKeys(Set<FieldKey> keys)
            {
                super.addQueryFieldKeys(keys);
                keys.add(FieldKey.fromString(ROWID_COL));
            }
        });

        List<FieldKey> defaultCols = new ArrayList<>();
        defaultCols.add(FieldKey.fromParts("Created"));
        defaultCols.add(FieldKey.fromParts("CreatedBy"));
        defaultCols.add(FieldKey.fromParts(SHORT_URL_COL));
        defaultCols.add(FieldKey.fromParts(TO_CLIPBOARD_COL));
        defaultCols.add(FieldKey.fromParts(FULL_URL_COL));
        setDefaultVisibleColumns(defaultCols);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new RowIdQueryUpdateService<ShortURLRecord>(this)
        {
            @Override
            protected ShortURLRecord createNewBean()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            protected ShortURLRecord insert(User user, Container container, ShortURLRecord bean)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            protected ShortURLRecord update(User user, Container container, ShortURLRecord bean, Integer oldKey)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public ShortURLRecord get(User user, Container container, int key)
            {
                return new TableSelector(CoreSchema.getInstance().getTableInfoShortURL()).getObject(key, ShortURLRecord.class);
            }

            @Override
            public void delete(User user, Container container, int key) throws QueryUpdateServiceException
            {
                ShortURLRecord shortUrl = get(user, container, key);
                if (shortUrl == null)
                {
                    throw new NotFoundException("No short URL record found for rowId " + key);
                }

                try
                {
                    ShortURLService.get().deleteShortURL(shortUrl, user);
                }
                catch (ValidationException e)
                {
                    String err = "Unable to delete short URL " + shortUrl.getShortURL();
                    throw new QueryUpdateServiceException(err + ". " + StringUtils.join(e.getAllErrors(), ". "), e);
                }
            }
        };
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return canDisplayTable(user, getContainer()) && getContainer().hasPermission(user, perm);
    }

    public static boolean canDisplayTable(@NotNull UserPrincipal user, @NotNull Container container)
    {
        return container.isRoot() && container.hasPermission(user, AdminOperationsPermission.class);
    }
}
