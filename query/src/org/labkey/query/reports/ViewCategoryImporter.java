/*
 * Copyright (c) 2011-2019 LabKey Corporation
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
package org.labkey.query.reports;

import org.apache.logging.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.AbstractFolderImportFactory;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.admin.ImportException;
import org.labkey.api.admin.InvalidFileException;
import org.labkey.api.data.Container;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.reports.model.ViewCategoryManager;
import org.labkey.api.security.User;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.folder.xml.FolderDocument.Folder;
import org.labkey.folder.xml.viewCategory.CategoriesDocument;
import org.labkey.folder.xml.viewCategory.CategoryType;
import org.labkey.folder.xml.viewCategory.ViewCategoryType;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

/**
 * User: klum
 * Date: Oct 21, 2011
 */

// This is not designated <Folder> because a StudyImportContext is passed in the case of a study archive import. Once
// we stop supporting view_categories.xml in the study folder we can switch this to <Folder> and remove the hacks in
// getXmlBean().
public class ViewCategoryImporter implements FolderImporter
{
    @Override
    public String getDescription()
    {
        return "view categories";
    }

    @Override
    public String getDataType() { return FolderArchiveDataTypes.VIEW_CATEGORIES; }

    @Override
    public void process(@Nullable PipelineJob job, ImportContext ctx, VirtualFile root) throws Exception
    {
        if (!ctx.isDataTypeSelected(getDataType()))
            return;

        if (!isValidForImportArchive(ctx))
            return;

        try
        {
            XmlObject xml = getXmlBean(ctx);
            if (xml instanceof CategoriesDocument doc)
            {
                // Newer "folder"-namespace CategoriesDocument
                xml.validate(XmlBeansUtil.getDefaultParseOptions());
                process(ctx.getContainer(), ctx.getUser(), ctx.getLogger(), doc);
            }
            else if (xml instanceof org.labkey.study.xml.viewCategory.CategoriesDocument oldDoc)
            {
                // Older "study"-namespace CategoriesDocument - deprecated in 22.2, remove support in 27.2
                xml.validate(XmlBeansUtil.getDefaultParseOptions());
                process(ctx.getContainer(), ctx.getUser(), ctx.getLogger(), oldDoc);
            }
        }
        catch (XmlException x)
        {
            throw new InvalidFileException(root.getRelativePath(ViewCategoryWriter.FILE_NAME), x);
        }
    }

    @Override
    public boolean isValidForImportArchive(ImportContext ctx) throws ImportException
    {
        return getXmlBean(ctx) != null;
    }

    private @Nullable XmlObject getXmlBean(ImportContext ctx) throws ImportException
    {
        try
        {
            if (ctx.getXml() != null && ctx.getXml() instanceof Folder folderXml)
            {
                // New folder archives export the category xml file at the root with a "categories" element in folder.xml
                FolderDocument.Folder.Categories categories = folderXml.getCategories();

                if (null != categories)
                {
                    String filename = categories.getFile();

                    if (null != filename)
                        return ctx.getRoot().getXmlBean(filename);
                }

                // Backward compatibility: Old folder archives (pre-21.11) exported view_categories.xml in the study folder
                VirtualFile study = ctx.getDir("study");
                return null != study ? study.getXmlBean(ViewCategoryWriter.FILE_NAME) : null;
            }

            // Backward compatibility: Really old study archive might have the category xml file at the root of the study node
            return ctx.getRoot().getXmlBean(ViewCategoryWriter.FILE_NAME);
        }
        catch (IOException e)
        {
            return null;
        }
    }

    private void process(Container c, User user, Logger logger, @NotNull CategoriesDocument doc) throws Exception
    {
        logger.info("Loading " + getDescription());

        DbScope scope = CoreSchema.getInstance().getSchema().getScope();

        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            XmlBeansUtil.validateXmlDocument(doc);
            ViewCategoryType categoryType = doc.getCategories();

            if (categoryType != null)
            {
                for (CategoryType type : categoryType.getCategoryArray())
                {
                    String[] parts;
                    if (type.getParent() != null)
                        parts = new String[]{type.getParent(), type.getLabel()};
                    else
                        parts = new String[]{type.getLabel()};

                    ViewCategory category = ViewCategoryManager.getInstance().ensureViewCategory(c, user, parts);

                    category.setDisplayOrder(type.getDisplayOrder());
                    ViewCategoryManager.getInstance().saveCategory(c, user, category);
                }
            }
            transaction.commit();
        }

        logger.info("Done importing " + getDescription());
    }

    private void process(Container c, User user, Logger logger, @NotNull org.labkey.study.xml.viewCategory.CategoriesDocument doc) throws Exception
    {
        logger.info("Loading " + getDescription());

        DbScope scope = CoreSchema.getInstance().getSchema().getScope();

        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            XmlBeansUtil.validateXmlDocument(doc);
            org.labkey.study.xml.viewCategory.ViewCategoryType categoryType = doc.getCategories();

            if (categoryType != null)
            {
                for (org.labkey.study.xml.viewCategory.CategoryType type : categoryType.getCategoryArray())
                {
                    String[] parts;
                    if (type.getParent() != null)
                        parts = new String[]{type.getParent(), type.getLabel()};
                    else
                        parts = new String[]{type.getLabel()};

                    ViewCategory category = ViewCategoryManager.getInstance().ensureViewCategory(c, user, parts);

                    category.setDisplayOrder(type.getDisplayOrder());
                    ViewCategoryManager.getInstance().saveCategory(c, user, category);
                }
            }
            transaction.commit();
        }

        logger.info("Done importing " + getDescription());
    }

    @Override
    public @NotNull Collection<PipelineJobWarning> postProcess(ImportContext ctx, VirtualFile root)
    {
        return Collections.emptyList();
    }

    public static class Factory extends AbstractFolderImportFactory
    {
        @Override
        public FolderImporter create()
        {
            return new ViewCategoryImporter();
        }
    }
}
