/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2017-2017 The OpenNMS Group, Inc.
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
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.telemetry.adapters.netflow;

import java.net.InetAddress;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.opennms.core.utils.InetAddressUtils;
import org.opennms.netmgt.dao.api.InterfaceToNodeCache;
import org.opennms.netmgt.dao.api.NodeDao;
import org.opennms.netmgt.flows.api.FlowRepositoryProvider;
import org.opennms.netmgt.flows.api.NetflowDocument;
import org.opennms.netmgt.flows.api.NodeInfo;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.telemetry.adapters.api.Adapter;
import org.opennms.netmgt.telemetry.adapters.api.TelemetryMessage;
import org.opennms.netmgt.telemetry.adapters.api.TelemetryMessageLog;
import org.opennms.netmgt.telemetry.adapters.netflow.v5.NetflowPacket;
import org.opennms.netmgt.telemetry.config.api.Protocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.support.TransactionOperations;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

public class Netflow5Adapter implements Adapter {

    // Key class, which is used to cache NodeInfo objects
    private static class NodeInfoKey {

        public final String location;

        public final String ipAddress;

        public NodeInfoKey(String location, String ipAddress) {
            this.location = location;
            this.ipAddress = ipAddress;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final NodeInfoKey that = (NodeInfoKey) o;
            boolean equals = Objects.equals(location, that.location)
                    && Objects.equals(ipAddress, that.ipAddress);
            return equals;
        }

        @Override
        public int hashCode() {
            return Objects.hash(location, ipAddress);
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(Netflow5Adapter.class);

    @Autowired
    @Qualifier("flowAdapterMetricRegistry")
    private MetricRegistry metricRegistry;

    @Autowired
    private InterfaceToNodeCache interfaceToNodeCache;

    @Autowired
    private NodeDao nodeDao;

    @Autowired
    private TransactionOperations transactionOperations;

    @Autowired
    private FlowRepositoryProvider provider;

    private final Netflow5Converter converter = new Netflow5Converter();

    // measures the flows/seconds throughput
    private Meter meter;

    // Caches NodeInfo data
    private LoadingCache<NodeInfoKey, Optional<NodeInfo>> nodeInfoCache;

    @PostConstruct
    public void init() {
        meter = metricRegistry.meter("persistence");

        // TODO MVR make this configurable, when it is actually an osgi-module
        nodeInfoCache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterAccess(300, TimeUnit.SECONDS) // 5 Minutes
                .build(new CacheLoader<NodeInfoKey, Optional<NodeInfo>>() {
                    @Override
                    public Optional<NodeInfo> load(NodeInfoKey key) throws Exception {
                        return getNodeInfo(key.location, key.ipAddress);
                    }
                });
    }

    @Override
    public void setProtocol(Protocol protocol) {
        // we do not need the protocol
    }

    @Override
    public void handleMessageLog(TelemetryMessageLog messageLog) {
        LOG.debug("Received {} telemetry messages", messageLog.getMessageList().size());

        for (TelemetryMessage eachMessage : messageLog.getMessageList()) {
            LOG.debug("Parse log message {}", eachMessage);

            try {
                final NetflowPacket flowPacket = parse(eachMessage);
                if (flowPacket != null) {
                    final List<NetflowDocument> flowDocuments = convert(flowPacket);
                    enrich(flowDocuments, messageLog);
                    persist(flowDocuments);
                }
            } catch (Throwable t) {
                LOG.error("An error occurred while handling incoming flow packets: {}", t.getMessage(), t);
                throw new RuntimeException(t);
            }
        }
    }
    
    private NetflowPacket parse(TelemetryMessage message) {
        // Create NetflowPacket which delegates all calls to the byte array
        final NetflowPacket flowPacket = new NetflowPacket(message.getByteArray());

        // Version must match for now. Otherwise we drop the packet
        if (flowPacket.getVersion() != NetflowPacket.VERSION) {
            LOG.warn("Invalid Version. Expected {}, received {}. Dropping flow packet.", NetflowPacket.VERSION, flowPacket.getVersion());
            return null;
        }

        // Empty flows are dropped for now
        if (flowPacket.getCount() == 0) {
            LOG.warn("Received packet has no content. Dropping flow packet.");
            return null;
        }

        // Validates the parsed packeet and drops it when not valid
        if (!flowPacket.isValid()) {
            // TODO MVR an invalid packet is skipped for now, but we may want to persist it anyways
            LOG.warn("Received packet is not valid. Dropping flow packet.");
            return null;
        }

        return flowPacket;
    }

    /**
     * Converts the given flow packet to flows represented by a {@link NetflowDocument}.
     *
     * @param netflowPacket the packet to convert
     * @return The flows of the packet
     */
    private List<NetflowDocument> convert(NetflowPacket netflowPacket) {
        return converter.convert(netflowPacket);
    }

    private void enrich(final List<NetflowDocument> documents, final TelemetryMessageLog messageLog) {
        enrich(documents, messageLog.getLocation(), messageLog.getSourceAddress());
    }

    protected void enrich(final List<NetflowDocument> documents, final String location, final String sourceAddress) {
        if (documents.isEmpty()) {
            LOG.debug("Nothing to enrich.");
            return;
        }

        transactionOperations.execute(callback -> {
            documents.stream().forEach(document -> {
                // Metadata from message
                document.setExporterAddress(sourceAddress);
                document.setLocation(location);

                // Node data
                getNodeInfoFromCache(location, sourceAddress).ifPresent(node -> document.setExporterNodeInfo(node));
                getNodeInfoFromCache(location, document.getIpv4DestAddress()).ifPresent(node -> document.setDestNodeInfo(node));
                getNodeInfoFromCache(location, document.getIpv4SourceAddress()).ifPresent(node -> document.setSourceNodeInfo(node));
            });
            return null;
        });
    }

    private Optional<NodeInfo> getNodeInfoFromCache(String location, String ipAddress) {
        final NodeInfoKey key = new NodeInfoKey(location, ipAddress);
        try {
            final Optional<NodeInfo> value = nodeInfoCache.get(key);
            return value;
        } catch (ExecutionException e) {
            LOG.error("Error while retrieving NodeInfo from NodeInfoCache: {}.", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private Optional<NodeInfo> getNodeInfo(String location, String ipAddress) {
        return getNodeInfo(location, InetAddressUtils.addr(ipAddress));
    }

    private Optional<NodeInfo> getNodeInfo(String location, InetAddress ipAddress) {
        final Optional<Integer> nodeId = interfaceToNodeCache.getFirstNodeId(location, ipAddress);
        if (nodeId.isPresent()) {
            final OnmsNode onmsNode = nodeDao.get(nodeId.get());

            final NodeInfo nodeInfo = new NodeInfo();
            nodeInfo.setForeignSource(onmsNode.getForeignSource());
            nodeInfo.setForeignId(onmsNode.getForeignId());
            nodeInfo.setCategories(onmsNode.getCategories().stream().map(c -> c.getName()).collect(Collectors.toList()));

            return Optional.of(nodeInfo);
        }
        return Optional.empty();
    }

    private void persist(List<NetflowDocument> documents) throws Exception {
        if (documents.isEmpty()) {
            LOG.debug("Nothing to persist");
            return;
        }

        provider.getFlowRepository().save(documents);
        meter.mark(documents.size());
    }
}
