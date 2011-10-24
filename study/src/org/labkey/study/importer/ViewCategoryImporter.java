package org.labkey.study.importer;

import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.labkey.api.data.DbScope;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.reports.model.ViewCategoryManager;
import org.labkey.api.study.InvalidFileException;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.StudySchema;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.writer.ViewCategoryWriter;
import org.labkey.study.xml.viewCategory.CategoriesDocument;
import org.labkey.study.xml.viewCategory.CategoryType;
import org.labkey.study.xml.viewCategory.ViewCategoryType;
import org.springframework.validation.BindException;

/**
 * Created by IntelliJ IDEA.
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
    public void process(StudyImpl study, ImportContext ctx, VirtualFile root, BindException errors) throws Exception
    {
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

    public void process(StudyImpl study, ImportContext ctx, XmlObject xmlObject) throws Exception
    {
        if (xmlObject instanceof CategoriesDocument)
        {
            DbScope scope = StudySchema.getInstance().getSchema().getScope();

            try
            {
                scope.ensureTransaction();
                CategoriesDocument doc = (CategoriesDocument)xmlObject;
                XmlBeansUtil.validateXmlDocument(doc);

                ViewCategoryType categoryType = doc.getCategories();

                if (categoryType != null)
                {
                    for (CategoryType type : categoryType.getCategoryArray())
                    {
                        ViewCategory category = ViewCategoryManager.getInstance().ensureViewCategory(ctx.getContainer(), ctx.getUser(), type.getLabel());
                        category.setDisplayOrder(type.getDisplayOrder());

                        ViewCategoryManager.getInstance().saveCategory(ctx.getContainer(), ctx.getUser(), category);
                    }
                }
                scope.commitTransaction();
            }
            finally
            {
                scope.closeConnection();
            }
        }
    }
}
