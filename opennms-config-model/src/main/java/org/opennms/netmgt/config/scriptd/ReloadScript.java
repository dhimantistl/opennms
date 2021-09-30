/*******************************************************************************
 * This file is part of OpenNMS(R).
 * 
 * Copyright (C) 2017 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2017 The OpenNMS Group, Inc.
 * 
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 * 
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 * 
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *     http://www.gnu.org/licenses/
 * 
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.config.scriptd;


import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlValue;

import org.opennms.core.xml.ValidateUsing;
import org.opennms.netmgt.config.utils.ConfigUtils;

@XmlRootElement(name = "reload-script")
@XmlAccessorType(XmlAccessType.FIELD)
@ValidateUsing("scriptd-configuration.xsd")
public class ReloadScript implements Serializable {
    private static final long serialVersionUID = 2L;

    @XmlValue
    private String m_content;

    @XmlAttribute(name = "language", required = true)
    private String m_language;

    public Optional<String> getContent() {
        return Optional.ofNullable(m_content);
    }

    public void setContent(final String content) {
        m_content = ConfigUtils.normalizeString(content);
    }

    public String getLanguage() {
        return m_language;
    }

    public void setLanguage(final String language) {
        m_language = ConfigUtils.assertNotEmpty(language, "language");
    }

    @Override
    public int hashCode() {
        return Objects.hash(m_content, m_language);
    }

    @Override
    public boolean equals(final Object obj) {
        if ( this == obj ) {
            return true;
        }

        if (obj instanceof ReloadScript) {
            final ReloadScript that = (ReloadScript)obj;
            return Objects.equals(this.m_content, that.m_content)
                    && Objects.equals(this.m_language, that.m_language);
        }
        return false;
    }

}
