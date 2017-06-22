/*
 * Copyright (c) 2011-2017 LabKey Corporation
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

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.data.BeanObjectFactory;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exceptions.OptimisticConflictException;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.security.User;
import org.labkey.api.util.ContainerUtil;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * User: klum
 * Date: Oct 12, 2011
 * Time: 7:13:20 PM
 */
public class ViewCategoryManager extends ContainerManager.AbstractContainerListener
{
    private static final ViewCategoryManager _instance = new ViewCategoryManager();
    private static final List<ViewCategoryListener> _listeners = new CopyOnWriteArrayList<>();

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

    public List<ViewCategory> getAllCategories(Container c)
    {
        return ViewCategoryCache.get().getAllCategories(c.getId());
    }

    public List<ViewCategory> getTopLevelCategories(Container c)
    {
        return ViewCategoryCache.get().getTopLevelCategories(c.getId());
    }

    public ViewCategory getCategory(Container c, int rowId)
    {
        return getCategory(c.getId(), rowId);
    }

    public ViewCategory getCategory(String containerId, int rowId)
    {
        return ViewCategoryCache.get().getViewCategory(containerId, rowId);
    }

    /**
     * looks up a view category by its path
     * @param c Container
     * @param parts An array representing a path hierarchy. For example ["reports"] would represent a top level
     *              category named 'reports' whereas ["reports", "R"] would represent a subcategory named 'R' with
     *              a parent named 'reports'. Current hierarchy depth is limited to one level deep.
     * @return The ViewCategory or null
     */
    public ViewCategory getCategory(Container c, String... parts)
    {
        return ViewCategoryCache.get().getViewCategory(c.getId(), parts);
    }

    public void deleteCategory(Container c, User user, ViewCategory category)
    {
        if (category.isNew())
            throw new IllegalArgumentException("View category has not been saved to the database yet");

        if (!category.canDelete(c, user))
            throw new RuntimeException("You must be an administrator to delete a view category");

        List<ViewCategory> categoriesToDelete = new ArrayList<>();
        category = getCategory(category.getContainerId(), category.getRowId());
        if (category == null)
            throw OptimisticConflictException.create(Table.ERROR_DELETED);

        categoriesToDelete.add(category);
        categoriesToDelete.addAll(category.getSubcategories());

        // delete the category definition (plus any subcategories) and fire the deleted event
        SQLFragment sql = new SQLFragment("DELETE FROM ").append(getTableInfoCategories(), "").append(" WHERE RowId = ?");
        sql.append(" OR Parent = ?");
        sql.addAll(category.getRowId(), category.getRowId());

        SqlExecutor executor = new SqlExecutor(CoreSchema.getInstance().getSchema().getScope());
        executor.execute(sql);

        ViewCategoryCache.get().clear(c);

        List<Throwable> errors = new ArrayList<>();
        for (ViewCategory vc : categoriesToDelete)
            errors.addAll(fireDeleteCategory(user, vc));

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
        ViewCategory ret;
        List<Throwable> errors;

        if (category.isNew()) // insert
        {
            boolean topLevelCategory = (null == category.getParent());
            final String[] parts;

            if (topLevelCategory)
            {
                parts = new String[]{category.getLabel()};
            }
            else
            {
                ViewCategory parent = category.getParentCategory();

                if (null == parent)
                    throw new IllegalStateException("Parent does not exist");

                parts = new String[]{parent.getLabel(), category.getLabel()};
            }

            ViewCategory dup = ViewCategoryManager.getInstance().getCategory(c, parts);

            if (null != dup)
            {
                if (topLevelCategory)
                    throw new IllegalArgumentException("There is already a subcategory attached to the same parent with the name: " + category.getLabel());
                else
                    throw new IllegalArgumentException("There is already a category in this folder with the name: " + category.getLabel());
            }
            category.beforeInsert(user, c.getId());

            ret = Table.insert(user, getTableInfoCategories(), category);

            ViewCategoryCache.get().clear(c);

            errors = fireCreatedCategory(user, ret);
        }
        else // update
        {
            ViewCategory existing = getCategory(c, category.getRowId());
            if (existing != null)
            {
                existing.setLabel(category.getLabel());
                existing.setDisplayOrder(category.getDisplayOrder());

                ret = Table.update(user, getTableInfoCategories(), existing, existing.getRowId());

                ViewCategoryCache.get().clear(c);

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

    public List<ViewCategory> getSubcategories(ViewCategory category)
    {
        return ViewCategoryCache.get().getSubcategories(category.getContainerId(), category.getRowId());
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
        List<Throwable> errors = new ArrayList<>();

        for (ViewCategoryListener l : _listeners)
        {
            try
            {
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
        List<Throwable> errors = new ArrayList<>();

        for (ViewCategoryListener l : _listeners)
        {
            try
            {
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
        List<Throwable> errors = new ArrayList<>();

        for (ViewCategoryListener l : _listeners)
        {
            try
            {
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
     * @param parts An array representing a path hierarchy. For example ["reports"] would represent a top level
     *              category named 'reports' whereas ["reports", "R"] would represent a subcategory named 'R' with
     *              a parent named 'reports'. Current hierarchy depth is limited to one level deep.
     * @return The requested ViewCategory
     */

    public ViewCategory ensureViewCategory(Container c, User user, String... parts)
    {
        if (parts.length > 2)
            throw new IllegalArgumentException("Only one level of view category is supported at this time");

        ViewCategory category = getCategory(c, parts);
        if (category == null)
        {
            ViewCategory parent = null;

            if (parts.length == 2)
            {
                parent = getCategory(c, parts[0]);
                if (parent == null)
                {
                    parent = new ViewCategory(parts[0], c, null);
                    parent = saveCategory(c, user, parent);
                }
            }
            category = new ViewCategory(parts[parts.length - 1], c, parent);
            category = saveCategory(c, user, category);
        }
        return category;
    }

    public static final String ENCODED_AMP = "&amp;";
    /**
     * Used to encode a view category to a serializable form that can later be decoded by the decode(String)
     * method and then used in either getCategory or ensureViewCategory.
     *
     * Parent child hierarchy is encoded into the string.
     *
     * @return encoded String that can be serialized and then decoded using the decode(String) method.
     */
    public String encode(ViewCategory category)
    {
        StringBuilder sb = new StringBuilder();

        if (category.getParentCategory() != null)
        {
            sb.append(category.getParentCategory().getLabel().replaceAll("&", ENCODED_AMP)).append("&");
        }
        sb.append(category.getLabel().replaceAll("&", ENCODED_AMP));

        return sb.toString();
    }

    /**
     * Used to decode a view category from a serializable form and then used in either getCategory or ensureViewCategory.
     *
     * Parent child hierarchy is encoded into the string.
     *
     * @return String[] that represents the path hierarchy of the category.
     */
    public String[] decode(String category)
    {
        List<String> names = new ArrayList<>();
        String[] originalAmps;

        StringBuffer sb = new StringBuffer();
        String delim = "";
        String trailing = "";

        // split on any original but encoded &'s
        if (category.contains(ENCODED_AMP))
        {
            // boundary cases
            if (category.startsWith(ENCODED_AMP))
                delim = "&";
            if (category.endsWith(ENCODED_AMP))
                trailing = "&";

            originalAmps = category.split(ENCODED_AMP);
        }
        else
            originalAmps = new String[]{category};

        for (String section : originalAmps)
        {
            // re-add the original &
            sb.append(delim);
            for (int i=0; i < section.length(); i++)
            {
                char c = section.charAt(i);

                switch (c)
                {
                    case '&' :
                        // this is the section delimiter
                        names.add(sb.toString());
                        sb = new StringBuffer();
                        break;
                    default :
                        sb.append(c);
                }
            }
            delim = "&";
        }
        sb.append(trailing);

        if (sb.length() > 0)
            names.add(sb.toString());

        return names.toArray(new String[names.size()]);
    }

    static
    {
        ObjectFactory.Registry.register(ViewCategory.class, new ViewCategoryFactory());
    }

    public static class ViewCategoryFactory extends BeanObjectFactory<ViewCategory>
    {
        ViewCategoryFactory()
        {
            super(ViewCategory.class);
        }

        @Override
        protected void fixupMap(Map<String, Object> m, ViewCategory bean)
        {
            if (null != bean.getParentCategory())
            {
                m.put("Parent", bean.getParentCategory().getRowId());
            }
        }
    }

    public class ViewCategoryTreeNode
    {
        private final ViewCategory _viewCategory;
        private List<ViewCategoryTreeNode> _children = new ArrayList<>();
        private boolean _isUserSubscribed;

        public ViewCategoryTreeNode(ViewCategory viewCategory)
        {
            _viewCategory = viewCategory;
        }

        public List<ViewCategoryTreeNode> getChildren()
        {
            return _children;
        }

        public void setChildren(List<ViewCategoryTreeNode> children)
        {
            _children = children;
        }

        public void addChild(ViewCategoryTreeNode viewCategoryTreeNode)
        {
            _children.add(viewCategoryTreeNode);
        }
        public boolean isUserSubscribed()
        {
            return _isUserSubscribed;
        }

        public void setUserSubscribed(boolean isUserSubscribed)
        {
            _isUserSubscribed = isUserSubscribed;
        }

        public ViewCategory getViewCategory()
        {
            return _viewCategory;
        }
    }

    public static final int UNCATEGORIZED_ROWID = 0;

    public List<ViewCategoryTreeNode> getCategorySubcriptionTree(Container container, Set<Integer> subscriptionSet)
    {
        // We take advantage of the fact that this is at most 2 levels; the 1st level may have multiple categories
        List<ViewCategory> categories = getAllCategories(container);
        sortViewCategories(categories);
        List<ViewCategoryTreeNode> viewCategoryTreeNodes = new ArrayList<>();

        // Add Uncategorized category
        ViewCategory uncategorizedCategory = ReportUtil.getDefaultCategory(container, null, null);
        uncategorizedCategory.setRowId(UNCATEGORIZED_ROWID);
        viewCategoryTreeNodes.add(makeTreeNode(uncategorizedCategory, subscriptionSet));

        // Make a modifiable copy, #21696
        categories.stream().filter(category -> null == category.getParentCategory()).forEach(category -> {
            ViewCategoryTreeNode treeNode = makeTreeNode(category, subscriptionSet);
            // Make a modifiable copy, #21696
            List<ViewCategory> subCategories = new ArrayList<>(category.getSubcategories());
            sortViewCategories(subCategories);
            for (ViewCategory subCategory : subCategories)
            {
                ViewCategoryTreeNode subTreeNode = makeTreeNode(subCategory, subscriptionSet);
                treeNode.addChild(subTreeNode);
            }
            viewCategoryTreeNodes.add(treeNode);
        });

        return viewCategoryTreeNodes;
    }

    private ViewCategoryTreeNode makeTreeNode(ViewCategory category, Set<Integer> subscriptionSet)
    {
        ViewCategoryTreeNode treeNode = new ViewCategoryTreeNode(category);
        if (subscriptionSet.contains(category.getRowId()))
            treeNode.setUserSubscribed(true);
        return treeNode;
    }

    public static void sortViewCategories(List<ViewCategory> categories)
    {
        categories.sort((o1, o2) ->
        {
            int ret = o1.getDisplayOrder() - o2.getDisplayOrder();
            if (0 == ret)
                ret = o1.getLabel().compareToIgnoreCase(o2.getLabel());
            return ret;
        });
    }

    public static class TestCase extends Assert
    {
        private static final String[] labels = {"Demographics", "Exam", "Discharge", "Final Exam"};
        private static final String[] subLabels = {"sub1", "sub&2", "sub3&", "sub &label", "sub_label&amp;", "&sub&label&"};

        private static ViewCategoryManager mgr;
        private static Container c;
        private static User user;
        private static ViewCategoryListener listener;

        @BeforeClass
        public static void setUp()
        {
            mgr = ViewCategoryManager.getInstance();
            c = JunitUtil.getTestContainer();
            user = TestContext.get().getUser();
        }

        @Test
        public void testCategoryCreation()
        {
            final List<String> notifications = new ArrayList<>();
            notifications.addAll(Arrays.asList(labels));

            listener = new ViewCategoryListener(){
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

                // test serialization encoding and decoding
                String encoded = mgr.encode(cat);
                String parts[] = mgr.decode(encoded);

                assertTrue(parts.length == 1);
                assertEquals(parts[0], cat.getLabel());

                // create sub categories
                for (String subLabel : subLabels)
                {
                    ViewCategory subcat = new ViewCategory();

                    subcat.setLabel(subLabel);
                    subcat.setDisplayOrder(i++);
                    subcat.setParentCategory(cat);
                    subcat.setContainerId(c.getId());

                    subcat = mgr.saveCategory(c, user, subcat);

                    // test serialization encoding and decoding
                    encoded = mgr.encode(subcat);
                    parts = mgr.decode(encoded);

                    assertTrue(parts.length == 2);
                    assertEquals(parts[0], cat.getLabel());
                    assertEquals(parts[1], subLabel);
                }

                // verify we don't allow duplicate subcategory names
                boolean duplicate = false;
                try
                {

                    ViewCategory subcat = new ViewCategory();

                    subcat.setLabel(subLabels[0]);
                    subcat.setDisplayOrder(i++);
                    subcat.setParentCategory(cat);
                    subcat.setContainer(cat.getContainerId());

                    mgr.saveCategory(c, user, subcat);
                }
                catch (IllegalArgumentException e)
                {
                    duplicate = true;
                }

                assertTrue("Duplicate subcategory name was allowed", duplicate);
            }

            // get top-level categories
            Map<String, ViewCategory> categoryMap = new HashMap<>();
            mgr.getAllCategories(c)
                .stream()
                .filter(cat -> null == cat.getParentCategory())
                .forEach(cat -> categoryMap.put(cat.getLabel(), cat));

            assertEquals(labels.length, categoryMap.size());
            List<String> subCategoryNames = Arrays.asList(subLabels);

            for (String label : labels)
            {
                assertTrue(categoryMap.containsKey(label));

                // check for subcategories
                ViewCategory cat = categoryMap.get(label);
                for (ViewCategory subCategory : cat.getSubcategories())
                {
                    assertTrue(subCategoryNames.contains(subCategory.getLabel()));
                    assertTrue(subCategory.getParentCategory().getRowId() == cat.getRowId());
                }
            }

            // delete the top level categories, make sure the subcategories get deleted as well
            for (String label : labels)
            {
                ViewCategory cat = categoryMap.get(label);

                mgr.deleteCategory(c, user, cat);
            }

            // make sure all the listeners were invoked correctly
            assertTrue(notifications.isEmpty());
            ViewCategoryManager.removeCategoryListener(listener);
        }

        @Test
        public void testEnsureCategories()
        {
            ViewCategory top = mgr.ensureViewCategory(c, user, "top");

            assertNotNull(top);
            assertTrue(top.getParentCategory() == null);
            assertTrue(top.getLabel().equals("top"));

            ViewCategory subTop = mgr.ensureViewCategory(c, user, "top", "sub");
            // issue : 17123
            mgr.ensureViewCategory(c, user, "top", "top");
            mgr.ensureViewCategory(c, user, "top");

            assertNotNull(subTop);
            assertTrue(subTop.getParentCategory().getLabel().equals("top"));
            assertTrue(subTop.getLabel().equals("sub"));

            ViewCategory subBottom = mgr.ensureViewCategory(c, user, "bottom", "sub");

            assertNotNull(subBottom);
            assertTrue(subBottom.getParentCategory().getLabel().equals("bottom"));
            assertTrue(subBottom.getLabel().equals("sub"));

            mgr.deleteCategory(c, user, top);
            mgr.deleteCategory(c, user, subBottom.getParentCategory());
        }

        @AfterClass
        public static void tearDown()
        {
            ContainerManager.delete(c, user);

            if (listener != null)
                ViewCategoryManager.removeCategoryListener(listener);

            listener = null;
            mgr = null;
            c = null;
            user = null;
        }
    }

    @Override
    public void containerDeleted(Container c, User user)
    {
        ContainerUtil.purgeTable(getTableInfoCategories(), c, "Container");
    }
}
