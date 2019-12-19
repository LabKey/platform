import React, {PureComponent} from "react";
import {FileAttachmentForm, importData} from "@labkey/components";
import {ActionURL} from "@labkey/api";

import {FontAwesomeIcon} from "@fortawesome/react-fontawesome";
import {faImage} from "@fortawesome/free-solid-svg-icons";

export default class SSOFields extends PureComponent<any, any> {
    constructor(props) {
        super(props);
        this.state = {
            headerLogoUrl: ActionURL.getBaseURL(true) + this.props.headerLogoUrl,
            loginLogoUrl: ActionURL.getBaseURL(true) + this.props.loginLogoUrl,
        };
    }


    setDefaultImage = (logoType) => {
        this.setState(() => ({ [logoType]:null })
            // , () => console.log("state ",this.state)
        );

    };

    render() {
        const baseUrl = ActionURL.getBaseURL(true);


        return(
            <div >
                <div className="">

                    <div className="fileAttachmentLabel">
                        Page Header Logo
                    </div>

                    <div className="fileAttachmentImage">
                        {this.state.headerLogoUrl ?
                            <img
                                src={this.state.headerLogoUrl}
                                onError={(e) => {this.setDefaultImage("headerLogoUrl")}}
                                alt="Sign in"
                            />
                            : <FontAwesomeIcon icon={faImage} color={"#DCDCDC"} size={"6x"} />
                        }
                    </div>


                    <div className="fileAttachmentComponent">
                        <FileAttachmentForm
                            showLabel={false}
                            allowMultiple={false}
                            allowDirectories={false}
                            acceptedFormats={".jpeg,.png,.gif,.tif"}
                            showAcceptedFormats={false}
                            onFileChange={(attachment) => this.props.onFileChange(attachment, "auth_header_logo")}
                        />
                    </div>
                </div>

                <br/>
                <div className="">
                    <div className="fileAttachmentLabel">
                        Login Page Logo
                    </div>

                    <div className="fileAttachmentImage">
                        {this.state.headerLogoUrl ?
                            <img
                                src={this.state.loginLogoUrl}
                                onError={(e) => {this.setDefaultImage("loginLogoUrl")}}
                                alt="Sign in"
                            />
                            : <FontAwesomeIcon icon={faImage} color={"#DCDCDC"} size={"6x"}/>
                        }
                    </div>

                    <div className="fileAttachmentComponent">
                        <FileAttachmentForm
                            showLabel={false}
                            allowMultiple={false}
                            allowDirectories={false}
                            acceptedFormats={".jpeg,.png,.gif,.tif"}
                            showAcceptedFormats={false}
                            onFileChange={(attachment) => this.props.onFileChange(attachment, "auth_login_page_logo")}
                        />
                    </div>
                </div>
            </div>
        );
    }
}