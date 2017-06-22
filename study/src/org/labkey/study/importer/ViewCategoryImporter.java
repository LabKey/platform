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
package org.labkey.study.importer;

import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.labkey.api.admin.ImportException;
import org.labkey.api.data.DbScope;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.reports.model.ViewCategoryManager;
import org.labkey.api.admin.InvalidFileException;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.StudySchema;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.writer.StudyArchiveDataTypes;
import org.labkey.study.writer.ViewCategoryWriter;
import org.labkey.study.xml.viewCategory.CategoriesDocument;
import org.labkey.study.xml.viewCategory.CategoryType;
import org.labkey.study.xml.viewCategory.ViewCategoryType;
import org.springframework.validation.BindException;

import java.io.IOException;

/**
 * User: klum
 * Date: Oct 21, 2011
 */
public class ViewCategoryImporter implements InternalStudyImporter
{
    @Override
    public String getDescription()
    {
        return "view categories";
    }

    @Override
    public String getDataType() { return StudyArchiveDataTypes.VIEW_CATEGORIES; }

    @Override
    public void process(StudyImportContext ctx, VirtualFile root, BindException errors) throws Exception
    {
        if (!ctx.isDataTypeSelected(getDataType()))
            return;

        if (!isValidForImportArchive(ctx, root))
            return;

        StudyImpl study = ctx.getStudy();
        try
        {
            XmlObject xml = root.getXmlBean(ViewCategoryWriter.FILE_NAME);
            if (xml instanceof CategoriesDocument)
            {
                xml.validate(XmlBeansUtil.getDefaultParseOptions());
                process(study, ctx, xml);
            }
        }
        catch (XmlException x)
        {
            throw new InvalidFileException(root.getRelativePath(ViewCategoryWriter.FILE_NAME), x);
        }
    }

    @Override
    public boolean isValidForImportArchive(StudyImportContext ctx, VirtualFile root) throws ImportException
    {
        try
        {
            return root != null && root.getXmlBean(ViewCategoryWriter.FILE_NAME) != null;
        }
        catch (IOException e)
        {
            return false;
        }
    }

    public void process(StudyImpl study, StudyImportContext ctx, XmlObject xmlObject) throws Exception
    {
        if (xmlObject instanceof CategoriesDocument)
        {
            ctx.getLogger().info("Loading " + getDescription());

            DbScope scope = StudySchema.getInstance().getSchema().getScope();

            try (DbScope.Transaction transaction = scope.ensureTransaction())
            {
                CategoriesDocument doc = (CategoriesDocument)xmlObject;
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

                        ViewCategory category = ViewCategoryManager.getInstance().ensureViewCategory(ctx.getContainer(), ctx.getUser(), parts);

                        category.setDisplayOrder(type.getDisplayOrder());
                        ViewCategoryManager.getInstance().saveCategory(ctx.getContainer(), ctx.getUser(), category);
                    }
                }
                transaction.commit();
            }

            ctx.getLogger().info("Done importing " + getDescription());
        }
    }
}
