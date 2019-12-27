import React, {PureComponent} from "react";
import {FileAttachmentForm, importData} from "@labkey/components";
import {ActionURL} from "@labkey/api";

import {FontAwesomeIcon} from "@fortawesome/react-fontawesome";
import {faImage} from "@fortawesome/free-solid-svg-icons";

export default class SSOFields extends PureComponent<any, any> {
    render() {
        return(
            <div >
                <div className="">
                    <ImageAndFileAttachmentForm
                        text="Page Header Logo"
                        imageUrl={ActionURL.getBaseURL(true) + this.props.headerLogoUrl}
                        onFileChange={this.props.onFileChange}
                        fileTitle='auth_header_logo'
                    />
                </div>

                <br/>

                <div className="">
                    <ImageAndFileAttachmentForm
                        text="Login Page Logo"
                        imageUrl={ActionURL.getBaseURL(true) + this.props.loginLogoUrl}
                        onFileChange={this.props.onFileChange}
                        fileTitle='auth_login_page_logo'
                    />
                </div>
            </div>
        );
    }
}

class ImageAndFileAttachmentForm extends PureComponent<any, any>{
    constructor(props) {
        super(props);
        this.state = {
            imageUrl: this.props.imageUrl,
        };
    }

    render() {
        return(
            <>
                <div className="fileAttachmentLabel">
                    {this.props.text}
                </div>

                <div className="fileAttachmentImage">
                    {this.state.imageUrl ?
                        <img
                            src={this.state.imageUrl}
                            onError={() => {this.setState({imageUrl: null})}}
                            alt="Sign in"
                        />
                        : <FontAwesomeIcon icon={faImage} color={"#DCDCDC"} size={"6x"}/>
                    }
                </div>

                <div className="fileAttachmentComponent">
                    <FileAttachmentForm
                        key={this.props.text}
                        showLabel={false}
                        allowMultiple={false}
                        allowDirectories={false}
                        acceptedFormats={".jpeg,.png,.gif,.tif"}
                        showAcceptedFormats={false}
                        onFileChange={(attachment) => {console.log('I am firing onFileChange from', this.props.fileTitle); this.props.onFileChange(attachment, this.props.fileTitle)}}
                    />
                </div>
            </>
        );
    }
}