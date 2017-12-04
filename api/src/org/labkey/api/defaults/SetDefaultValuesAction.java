/*
 * Copyright (c) 2009-2017 LabKey Corporation
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

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.InsertView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewServlet;
import org.springframework.validation.BindException;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
/*
 * User: brittp
 * Date: Jan 27, 2009
 * Time: 4:52:21 PM
 */

@RequiresPermission(AdminPermission.class)
public class SetDefaultValuesAction<FormType extends DomainIdForm> extends DefaultValuesAction<FormType>
{
    private URLHelper _returnUrl;

    public SetDefaultValuesAction()
    {
    }

    public SetDefaultValuesAction(Class formClass)
    {
        super(formClass);
    }

    private class DefaultableDataColumn extends DataColumn implements DefaultableDisplayColumn
    {
        private DomainProperty _property;

        public DefaultableDataColumn(DomainProperty property, ColumnInfo col)
        {
            super(col);
            _property = property;
        }

        public DefaultValueType getDefaultValueType()
        {
            return _property.getDefaultValueTypeEnum();
        }

        public Class getJavaType()
        {
            return _property.getPropertyDescriptor().getPropertyType().getJavaType();
        }

        @Override
        protected boolean renderRequiredIndicators()
        {
            return false;
        }

        @Override
        protected boolean isDisabledInput()
        {
            return _property.getPropertyDescriptor().getPropertyType().getJavaType() == File.class;
        }
    }

    protected class DefaultValueDataRegion extends DataRegion
    {
        public void render(RenderContext ctx, Writer out) throws IOException
        {
            renderFormBegin(ctx, out, MODE_INSERT);
            renderMainErrors(ctx, out);
            out.write("<table class=\"lk-fields-table\">");
            out.write("<tr>" +
                    "<td class=\"lk-form-label lk-form-col-label\"><label>Field</label></td>" +
                    "<td class=\"lk-form-label lk-form-col-label\" style=\"text-align: left;\"><label>Initial/Default Value</label></td>" +
                    "<td class=\"lk-form-label lk-form-col-label\"><label>Default type</label></td>" +
                    "</tr>");
            for (DisplayColumn renderer : getDisplayColumns())
            {
                if (!shouldRender(renderer, ctx) || !(renderer instanceof DefaultableDisplayColumn))
                    continue;
                boolean isFile = ((DefaultableDisplayColumn) renderer).getJavaType() == File.class;
                out.write("<tr>");

                renderer.renderDetailsCaptionCell(ctx, out, "control-label lk-form-row-label");

                if (isFile)
                    out.write("<td></td>"); // No input for file
                else
                    renderer.renderInputCell(ctx, out);

                out.write("<td>");
                if (isFile)
                    out.write("Defaults cannot be set for file fields.");
                else
                {
                    DefaultValueType defaultType = ((DefaultableDisplayColumn) renderer).getDefaultValueType();
                    if (defaultType == null)
                        defaultType = DefaultValueType.FIXED_EDITABLE;
                    out.write(PageFlowUtil.filter(defaultType.getLabel()));
                    out.write(PageFlowUtil.helpPopup("Default Value Type: " + defaultType.getLabel(), defaultType.getHelpText(), true));
                }
                out.write("</td>");

                out.write("</tr>");
            }
            out.write("</table>");
            ButtonBar bbar = getButtonBar(MODE_INSERT);
            bbar.setStyle(ButtonBar.Style.separateButtons);
            bbar.render(ctx, out);
            renderFormEnd(ctx, out);
        }
    }

    protected DataRegion createDataRegion()
    {
        return new DefaultValueDataRegion();
    }

    public HttpView getView(FormType domainIdForm, boolean reshow, BindException errors) throws Exception
    {
        _returnUrl = domainIdForm.getReturnURLHelper();
        Domain domain = getDomain(domainIdForm);
        List<? extends DomainProperty> properties = domain.getProperties();
        if (properties.isEmpty())
        {
            return new HtmlView("No fields are defined for this table.<br><br>" + 
                    PageFlowUtil.button("Cancel").href(domainIdForm.getReturnURLHelper()));
        }


        DataRegion rgn = createDataRegion();
        TableInfo baseTable = OntologyManager.getTinfoObject();
        rgn.setTable(baseTable);
        for (DomainProperty dp : properties)
        {
            ColumnInfo info = dp.getPropertyDescriptor().createColumnInfo(baseTable, "objecturi", getUser(), getContainer());
            rgn.addDisplayColumn(new DefaultableDataColumn(dp, info));
        }
        InsertView view = new InsertView(rgn, errors);

        if (reshow)
            view.setInitialValues(ViewServlet.adaptParameterMap(domainIdForm.getRequest().getParameterMap()));
        else
        {
            Map<DomainProperty, Object> defaults = DefaultValueService.get().getDefaultValues(domainIdForm.getContainer(), domain);
            Map<String, Object> formDefaults = new HashMap<>();
            for (Map.Entry<DomainProperty, Object> entry : defaults.entrySet())
            {
                if (entry.getValue() != null)
                {
                    String stringValue = entry.getValue().toString();
                    decodePropertyValues(formDefaults, ColumnInfo.propNameFromName(entry.getKey().getName()), stringValue);
                }
            }
            view.setInitialValues(formDefaults);
        }

        boolean defaultsDefined = DefaultValueService.get().hasDefaultValues(domainIdForm.getContainer(), domain, false);

        ButtonBar bbar = new ButtonBar();
        bbar.setStyle(ButtonBar.Style.separateButtons);
        ActionURL setDefaultsURL = getViewContext().getActionURL().clone().deleteParameters();
        ActionButton saveButton = new ActionButton(setDefaultsURL, "Save Defaults");
        saveButton.setActionType(ActionButton.Action.POST);
        bbar.add(saveButton);
        if (defaultsDefined)
        {
            ActionURL clearURL = new ActionURL(ClearDefaultValuesAction.class, domainIdForm.getContainer());
            ActionButton clearButton = new ActionButton(clearURL, "Clear Defaults");
            clearButton.setActionType(ActionButton.Action.POST);
            bbar.add(clearButton);
        }
        bbar.add(new ActionButton("Cancel", _returnUrl));
        rgn.addHiddenFormField("domainId", "" + domainIdForm.getDomainId());
        rgn.addHiddenFormField(ActionURL.Param.returnUrl, domainIdForm.getReturnUrl());
        rgn.setButtonBar(bbar);

        addAdditionalFormFields(domainIdForm, rgn);

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
        if (!domain.getContainer().equals(getContainer()) && domain.getContainer().hasPermission(getUser(), AdminPermission.class))
        {
            ActionURL url = buildSetInheritedDefaultsURL(domain, domainIdForm);
            headerHtml.append(PageFlowUtil.textLink("edit default values for this table in " + PageFlowUtil.filter(domain.getContainer().getPath()), url));
        }
        headerHtml.append("<p>Default values set here will be inherited by all sub-folders that use this table and do not specify their own defaults.</p>");

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

    protected ActionURL buildSetInheritedDefaultsURL(Domain domain, FormType domainIdForm)
    {
        // Overrides to this method should call super, and then add any additional url parameters the entity type may need.
        ActionURL url = new ActionURL(this.getClass(), domain.getContainer());
        url.addParameter(ActionURL.Param.returnUrl, getViewContext().getActionURL().getLocalURIString());
        url.addParameter("domainId", domain.getTypeId());
        return url;
    }

    /**
     * Convenience method for subclasses to add additional fields needed, e.g., on reshow.
     * @param domainIdForm
     * @param rgn
     */
    protected void addAdditionalFormFields(FormType domainIdForm, DataRegion rgn)
    {
        return;
    }

    /**
     * Convenience method for subclasses to override if they've done additional processing of values before they were stored.
     * @param formDefaults
     * @param propName
     * @param stringValue
     * @throws IOException
     */
    protected void decodePropertyValues(Map<String, Object> formDefaults, String propName, String stringValue) throws IOException
    {
        formDefaults.put(propName, stringValue);
    }

    private void appendEditURL(StringBuilder builder, Container container, Domain domain, String returnUrl)
    {
        ActionURL editURL = new ActionURL(this.getClass(), container);
        editURL.addParameter("domainId", domain.getTypeId());
        if (StringUtils.isNotEmpty(returnUrl))
            editURL.addParameter(ActionURL.Param.returnUrl, returnUrl);
        builder.append("<a href=\"").append(PageFlowUtil.filter(editURL.getLocalURIString())).append("\">");
        builder.append(PageFlowUtil.filter(container.getPath()));
        builder.append("</a><br>");
    }

    public boolean handlePost(FormType domainIdForm, BindException errors) throws Exception
    {
        Domain domain = getDomain(domainIdForm);
        // first, we validate the post:
        boolean failedValidation = false;
        Map<DomainProperty, Object> values = new HashMap<>();
        for (DomainProperty property : domain.getProperties())
        {
            String propName = ColumnInfo.propNameFromName(property.getName());
            String value = encodePropertyValues(domainIdForm, propName);
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
                    String label = property.getPropertyDescriptor().getNonBlankCaption();
                    errors.reject(SpringActionController.ERROR_MSG,
                            label + " must be of type " + ColumnInfo.getFriendlyTypeName(type.getJavaType()) + ".");
                }
            }
        }
        if (failedValidation)
            return false;

        try
        {
            if (values.size() > 0)
                DefaultValueService.get().setDefaultValues(domainIdForm.getContainer(), values);
        }
        catch (ExperimentException e)
        {
            errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
            return false;
        }
        return true;
    }

    /**
     * Convenience method for subclasses to override to handle additional form parameters before storing them in the property values.
     * @param domainIdForm
     * @param propName
     * @return
     * @throws IOException
     */
    protected String encodePropertyValues(FormType domainIdForm, String propName) throws IOException
    {
        return domainIdForm.getRequest().getParameter(propName);
    }

    public NavTree appendNavTrail(NavTree root)
    {
        setHelpTopic("manageAssayDesign");
        root.addChild("Edit Type", _returnUrl);
        root.addChild("Set Default Values");
        return root;
    }
}
