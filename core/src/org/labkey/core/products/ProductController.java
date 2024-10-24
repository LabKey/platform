/*
 * Copyright (c) 2019 LabKey Corporation
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
package org.labkey.core.products;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.labkey.api.action.Marshal;
import org.labkey.api.action.Marshaller;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.products.ProductRegistry;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.logging.LogHelper;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Marshal(Marshaller.Jackson)
public class ProductController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(ProductController.class);
    private static final Logger _log = LogHelper.getLogger(ProductController.class, "Application product menu logging");

    public ProductController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(ReadPermission.class)
    public static class UserMenuSectionAction extends ReadOnlyApiAction<UserMenuSectionForm>
    {
        @Override
        public void validateForm(UserMenuSectionForm form, Errors errors)
        {
            if (form.getProductId() == null)
                errors.reject(ERROR_REQUIRED, "productId is required");
        }

        @Override
        public Object execute(UserMenuSectionForm form, BindException errors) throws Exception
        {
            return ProductRegistry.get().getUserMenuSection(getViewContext(), form.getProductId());
        }
    }

    public static class UserMenuSectionForm
    {
        private String _productId;

        public String getProductId()
        {
            return _productId;
        }

        public void setProductId(String productId)
        {
            _productId = productId;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public static class MenuSectionsAction extends ReadOnlyApiAction<MenuItemsForm>
    {
        private List<String> _productIds;

        @Override
        public void validateForm(MenuItemsForm menuItemsForm, Errors errors)
        {
            if (menuItemsForm.getCurrentProductId() == null)
                errors.reject(ERROR_REQUIRED, "currentProductId is required");

            ProductRegistry registry = ProductRegistry.get();
            if (!StringUtils.isEmpty(menuItemsForm.getProductIds()))
            {
                _productIds = Arrays.asList(menuItemsForm.getProductIds().split(","));
                String missingProducts = _productIds.stream().filter(productId -> !registry.containsProductId(productId)).collect(Collectors.joining(", "));
                if (!missingProducts.isEmpty())
                    _log.warn("No such products: " + missingProducts);
            }
        }

        @Override
        public Object execute(MenuItemsForm menuItemsForm, BindException errors) throws Exception
        {
            return ProductRegistry.get().getProductMenuSections(getViewContext(), _productIds);
        }
    }

    public static class MenuItemsForm
    {
        private String _productIds; // comma-separated list of productIds
        private String _currentProductId;

        public String getProductIds()
        {
            return _productIds;
        }

        public void setProductIds(String productIds)
        {
            _productIds = productIds;
        }

        public String getCurrentProductId()
        {
            return _currentProductId;
        }

        public void setCurrentProductId(String currentProductId)
        {
            _currentProductId = currentProductId;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public static class GetRegisteredProductsAction extends ReadOnlyApiAction<Object>
    {
        @Override
        public Object execute(Object form, BindException errors) throws Exception
        {
            return ProductRegistry.get().getRegisteredProducts();
        }
    }
}
