/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2022 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2022 The OpenNMS Group, Inc.
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
package org.opennms.netmgt.enlinkd.snmp;

import org.opennms.netmgt.enlinkd.model.OspfArea;
import org.opennms.netmgt.snmp.RowCallback;
import org.opennms.netmgt.snmp.SnmpInstId;
import org.opennms.netmgt.snmp.SnmpObjId;
import org.opennms.netmgt.snmp.SnmpRowResult;
import org.opennms.netmgt.snmp.TableTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;


public class OspfAreaTableTracker extends TableTracker {
    private final static Logger LOG = LoggerFactory.getLogger(OspfAreaTableTracker.class);

    public static final SnmpObjId OSPF_AREA_ENTRY = SnmpObjId.get(".1.3.6.1.2.1.14.2.1");

    public final static SnmpObjId OSPF_AREA_ID = SnmpObjId.get(".1.3.6.1.2.1.14.2.1.1");
    public final static SnmpObjId OSPF_AUTH_TYPE = SnmpObjId.get(".1.3.6.1.2.1.14.2.1.2");
    public final static SnmpObjId OSPF_IMPORT_AS_EXTERN = SnmpObjId.get(".1.3.6.1.2.1.14.2.1.3");
    public final static SnmpObjId OSPF_SPF_RUNS       = SnmpObjId.get(".1.3.6.1.2.1.14.2.1.4");
    public final static SnmpObjId OSPF_AREA_BDR_RTR_COUNT = SnmpObjId.get(".1.3.6.1.2.1.14.2.1.5");
    public final static SnmpObjId OSPF_AS_BDR_RTR_COUNT = SnmpObjId.get(".1.3.6.1.2.1.14.2.1.6");
    public final static SnmpObjId OSPF_AREA_LSA_COUNT = SnmpObjId.get(".1.3.6.1.2.1.14.2.1.7");
    //    public final static SnmpObjId OSPF_AREA_LSA_CKSUM_SUM                       = SnmpObjId.get(".1.3.6.1.2.1.14.2.1.8");
    //    public final static SnmpObjId OSPF_AREA_SUMMARY                             = SnmpObjId.get(".1.3.6.1.2.1.14.2.1.9");
    //    public final static SnmpObjId OSPF_AREA_STATUS                              = SnmpObjId.get(".1.3.6.1.2.1.14.2.1.10");
    //    public final static SnmpObjId OSPF_AREA_NSSA_TRANSLATOR_ROLE                = SnmpObjId.get(".1.3.6.1.2.1.14.2.1.11");
    //    public final static SnmpObjId OSPF_AREA_NSSA_TRANSLATOR_STATE               = SnmpObjId.get(".1.3.6.1.2.1.14.2.1.12");
    //    public final static SnmpObjId OSPF_AREA_NSSA_TRANSLATOR_STABILITY_INTERVAL  = SnmpObjId.get(".1.3.6.1.2.1.14.2.1.13");
    //    public final static SnmpObjId OSPF_AREA_NSSA_TRANSLATOR_EVENTS              = SnmpObjId.get(".1.3.6.1.2.1.14.2.1.14");

    public static final SnmpObjId[] s_ospfAreatable_elemList = new SnmpObjId[]{
            /**
             * A 32-bit integer uniquely identifying an area.
             *           Area ID 0.0.0.0 is used for the OSPF backbone.
            */
            OSPF_AREA_ID,
            /**
             * The authentication type specified for an area.
            */
            OSPF_AUTH_TYPE,
            /**
             * Indicates if an area is a stub area, NSSA, or standard
            area.  Type-5 AS-external LSAs and type-11 Opaque LSAs are
            not imported into stub areas or NSSAs.  NSSAs import
            AS-external data as type-7 LSAs
            */
            OSPF_IMPORT_AS_EXTERN,
            /**
             * The total number of Area Border Routers reachable
             * within this area.  This is initially zero and is
             * calculated in each Shortest Path First (SPF) pass.
            */
            OSPF_AREA_BDR_RTR_COUNT,
            /**
             * The total number of Autonomous System Border
             * Routers reachable within this area.  This is
             * initially zero and is calculated in each SPF
             * pass.
            */
            OSPF_AS_BDR_RTR_COUNT,
            /** The total number of link state advertisements
             * in this area's link state database, excluding
             * AS-external LSAs.
            */
            OSPF_AREA_LSA_COUNT
    };

    public static class OspfAreaRow extends SnmpRowResult {

        public OspfAreaRow(int columnCount, SnmpInstId instance) {
            super(columnCount, instance);
        }

        public InetAddress getOspfAreaId() {
            return getValue(OSPF_AREA_ID).toInetAddress();
        }

        public Integer getOspfAuthType() {
            return getValue(OSPF_AUTH_TYPE).toInt();
        }

        public Integer getOspfImportAsExtern() {
            return getValue(OSPF_IMPORT_AS_EXTERN).isNull() ? null : getValue(OSPF_IMPORT_AS_EXTERN).toInt();
        }

        public Integer getOspfAreaBdrRtrCount() {
            return getValue(OSPF_AREA_BDR_RTR_COUNT).toInt();
        }

        public Integer getOspfAsBdrRtrCount() {
            return getValue(OSPF_AS_BDR_RTR_COUNT).toInt();
        }

        public Integer getOspfAreaLsaCount() {
            return getValue(OSPF_AREA_LSA_COUNT).toInt();
        }

        public OspfArea getOspfArea() {
            final OspfArea area = new OspfArea();
            area.setOspfAreaId(getOspfAreaId());
            area.setOspfAuthType(getOspfAuthType());
            area.setOspfImportAsExtern(getOspfImportAsExtern());
            area.setOspfAreaBdrRtrCount(getOspfAreaBdrRtrCount());
            area.setOspfAsBdrRtrCount(getOspfAsBdrRtrCount());
            area.setOspfAreaLsaCount(getOspfAreaLsaCount());

            return area;
        }
    }

    public OspfAreaTableTracker() {
        super(s_ospfAreatable_elemList);
    }

    public OspfAreaTableTracker(RowCallback rowProcessor) {
        super(rowProcessor, s_ospfAreatable_elemList);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SnmpRowResult createRowResult(final int columnCount, final SnmpInstId instance) {
        return new OspfAreaTableTracker.OspfAreaRow(columnCount, instance);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void rowCompleted(final SnmpRowResult row) {
        processOspfAreaRow((OspfAreaTableTracker.OspfAreaRow) row);
    }

    /**
     * <p>processOspfIfRow</p>
     *
     * @param row a {@link org.opennms.netmgt.enlinkd.snmp.OspfAreaTableTracker.OspfAreaRow} object.
     */
    public void processOspfAreaRow(final OspfAreaTableTracker.OspfAreaRow row) {
    }
}
