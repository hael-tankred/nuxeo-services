package org.nuxeo.ecm.platform.ui.web.auth.service;

import java.io.Serializable;

import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.runtime.api.Framework;

@XObject("loginProvider")
public class LoginProviderLink implements Serializable {

    private static final long serialVersionUID = 1L;

    public LoginProviderLink() {

    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof LoginProviderLink && name != null) {
            return name.equals(((LoginProviderLink) obj).getName());
        }
        return super.equals(obj);
    }

    @XNode("@name")
    protected String name;

    @XNode("@remove")
    protected boolean remove = false;

    protected String iconPath;

    protected String link;

    @XNode("description")
    protected String description;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIconPath() {
        return iconPath;
    }

    @XNode("iconPath")
    public void setIconPath(String iconPath) {
        this.iconPath = Framework.expandVars(iconPath);
    }

    public String getLink() {
        return link;
    }

    @XNode("link")
    public void setLink(String link) {
        this.link = Framework.expandVars(link);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void merge(LoginProviderLink newLink) {
        if (newLink.link != null) {
            link = newLink.link;
        }
        if (newLink.description != null) {
            description = newLink.description;
        }
        if (newLink.iconPath != null) {
            iconPath = newLink.iconPath;
        }
    }
}
