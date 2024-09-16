import React, { FC, memo, useCallback, useState } from 'react';
import { FileAttachmentForm } from '@labkey/components';
import classNames from 'classnames';

const NoImageSelectedDisplay: FC = memo(() => (
    <div className="sso-fields__null-image">
        <span className="fa fa-5x fa-image" style={{ color: '#DCDCDC' }} />
        <div className="sso-fields__null-image__text">None selected</div>
    </div>
));

interface ImgFileAttachFormProps {
    canEdit: boolean;
    fileTitle: string;
    handleDeleteLogo: (name: string) => void;
    imageUrl: string;
    index: number;
    onFileChange: (attachment, name: string) => void;
    text: string;
}

export const ImageAndFileAttachmentForm: FC<ImgFileAttachFormProps> = memo(props => {
    const { canEdit, fileTitle, handleDeleteLogo, index, onFileChange, text } = props;
    const [imageUrl, setImageUrl] = useState<string>(props.imageUrl);
    const imageClassName = classNames('sso-fields__image', {
        'sso-fields__image__header-logo': fileTitle === 'auth_header_logo',
        'sso-fields__image__page-logo': fileTitle !== 'auth_header_logo',
    });
    const onError = useCallback(() => setImageUrl(null), []);
    const onDelete = useCallback(() => {
        setImageUrl(null);
        handleDeleteLogo(fileTitle);
    }, [fileTitle, handleDeleteLogo]);
    const onChange = useCallback(attachment => onFileChange(attachment, fileTitle), [fileTitle, onFileChange]);
    const img = <img className={imageClassName} src={imageUrl} onError={onError} alt="Sign in" />;

    return (
        <>
            <div className="sso-fields__label">{text}</div>
            <div className="sso-fields__inputs">
                {canEdit ? (
                    <>
                        <div className="sso-fields__image-holder">
                            {imageUrl ? (
                                <div className="sso-fields__img_container">
                                    {img}
                                    <div className="sso-fields__delete-img-icon" onClick={onDelete}>
                                        <span className="fa fa-times-circle sso-fields__delete-img clickable" />
                                    </div>
                                </div>
                            ) : (
                                <NoImageSelectedDisplay />
                            )}
                        </div>

                        <div className="sso-fields__file-attachment" id={fileTitle}>
                            <FileAttachmentForm
                                index={index}
                                showLabel={false}
                                allowMultiple={false}
                                allowDirectories={false}
                                acceptedFormats=".jpg,.jpeg,.png,.gif,.tif"
                                showAcceptedFormats={false}
                                onFileChange={onChange}
                            />
                        </div>
                    </>
                ) : (
                    <div className="sso-fields__image-holder--view-only">
                        {imageUrl ? img : <NoImageSelectedDisplay />}
                    </div>
                )}
            </div>
        </>
    );
});

interface Props {
    canEdit: boolean;
    handleDeleteLogo: (name: string) => void;
    headerLogoUrl: string;
    loginLogoUrl: string;
    onFileChange: (attachment, name: string) => void;
}

export const SSOFields: FC<Props> = memo(({ canEdit, handleDeleteLogo, headerLogoUrl, loginLogoUrl, onFileChange }) => (
    <div className="sso-fields-container">
        <ImageAndFileAttachmentForm
            text="Login Page Logo"
            imageUrl={loginLogoUrl}
            onFileChange={onFileChange}
            handleDeleteLogo={handleDeleteLogo}
            fileTitle="auth_login_page_logo"
            canEdit={canEdit}
            index={1}
        />

        <ImageAndFileAttachmentForm
            text="Page Header Logo"
            imageUrl={headerLogoUrl}
            onFileChange={onFileChange}
            handleDeleteLogo={handleDeleteLogo}
            fileTitle="auth_header_logo"
            canEdit={canEdit}
            index={2}
        />
    </div>
));
