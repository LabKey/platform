/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.api.defaults;

import org.labkey.api.action.SpringActionController;
import org.labkey.api.view.*;
import org.labkey.api.data.*;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.*;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.ACL;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.util.PageFlowUtil;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.ConversionException;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.io.Writer;
import java.io.IOException;
/*
 * User: brittp
 * Date: Jan 27, 2009
 * Time: 4:52:21 PM
 */

@RequiresPermission(ACL.PERM_ADMIN)
public class SetDefaultValuesAction extends DefaultValuesAction
{
    private String _returnUrl;

    public void validateCommand(DomainIdForm target, Errors errors)
    {
    }

    private class DefaultValueDisplayColumn extends DataColumn
    {
        private DomainProperty _property;

        public DefaultValueDisplayColumn(DomainProperty property, ColumnInfo col)
        {
            super(col);
            _property = property;
        }

        public DomainProperty getProperty()
        {
            return _property;
        }

        @Override
        protected boolean renderRequiredIndicators()
        {
            return false;
        }

        @Override
        protected boolean isDisabledInput()
        {
            return false;
        }
    }

    private class DefaultValueDataRegion extends DataRegion
    {
        public void render(RenderContext ctx, Writer out) throws IOException
        {
            renderFormHeader(out, MODE_INSERT);
            renderMainErrors(ctx, out);
            out.write("<table>");
            out.write("<tr><th>Field</th>" +
                    "<th>Initial/Default Value</th>" +
                    "<th>Default type</th><tr>");
            for (DisplayColumn renderer : getDisplayColumns())
            {
                if (!shouldRender(renderer, ctx))
                    continue;
                renderInputError(ctx, out, 1, renderer);
                out.write("<tr>");
                renderer.renderDetailsCaptionCell(ctx, out);
                renderer.renderInputCell(ctx, out, 1);
                out.write("<td>");
                DomainProperty property = ((DefaultValueDisplayColumn) renderer).getProperty();
                DefaultValueType defaultType = property.getDefaultValueTypeEnum();
                if (defaultType == null)
                    defaultType = DefaultValueType.FIXED_EDITABLE;
                out.write(PageFlowUtil.filter(defaultType.getLabel()));
                out.write(PageFlowUtil.helpPopup("Default Value Type: " + defaultType.getLabel(), defaultType.getHelpText(), true));

                out.write("</td>");
                out.write("</tr>");
            }
            out.write("</table>");
            getButtonBar(MODE_INSERT).render(ctx, out);
            renderFormEnd(ctx, out);
        }
    }

    public ModelAndView getView(DomainIdForm domainIdForm, boolean reshow, BindException errors) throws Exception
    {
        _returnUrl = domainIdForm.getReturnUrl();
        Domain domain = getDomain(domainIdForm);
        DataRegion rgn = new DefaultValueDataRegion();
        TableInfo baseTable = OntologyManager.getTinfoObject();
        rgn.setTable(baseTable);
        for (DomainProperty dp : domain.getProperties())
        {
            ColumnInfo info = dp.getPropertyDescriptor().createColumnInfo(baseTable, "objecturi", getViewContext().getUser());
            rgn.addDisplayColumn(new DefaultValueDisplayColumn(dp, info));
        }
        InsertView view = new InsertView(rgn, errors);

        if (reshow)
            view.setInitialValues(domainIdForm.getRequest().getParameterMap());
        else
        {
            Map<DomainProperty, Object> defaults = DefaultValueService.get().getDefaultValues(domainIdForm.getContainer(), domain);
            Map<String, String> formDefaults = new HashMap<String, String>();
            for (Map.Entry<DomainProperty, Object> entry : defaults.entrySet())
            {
                if (entry.getValue() != null)
                {
                    String stringValue = entry.getValue().toString();
                    formDefaults.put(ColumnInfo.propNameFromName(entry.getKey().getName()), stringValue);
                }
            }
            view.setInitialValues(formDefaults);
        }

        boolean defaultsDefined = DefaultValueService.get().hasDefaultValues(domainIdForm.getContainer(), domain, false);

        ButtonBar bbar = new ButtonBar();
        ActionButton saveButton = new ActionButton("setDefaultValues.view", "Save Defaults", DataRegion.MODE_INSERT, ActionButton.Action.POST);
        bbar.add(saveButton);
        if (defaultsDefined)
        {
            ActionButton clearButton = new ActionButton("clearDefaultValues.view", "Clear Defaults", DataRegion.MODE_INSERT, ActionButton.Action.POST);
            bbar.add(clearButton);
        }
        bbar.add(new ActionButton("Cancel", new ActionURL(domainIdForm.getReturnUrl())));
        rgn.addHiddenFormField("domainId", "" + domainIdForm.getDomainId());
        rgn.addHiddenFormField("returnUrl", domainIdForm.getReturnUrl());
        rgn.setButtonBar(bbar);

        List<Container> overridees = DefaultValueService.get().getDefaultValueOverridees(domainIdForm.getContainer(), domain);
        boolean inherited = !overridees.isEmpty();
        StringBuilder headerHtml = new StringBuilder("<span class=\"normal\">");
        if (!defaultsDefined)
        {
            if (inherited)
                headerHtml.append("This table inherits default values from a parent folder.");
            else
                headerHtml.append("No defaults are defined for this table in this folder.");
        }
        else
            headerHtml.append("Defaults are currently defined for this table in this folder.");
        headerHtml.append("</span>");
        if (!domain.getContainer().equals(getViewContext().getContainer()))
        {
            ActionURL url = new ActionURL(SetDefaultValuesAction.class, domain.getContainer());
            url.addParameter("returnUrl", getViewContext().getActionURL().getLocalURIString());
            url.addParameter("domainId", domain.getTypeId());
            headerHtml.append(" [<a href=\"" + url + "\">edit default values for this table in " + PageFlowUtil.filter(domain.getContainer().getPath()) + "</a>]");
        }
        headerHtml.append("<br><br>Default values set here will be inherited by all sub-folders that use this table and do not specify their own defaults.");

        HtmlView headerView = new HtmlView(headerHtml.toString());

        StringBuilder overrideHtml = new StringBuilder();
        if (!overridees.isEmpty())
        {
            overrideHtml.append("<span class=\"normal\">");
            if (!defaultsDefined)
                overrideHtml.append("If saved, these values will override defaults set in the following folder:");
            else
                overrideHtml.append("These values override defaults set in the following folder:");
            overrideHtml.append("</span><br>");
            Container container = overridees.get(overridees.size() - 1);
            appendEditURL(overrideHtml, container, domain, domainIdForm.getReturnUrl());
        }
        List<Container> overriders = DefaultValueService.get().getDefaultValueOverriders(domainIdForm.getContainer(), domain);
        if (!overriders.isEmpty())
        {
            if (!overridees.isEmpty())
                overrideHtml.append("<br>");
            overrideHtml.append("<span class=\"normal\">");
            if (!defaultsDefined)
                overrideHtml.append("If saved, these values will be overridden by defaults the following folder(s):");
            else
                overrideHtml.append("These values are overridden by defaults set in the following folder(s):");
            overrideHtml.append("</span><br>");
            for (Container container : overriders)
                appendEditURL(overrideHtml, container, domain, domainIdForm.getReturnUrl());
        }

        return new VBox(headerView, view, new HtmlView(overrideHtml.toString()));
    }

    private void appendEditURL(StringBuilder builder, Container container, Domain domain, String returnUrl)
    {
        ActionURL editURL = new ActionURL(SetDefaultValuesAction.class, container);
        editURL.addParameter("domainId", domain.getTypeId());
        editURL.addParameter("returnUrl", returnUrl);
        builder.append("<a href=\"").append(editURL.getLocalURIString()).append("\">");
        builder.append(PageFlowUtil.filter(container.getPath()));
        builder.append("</a><br>");
    }

    public boolean handlePost(DomainIdForm domainIdForm, BindException errors) throws Exception
    {
        Domain domain = getDomain(domainIdForm);
        // first, we validate the post:
        boolean failedValidation = false;
        Map<DomainProperty, Object> values = new HashMap<DomainProperty, Object>();
        for (DomainProperty property : domain.getProperties())
        {
            String propName = ColumnInfo.propNameFromName(property.getName());
            String value = domainIdForm.getRequest().getParameter(propName);
            String label = property.getPropertyDescriptor().getNonBlankLabel();
            PropertyType type = property.getPropertyDescriptor().getPropertyType();
            if (value != null && value.length() > 0)
            {
                try
                {
                    Object converted = ConvertUtils.convert(value, type.getJavaType());
                    values.put(property, converted);
                }
                catch (ConversionException e)
                {
                    failedValidation = true;
                    errors.reject(SpringActionController.ERROR_MSG,
                            label + " must be of type " + ColumnInfo.getFriendlyTypeName(type.getJavaType()) + ".");
                }
            }
        }
        if (failedValidation)
            return false;

        try
        {
            DefaultValueService.get().setDefaultValues(domainIdForm.getContainer(), values);
        }
        catch (ExperimentException e)
        {
            errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
            return false;
        }
        return true;
    }

    public NavTree appendNavTrail(NavTree root)
    {
        ActionURL returnUrl = new ActionURL(_returnUrl);
        root.addChild("Edit Type", returnUrl);
        root.addChild("Set Default Values");
        return root;
    }
}