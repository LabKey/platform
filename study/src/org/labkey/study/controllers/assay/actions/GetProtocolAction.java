package org.labkey.study.controllers.assay.actions;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.labkey.api.action.ApiUsageException;
import org.labkey.api.action.Marshal;
import org.labkey.api.action.Marshaller;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.gwt.client.assay.model.GWTPropertyDescriptorMixin;
import org.labkey.api.gwt.client.assay.model.GWTProtocol;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.JsonUtil;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.study.assay.AssayServiceImpl;
import org.springframework.validation.BindException;

@Marshal(Marshaller.Jackson)
@RequiresPermission(ReadPermission.class)
public class GetProtocolAction extends ReadOnlyApiAction<GWTProtocol>
{
    @Override
    protected ObjectMapper createObjectMapper()
    {
        ObjectMapper mapper = JsonUtil.DEFAULT_MAPPER.copy();
        configureObjectMapper(mapper);
        return mapper;
    }

    @Override
    public Object execute(GWTProtocol protocol, BindException errors) throws Exception
    {
        if (protocol.getProtocolId() != null)
        {
            // get existing protocol
            ExpProtocol expProtocol = ExperimentService.get().getExpProtocol(protocol.getProtocolId());
            if (expProtocol == null)
            {
                throw new NotFoundException("Could not locate Experiment Protocol for id: " + protocol.getProtocolId().toString());
            }
            else if (expProtocol.getContainer().hasPermission(getUser(), ReadPermission.class))
            {
                AssayServiceImpl svc = new AssayServiceImpl(getViewContext());
                GWTProtocol ret = svc.getAssayDefinition(protocol.getProtocolId(), false);
                if (ret == null)
                {
                    throw new NotFoundException("Could not locate Assay Definition for id: " + protocol.getProtocolId().toString());
                }
                return success("Assay protocol " + protocol.getName() + "'", ret);
            }
            else
            {
                throw new UnauthorizedException();
            }
        }
        else if (protocol.getProviderName() != null)
        {
            // get the assay template
            AssayServiceImpl svc = new AssayServiceImpl(getViewContext());
            GWTProtocol ret = svc.getAssayTemplate(protocol.getProviderName());
            return success("Generated assay template for provider '" + protocol.getProviderName() + "'", ret);
        }
        else
        {
            throw new ApiUsageException("Assay protocolId or providerName required");
        }
    }

    static void configureObjectMapper(ObjectMapper om) {
        om.addMixIn(GWTPropertyDescriptor.class, GWTPropertyDescriptorMixin.class);
    }
}
