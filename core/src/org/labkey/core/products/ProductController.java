package org.labkey.core.products;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.action.Marshal;
import org.labkey.api.action.Marshaller;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.products.ProductRegistry;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;

@Marshal(Marshaller.Jackson)
public class ProductController extends SpringActionController
{

    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(ProductController.class);
    private static final Logger _log = Logger.getLogger(ProductController.class);

    public ProductController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(ReadPermission.class)
    public static class MenuItemsAction extends ReadOnlyApiAction<MenuItemsForm>
    {
        @Override
        public void validateForm(MenuItemsForm menuItemsForm, Errors errors)
        {
            if (StringUtils.isEmpty(menuItemsForm.getProductId()))
                errors.reject(ERROR_REQUIRED, "'productId' is required");
            else if (!ProductRegistry.get().containsProductId(menuItemsForm.getProductId()))
                errors.reject(ERROR_MSG, "No such product: '" + menuItemsForm.getProductId() + "'");
            if (menuItemsForm.getItemLimit() != null && menuItemsForm.getItemLimit() < 0)
                errors.reject(ERROR_MSG, "'itemLimit' must be >= 0");
        }

        @Override
        public Object execute(MenuItemsForm menuItemsForm, BindException errors) throws Exception
        {
            return ProductRegistry.get().getMenuSections(getViewContext(), menuItemsForm.getProductId(), menuItemsForm.getItemLimit());
        }
    }

    public static class MenuItemsForm
    {
        private String _productId;
        private Integer _itemLimit;

        public String getProductId()
        {
            return _productId;
        }

        public void setProductId(String productId)
        {
            _productId = productId;
        }

        public Integer getItemLimit()
        {
            return _itemLimit;
        }

        public void setItemLimit(Integer itemLimit)
        {
            _itemLimit = itemLimit;
        }
    }
}
