/*
 * Copyright (c) 2011-2012 LabKey Corporation
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
package org.labkey.api.reports.model;

import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.labkey.api.cache.DbCache;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.util.ContainerUtil;
import org.labkey.api.util.TestContext;

import java.beans.PropertyChangeEvent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Oct 12, 2011
 * Time: 7:13:20 PM
 */
public class ViewCategoryManager implements ContainerManager.ContainerListener
{
    private static final Logger _log = Logger.getLogger(ViewCategoryManager.class);
    private static final ViewCategoryManager _instance = new ViewCategoryManager();
    private static final List<ViewCategoryListener> _listeners = new CopyOnWriteArrayList<ViewCategoryListener>();

    private ViewCategoryManager()
    {
        ContainerManager.addContainerListener(this);
    }

    public static ViewCategoryManager getInstance()
    {
        return _instance;
    }

    public TableInfo getTableInfoCategories()
    {
        return CoreSchema.getInstance().getSchema().getTable("ViewCategory");
    }

    public ViewCategory[] getCategories(Container c, User user)
    {
        return getCategories(c, user, new SimpleFilter());
    }

    public ViewCategory[] getCategories(Container c, User user, SimpleFilter filter)
    {
        try {
            filter.addCondition("Container", c);
            ViewCategory[] categories = Table.select(getTableInfoCategories(), Table.ALL_COLUMNS, filter, null, ViewCategory.class);

            return categories;
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
    }

    public ViewCategory getCategory(int rowId)
    {
        try {
            String cacheKey = getCacheKey(rowId);
            ViewCategory category = (ViewCategory) DbCache.get(getTableInfoCategories(), cacheKey);

            if (category != null)
                return category;

            SimpleFilter filter = new SimpleFilter("rowId", rowId);
            ViewCategory[] categories = Table.select(getTableInfoCategories(), Table.ALL_COLUMNS, filter, null, ViewCategory.class);

            assert categories.length <= 1;

            if (categories.length == 1)
            {
                DbCache.put(getTableInfoCategories(), cacheKey, categories[0]);
                return categories[0];
            }
            return null;
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
    }

    public ViewCategory getCategory(Container c, String label)
    {
        try {
            String cacheKey = getCacheKey(c, label);
            ViewCategory category = (ViewCategory) DbCache.get(getTableInfoCategories(), cacheKey);

            if (category != null)
                return category;

            SimpleFilter filter = new SimpleFilter("Container", c);
            filter.addCondition("label", label);

            ViewCategory[] categories = Table.select(getTableInfoCategories(), Table.ALL_COLUMNS, filter, null, ViewCategory.class);

            // should only be one as there is a unique constraint on the db
            assert categories.length <= 1;

            if (categories.length == 1)
            {
                DbCache.put(getTableInfoCategories(), cacheKey, categories[0]);
                return categories[0];
            }
            return null;
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void deleteCategory(Container c, User user, ViewCategory category)
    {
        if (category.isNew())
            throw new IllegalArgumentException("View category has not been saved to the database yet");

        if (!category.canDelete(c, user))
            throw new RuntimeException("You must be an administrator to delete a view category");

        try {
            category = getCategory(category.getRowId());
            if (category == null)
                throw Table.OptimisticConflictException.create(Table.ERROR_DELETED);

            // delete the category definition and fire the deleted event

            // the category table has a delete cascade fk rule, so no need to explicitly delete subcategories
            SQLFragment sql = new SQLFragment("DELETE FROM ").append(getTableInfoCategories(), "").append(" WHERE RowId = ?");
            Table.execute(CoreSchema.getInstance().getSchema(), sql.getSQL(), category.getRowId());

            DbCache.remove(getTableInfoCategories(), getCacheKey(category.getRowId()));
            DbCache.remove(getTableInfoCategories(), getCacheKey(c, category.getLabel()));
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }

        List<Throwable> errors = fireDeleteCategory(user, category);

        if (errors.size() != 0)
        {
            Throwable first = errors.get(0);
            if (first instanceof RuntimeException)
                throw (RuntimeException)first;
            else
                throw new RuntimeException(first);
        }
    }

    public ViewCategory saveCategory(Container c, User user, ViewCategory category)
    {
        try {
            ViewCategory ret = null;
            List<Throwable> errors;

            if (category.isNew())
            {
                // check for duplicates
                SimpleFilter filter = new SimpleFilter("label", category.getLabel());
                if (category.getParent() != null)
                    filter.addCondition(FieldKey.fromParts("parent"), category.getParent().getRowId());

                if (getCategories(c, user, filter).length > 0)
                {
                    if (category.getParent() != null)
                        throw new IllegalArgumentException("There is already a subcategory attached to the same parent with the name: " + category.getLabel());
                    else
                        throw new IllegalArgumentException("There is already a category in this folder with the name: " + category.getLabel());
                }

                if (category.getContainerId() == null)
                    category.setContainerId(c.getId());
                
                ret = Table.insert(user, getTableInfoCategories(), category);
                errors = fireCreatedCategory(user, ret);
            }
            else
            {
                ViewCategory existing = getCategory(category.getRowId());
                if (existing != null)
                {
                    existing.setLabel(category.getLabel());
                    existing.setDisplayOrder(category.getDisplayOrder());

                    ret = Table.update(user, getTableInfoCategories(), existing, existing.getRowId());

                    DbCache.remove(getTableInfoCategories(), getCacheKey(existing.getRowId()));
                    DbCache.remove(getTableInfoCategories(), getCacheKey(c, existing.getLabel()));

                    errors = fireUpdateCategory(user, ret);
                }
                else
                    throw new RuntimeException("The specified category does not exist, rowid: " + category.getRowId());
            }
            
            if (errors.size() != 0)
            {
                Throwable first = errors.get(0);
                if (first instanceof RuntimeException)
                    throw (RuntimeException)first;
                else
                    throw new RuntimeException(first);
            }
            return ret;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    public List<ViewCategory> getSubCategories(ViewCategory category)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("parent"), category.getRowId());
        filter.addCondition(FieldKey.fromParts("Container"), category.getContainerId());

        TableSelector selector = new TableSelector(getTableInfoCategories(), filter, null);
        ViewCategory[] categories = selector.getArray(ViewCategory.class);

        return Arrays.asList(categories);
    }

    private String getCacheKey(Container c, String label)
    {
        return "ViewCategory-" + c + "-" + label;
    }

    private String getCacheKey(int categoryId)
    {
        return "ViewCategory-" + categoryId;
    }

    public static void addCategoryListener(ViewCategoryListener listener)
    {
        _listeners.add(listener);
    }

    public static void removeCategoryListener(ViewCategoryListener listener)
    {
        _listeners.remove(listener);
    }

    private static List<Throwable> fireDeleteCategory(User user, ViewCategory category)
    {
        List<Throwable> errors = new ArrayList<Throwable>();

        for (ViewCategoryListener l : _listeners)
        {
            try {
                l.categoryDeleted(user, category);
            }
            catch (Throwable t)
            {
                errors.add(t);
            }
        }
        return errors;
    }

    private static List<Throwable> fireUpdateCategory(User user, ViewCategory category)
    {
        List<Throwable> errors = new ArrayList<Throwable>();

        for (ViewCategoryListener l : _listeners)
        {
            try {
                l.categoryUpdated(user, category);
            }
            catch (Throwable t)
            {
                errors.add(t);
            }
        }
        return errors;
    }

    private static List<Throwable> fireCreatedCategory(User user, ViewCategory category)
    {
        List<Throwable> errors = new ArrayList<Throwable>();

        for (ViewCategoryListener l : _listeners)
        {
            try {
                l.categoryCreated(user, category);
            }
            catch (Throwable t)
            {
                errors.add(t);
            }
        }
        return errors;
    }

    /**
     * Returns an existing category or creates a new one.
     */
    public ViewCategory ensureViewCategory(Container c, User user, String label)
    {
        ViewCategory category = getCategory(c, label);
        if (category == null)
        {
            category = new ViewCategory();

            category.setContainer(c.getId());
            category.setLabel(label);

            category = saveCategory(c, user, category);
        }
        return category;
    }

    static
    {
        ObjectFactory.Registry.register(ViewCategory.class, new ViewCategoryFactory());
    }

    public static class ViewCategoryFactory implements ObjectFactory<ViewCategory>
    {
        @Override
        public ViewCategory fromMap(Map<String, ?> m)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public ViewCategory fromMap(ViewCategory bean, Map<String, ?> m)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, Object> toMap(ViewCategory bean, @Nullable Map<String, Object> m)
        {
            if (null == m)
                m = new CaseInsensitiveHashMap<Object>();

            m.put("RowId", bean.getRowId());
            m.put("Label", bean.getLabel());
            m.put("DisplayOrder", bean.getDisplayOrder());
            m.put("ContainerId", bean.getContainerId());

            if (null != bean.getParent())
            {
                m.put("Parent", bean.getParent().getRowId());
            }

            return m;
        }

        @Override
        public ViewCategory handle(ResultSet rs) throws SQLException
        {
            ViewCategory vc;

            int parentId = rs.getInt("Parent");
            ViewCategory parent = ViewCategoryManager.getInstance().getCategory(parentId);

            vc = new ViewCategory(rs.getString("Label"), rs.getInt("RowId"), rs.getInt("DisplayOrder"), parent);
            vc.setContainerId(rs.getString("Container"));

            return vc;
        }

        @Override
        public ArrayList<ViewCategory> handleArrayList(ResultSet rs) throws SQLException
        {
            ArrayList<ViewCategory> list = new ArrayList<ViewCategory>();
            while (rs.next())
            {
                list.add(handle(rs));
            }
            return list;
        }

        @Override
        public ViewCategory[] handleArray(ResultSet rs) throws SQLException
        {
            ArrayList<ViewCategory> list = handleArrayList(rs);
            return list.toArray(new ViewCategory[list.size()]);
        }
    }

    public static class TestCase extends Assert
    {
        private static final String[] labels = {"Demographics", "Exam", "Discharge", "Final Exam"};
        private static final String[] subLabels = {"sub1", "sub2", "sub3"};

        @Test
        public void test() throws Exception
        {
            ViewCategoryManager mgr = ViewCategoryManager.getInstance();
            Container c = ContainerManager.getSharedContainer();
            User user = TestContext.get().getUser();

            final List<String> notifications = new ArrayList<String>();
            for (String label : labels)
                notifications.add(label);

            ViewCategoryListener listener = new ViewCategoryListener(){
                @Override
                public void categoryDeleted(User user, ViewCategory category)
                {
                    notifications.remove(category.getLabel());
                }

                @Override
                public void categoryCreated(User user, ViewCategory category)
                {
                }

                @Override
                public void categoryUpdated(User user, ViewCategory category)
                {
                }
            };
            ViewCategoryManager.addCategoryListener(listener);

            // create some categories
            int i=0;
            for (String label : labels)
            {
                ViewCategory cat = new ViewCategory();

                cat.setLabel(label);
                cat.setDisplayOrder(i++);

                cat = mgr.saveCategory(c, user, cat);

                // create sub categories
                for (String subLabel : subLabels)
                {
                    ViewCategory subcat = new ViewCategory();

                    subcat.setLabel(subLabel);
                    subcat.setDisplayOrder(i++);
                    subcat.setParent(cat);

                    mgr.saveCategory(c, user, subcat);
                }

                // verify we don't allow duplicate subcategory names
                boolean duplicate = false;
                try {

                    ViewCategory subcat = new ViewCategory();

                    subcat.setLabel(subLabels[0]);
                    subcat.setDisplayOrder(i++);
                    subcat.setParent(cat);

                    mgr.saveCategory(c, user, subcat);
                }
                catch (IllegalArgumentException e)
                {
                    duplicate = true;
                }

                assertTrue("Duplicate subcategory name was allowed", duplicate);
            }

            // get categories
            Map<String, ViewCategory> categoryMap = new HashMap<String, ViewCategory>();
            for (ViewCategory cat : mgr.getCategories(c, user))
            {
                categoryMap.put(cat.getLabel(), cat);
            }

            List<String> subCategoryNames = Arrays.asList(subLabels);
            for (String label : labels)
            {
                assertTrue(categoryMap.containsKey(label));

                // check for subcategories
                ViewCategory cat = categoryMap.get(label);
                for (ViewCategory subCategory : cat.getSubcategories())
                {
                    assertTrue(subCategoryNames.contains(subCategory.getLabel()));
                    assertTrue(subCategory.getParent().getRowId() == cat.getRowId());
                }
            }

            // delete the top level categories, make sure the subcategories get deleted as well
/*
            for (String label : labels)
            {
                ViewCategory cat = categoryMap.get(label);
                mgr.deleteCategory(c, user, cat);
            }
*/
            for (ViewCategory cat : categoryMap.values())
            {
                mgr.deleteCategory(c, user, cat);
            }

            // make sure all the listeners were invoked correctly
            assertTrue(notifications.isEmpty());
            ViewCategoryManager.removeCategoryListener(listener);
        }
    }

    @Override
    public void containerCreated(Container c, User user){}
    @Override
    public void containerMoved(Container c, Container oldParent, User user){}
    @Override
    public void propertyChange(PropertyChangeEvent evt){}

    @Override
    public void containerDeleted(Container c, User user)
    {
        try {
            ContainerUtil.purgeTable(getTableInfoCategories(), c, "Container");
        }
        catch (SQLException x)
        {
            _log.error("Error occurred deleting categories for container", x);
        }
    }
}
