package org.labkey.issue.model;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.BlockingCache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.Filter;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.issue.model.IssueManager.CustomColumn;
import org.labkey.issue.model.IssueManager.CustomColumnConfiguration;
import org.labkey.issue.model.IssueManager.CustomColumnMap;

import java.sql.SQLException;

/**
 * User: adam
 * Date: 1/27/13
 * Time: 2:41 PM
 */
public class ColumnConfigurationCache
{
    private static final BlockingCache<Container, CustomColumnConfiguration> CACHE = CacheManager.getBlockingCache(1000, CacheManager.DAY, "Issues Column Configurations", new org.labkey.api.cache.CacheLoader<Container, CustomColumnConfiguration>()
    {
        @Override
        public CustomColumnConfiguration load(Container c, @Nullable Object argument)
        {
            final CustomColumnMap map = new CustomColumnMap();
            Filter filter = new SimpleFilter(new FieldKey(null, "Container"), c);
            new TableSelector(IssuesSchema.getInstance().getTableInfoCustomColumns(), filter, null).forEach(
                new Selector.ForEachBlock<CustomColumn>() {
                    @Override
                    public void exec(IssueManager.CustomColumn cc) throws SQLException
                    {
                        map.put(cc.getName(), cc);
                    }
                }, IssueManager.CustomColumn.class);

            return new CustomColumnConfiguration(map);
        }
    });

    static CustomColumnConfiguration get(Container c)
    {
        return CACHE.get(c);
    }

    static void uncache(Container c)
    {
        CACHE.remove(c);
    }
}
