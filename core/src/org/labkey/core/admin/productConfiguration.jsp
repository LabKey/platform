<%
/*
 * Copyright (c) 2022 LabKey Corporation
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
%>
<%@ page import="org.labkey.api.settings.AdminConsole" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("internal/jQuery");
    }
%>
<style>
    .description-text {
        font-style: italic;
    }

    .toggle-label-text {
        font-size: 1.1em;
        padding-top: 0.1em;
        padding-bottom: 0.1em;
    }
</style>

<% if (AdminConsole.getProductGroups().isEmpty()) { %>
        <div class="description-text">
            No products requiring configuration have been registered for this deployment.
        </div>
<% } else { %>

<div class="description-text">
    The set of features associated with each product is dependent on the modules included in the current deployment.
    If you don't see features you might expect associated with certain products,
    check the <a href="<%=h(AdminController.getShowAdminURL() + "#modules")%>">Module Information</a> page for more insight.
</div>

<% for (AdminConsole.ProductGroup productGroup : AdminConsole.getProductGroups()) { %>
<div class="list-group">
    <h4><%=h(productGroup.getName())%></h4>
    <% for (AdminConsole.Product product : productGroup.getProducts() ) { %>
    <div class="product-group-item">
        <label>
            <input type="radio" id="<%=h(product.getKey())%>" name="<%=h(productGroup.getKey())%>" value="<%=h(product.getKey())%>" <%=checked(product.isEnabled())%>>
            <span class="toggle-label-text"><%=h(product.getName())%> - Includes the following features: <%=h(StringUtils.join(product.getFeatureFlags(), ", "))%></span>
        </label>
    </div>
    <% } %>
<% } %>

</div>
<script type="application/javascript" nonce="<%=getScriptNonce()%>">
    (function () {
        let inputList = document.querySelectorAll('div.product-group-item input');
        inputList.forEach(function (input) {
            input.addEventListener('change', function (e) {
                let productKey = input.id;
                LABKEY.Ajax.request({
                    url: LABKEY.ActionURL.buildURL('admin', 'productFeature.api'),
                    method: 'POST',
                    params: { productKey: productKey },
                    // success: LABKEY.Utils.getCallbackWrapper(function(json) {
                    //     console.log('Product set to ' + json.productKey + '.');
                    // }),
                    failure: LABKEY.Utils.getCallbackWrapper(null, null, true)
                });
            });
        });
    })();
</script>
<% } %>
