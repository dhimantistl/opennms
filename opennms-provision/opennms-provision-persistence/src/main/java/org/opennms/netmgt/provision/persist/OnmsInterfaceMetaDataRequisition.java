/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2006-2014 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2014 The OpenNMS Group, Inc.
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
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.provision.persist;

import java.util.Objects;

import org.opennms.netmgt.provision.persist.requisition.RequisitionMetaData;

public class OnmsInterfaceMetaDataRequisition {

    private RequisitionMetaData metaData;

    public OnmsInterfaceMetaDataRequisition(RequisitionMetaData metaData) {
        this.metaData = Objects.requireNonNull(metaData);
    }

    public RequisitionMetaData getMetaData() {
        return metaData;
    }

    public String getContext() {
        return metaData.getContext();
    }

    public String getKey() {
        return metaData.getKey();
    }

    public String getValue() {
        return metaData.getValue();
    }

    public void visit(RequisitionVisitor visitor) {
        visitor.visitInterfaceMetaData(this);
        visitor.completeInterfaceMetaData(this);
    }

}
