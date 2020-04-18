/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import React from 'react'
import { List, Map, fromJS } from 'immutable'
import { AppURL, imageURL, LoadingSpinner, User, MenuSectionConfig, ProductMenuModel, NavigationBar } from "@labkey/components";
import { getServerContext } from "@labkey/api";

const PRODUCT_KEY = "sampleManager"; // TODO change this to a user select or input

interface State {
    model: ProductMenuModel,
    menuSectionConfigs: List<Map<string, MenuSectionConfig>>
    brandIcon: string
}

export class  NavigationBarPage extends React.Component<any, State> {

    constructor(props: any) {
        super(props);

        const assaysMenuConfig = new MenuSectionConfig({
            emptyText: 'No assays defined',
            iconURL: imageURL('_images', "assay.svg"),
            maxColumns: 2,
            maxItemsPerColumn: 12,
            seeAllURL: AppURL.create('assays').addParam('viewAs', 'grid')
        });
        const samplesMenuConfig = new MenuSectionConfig({
            emptyText: 'No sample types defined',
            iconURL: imageURL('_images', "samples.svg"),
            maxColumns: 1,
            maxItemsPerColumn: 12,
            seeAllURL: AppURL.create('samples').addParam('viewAs', 'grid')
        });
        const workflowMenuConfig = new MenuSectionConfig({
            iconURL: imageURL('_images', "workflow.svg"),
            maxColumns: 1,
            maxItemsPerColumn: 3,
            seeAllURL: AppURL.create('workflow').addParam('viewAs', 'heatmap')
        });
        const userMenuConfig = new MenuSectionConfig({
            iconCls: "fas fa-user-circle "
        });

        this.state = {
            brandIcon: 'http://labkey.wpengine.com/wp-content/uploads/2015/12/cropped-LK-icon.png',
            model: new ProductMenuModel({productId: PRODUCT_KEY}),
            menuSectionConfigs: fromJS([
                {samples: samplesMenuConfig},
                {assays: assaysMenuConfig},
                {workflow: workflowMenuConfig, user: userMenuConfig}
            ])
        };
    }

    componentWillMount() {
        const { model } = this.state;

        model.getMenuSections()
            .then(sections => {
                this.setState(() => ({
                    model: model.setLoadedSections(sections)}))
            });
    }

    onSearch(searchTerm: string) {
        alert('Search term: ' + searchTerm);
    }

    render() {
        const { model, menuSectionConfigs, brandIcon } = this.state;

        if (model.isLoading) {
            return <LoadingSpinner/>
        }

        return (
            <NavigationBar
                brand={<img src={brandIcon}  height="38px" width="38px"/>}
                projectName={getServerContext().container.title}
                menuSectionConfigs={menuSectionConfigs}
                model={model}
                showSearchBox={true}
                onSearch={this.onSearch}
                user={new User(getServerContext().user)}
            />
        )
    }
}

