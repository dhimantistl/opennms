/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2010-2022 The OpenNMS Group, Inc.
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

package org.opennms.netmgt.timeseries.sampleread;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.opennms.integration.api.v1.timeseries.Aggregation;
import org.opennms.integration.api.v1.timeseries.IntrinsicTagNames;
import org.opennms.integration.api.v1.timeseries.StorageException;
import org.opennms.integration.api.v1.timeseries.TimeSeriesFetchRequest;
import org.opennms.integration.api.v1.timeseries.TimeSeriesStorage;
import org.opennms.integration.api.v1.timeseries.immutables.ImmutableDataPoint;
import org.opennms.integration.api.v1.timeseries.immutables.ImmutableMetric;
import org.opennms.integration.api.v1.timeseries.immutables.ImmutableTimeSeriesData;
import org.opennms.integration.api.v1.timeseries.immutables.ImmutableTimeSeriesFetchRequest;
import org.opennms.netmgt.dao.api.ResourceDao;
import org.opennms.netmgt.measurements.api.FetchResults;
import org.opennms.netmgt.measurements.model.Source;
import org.opennms.netmgt.model.OnmsAttribute;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.model.OnmsResource;
import org.opennms.netmgt.model.OnmsResourceType;
import org.opennms.netmgt.model.ResourceId;
import org.opennms.netmgt.model.ResourcePath;
import org.opennms.netmgt.model.RrdGraphAttribute;
import org.opennms.netmgt.timeseries.TimeseriesStorageManagerImpl;
import org.opennms.newts.api.Measurement;
import org.opennms.newts.api.Resource;
import org.opennms.newts.api.Results.Row;
import org.opennms.newts.api.Timestamp;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class TimeseriesFetchStrategyTest {

    private final static long START_TIME = 1431047069000L - (300 * 1000);
    private final static long END_TIME = 1431047069000L -1;
    private final static long STEP = (300 * 1000);

    private ResourceDao resourceDao;
    private TimeseriesStorageManagerImpl storageManager;
    private TimeSeriesStorage timeSeriesStorage;

    private TimeseriesFetchStrategy fetchStrategy;

    private Map<ResourceId, OnmsResource> resources = Maps.newHashMap();


    @Before
    public void setUp() {
        resourceDao = mock(ResourceDao.class);
        this.timeSeriesStorage = Mockito.mock(TimeSeriesStorage.class);
        when(timeSeriesStorage.supportsAggregation(Aggregation.AVERAGE)).thenReturn(true);
        storageManager = Mockito.mock(TimeseriesStorageManagerImpl.class);
        when(storageManager.get()).thenReturn(this.timeSeriesStorage);
        storageManager.onBind(this.timeSeriesStorage, new HashMap<String, String>());

        fetchStrategy = new TimeseriesFetchStrategy();
        fetchStrategy.setResourceDao(resourceDao);
        fetchStrategy.setTimeseriesStorageManager(storageManager);
        fetchStrategy.setMetricRegistry(new MetricRegistry());
    }

    @After
    public void tearDown() {
        verifyNoMoreInteractions(resourceDao);
    }

    @Test
    public void canRetrieveAttributeWhenFallbackAttributeIsSet() throws Exception {
        createMockResource("icmplocalhost", "icmp", "127.0.0.1");
        replay();

        Source sourceToBeFetched = new Source();
        sourceToBeFetched.setResourceId("nodeSource[NODES:1505998205].responseTime[127.0.0.1]");
        sourceToBeFetched.setAttribute("icmp");
        sourceToBeFetched.setFallbackAttribute("willNotBeFound");
        sourceToBeFetched.setAggregation("AVERAGE");
        sourceToBeFetched.setLabel("icmp");

        FetchResults fetchResults = fetchStrategy.fetch(START_TIME, END_TIME, STEP, 0, null, null, Lists.newArrayList(sourceToBeFetched), false);
        assertEquals(1, fetchResults.getColumns().keySet().size());
        assertTrue(fetchResults.getColumns().containsKey("icmp"));
        assertEquals(1, fetchResults.getTimestamps().length);

        verify(resourceDao, atLeastOnce()).getResourceById(any(ResourceId.class));
    }

    @Test
    public void canRetrieveFallbackAttributeWhenAttributeNotFound() throws Exception {
        createMockResource("icmplocalhost", "icmp", "127.0.0.1");
        replay();

        Source sourceToBeFetched = new Source();
        sourceToBeFetched.setResourceId("nodeSource[NODES:1505998205].responseTime[127.0.0.1]");
        sourceToBeFetched.setAttribute("willNotBeFound");
        sourceToBeFetched.setFallbackAttribute("icmp");
        sourceToBeFetched.setAggregation("AVERAGE");
        sourceToBeFetched.setLabel("icmp");

        FetchResults fetchResults = fetchStrategy.fetch(START_TIME, END_TIME, STEP, 0, null, null, Lists.newArrayList(sourceToBeFetched), false);
        assertEquals(1, fetchResults.getColumns().keySet().size());
        assertTrue(fetchResults.getColumns().containsKey("icmp"));
        assertEquals(1, fetchResults.getTimestamps().length);

        verify(resourceDao, atLeastOnce()).getResourceById(any(ResourceId.class));
    }

    @Test
    public void cannotRetrieveUnknownAttributeAndUnknownFallbackAttribute() throws StorageException {
        createMockResource("icmplocalhost", "shouldNotBeFound", "127.0.0.1", false);
        replay();

        Source sourceToBeFetched = new Source();
        sourceToBeFetched.setResourceId("nodeSource[NODES:1505998205].responseTime[127.0.0.1]");
        sourceToBeFetched.setAttribute("willNotBeFound");
        sourceToBeFetched.setFallbackAttribute("willNotBeFoundToo");
        sourceToBeFetched.setAggregation("AVERAGE");
        sourceToBeFetched.setLabel("icmp");

        FetchResults fetchResults = fetchStrategy.fetch(START_TIME, END_TIME, STEP, 0, null, null, Lists.newArrayList(sourceToBeFetched), false);
        assertNull(fetchResults);

        verify(resourceDao, atLeastOnce()).getResourceById(any(ResourceId.class));
    }

    @Test
    public void testFetch() throws Exception {
        List<Source> sources = Lists.newArrayList(
            createMockResource("icmplocalhost", "icmp", "127.0.0.1"),
            createMockResource("snmplocalhost", "snmp", "127.0.0.1"),
            createMockResource("snmp192", "snmp", "192.168.0.1")
        );
        replay();

        FetchResults fetchResults = fetchStrategy.fetch(START_TIME, END_TIME, STEP, 0, null, null, sources, false);
        assertEquals(3, fetchResults.getColumns().keySet().size());
        assertTrue(fetchResults.getColumns().containsKey("icmplocalhost"));
        assertTrue(fetchResults.getColumns().containsKey("snmplocalhost"));
        assertTrue(fetchResults.getColumns().containsKey("snmp192"));
        assertEquals(1, fetchResults.getTimestamps().length);

        verify(resourceDao, atLeastOnce()).getResourceById(any(ResourceId.class));
    }

    @Test
    public void testFetchWithNoResults() throws Exception {
        List<Source> sources = Lists.newArrayList();
        replay();

        FetchResults fetchResults = fetchStrategy.fetch(START_TIME, END_TIME, STEP, 0, null, null, sources, false);
        assertEquals(0, fetchResults.getColumns().keySet().size());
        assertEquals(0, fetchResults.getTimestamps().length);
    }

    @Test
    public void testFetchWithDuplicateResources() throws Exception {
        List<Source> sources = Lists.newArrayList(
            createMockResource("icmp", "icmp", "127.0.0.1"),
            createMockResource("icmp", "icmp", "192.168.0.1")
        );
        replay();

        FetchResults fetchResults = fetchStrategy.fetch(START_TIME, END_TIME, STEP, 0, null, null, sources, false);
        // It's not possible to fetch multiple resources with the same label, we should only get 1 ICMP result
        assertEquals(1, fetchResults.getColumns().keySet().size());

        verify(resourceDao, atLeastOnce()).getResourceById(any(ResourceId.class));
    }

    @Test
    public void canRetrieveValuesByDatasource() throws StorageException {
        List<Source> sources = Collections.singletonList(
                createMockResource("ping1Micro", "strafeping",  "ping1", "127.0.0.1", true));
        replay();

        FetchResults fetchResults = fetchStrategy.fetch(START_TIME, END_TIME, STEP, 0, null, null, sources, false);
        assertEquals(1, fetchResults.getColumns().keySet().size());
        assertTrue(fetchResults.getColumns().containsKey("ping1Micro"));

        verify(resourceDao, atLeastOnce()).getResourceById(any(ResourceId.class));
    }

    public Source createMockResource(final String label, final String attr, final String node) throws StorageException {
        return createMockResource(label, attr, node, true);
    }

    public Source createMockResource(final String label, final String attr, final String node, boolean expect) throws StorageException {
        return createMockResource(label, attr, null, node, expect);
    }

    public Source createMockResource(final String label, final String attr, final String ds, final String node, boolean expect) throws StorageException {
        OnmsResourceType nodeType = mock(OnmsResourceType.class);
        when(nodeType.getName()).thenReturn("nodeSource");
        when(nodeType.getLabel()).thenReturn("nodeSourceTypeLabel");

        OnmsResourceType type = mock(OnmsResourceType.class);
        when(type.getName()).thenReturn("newtsTypeName");
        when(type.getLabel()).thenReturn("newtsTypeLabel");

        final int nodeId = node.hashCode();
        final String newtsResourceId = "response:" + node + ":" + attr;
        final ResourceId parentId = ResourceId.get("nodeSource", "NODES:" + nodeId);
        final ResourceId resourceId = parentId.resolve("responseTime", node);
        OnmsResource parent = resources.get(parentId);
        if (parent == null) {
            parent = new OnmsResource("NODES:" + nodeId, ""+nodeId, nodeType, Sets.newHashSet(), ResourcePath.get("foo"));
            final OnmsNode entity = new OnmsNode();
            entity.setId(nodeId);
            entity.setForeignSource("NODES");
            entity.setForeignId(""+nodeId);
            entity.setLabel(""+nodeId);
            parent.setEntity(entity);
            resources.put(parentId, parent);
        }
        OnmsResource resource = resources.get(resourceId);
        if (resource == null) {
            resource = new OnmsResource(attr, label, type, Sets.newHashSet(), ResourcePath.get("foo"));
            resource.setParent(parent);
            resources.put(resourceId, resource);
        }
        Set<OnmsAttribute> attributes = resource.getAttributes();
        attributes.add(new RrdGraphAttribute(attr, "", newtsResourceId));

        Resource res = new Resource(newtsResourceId);
        Row<Measurement> row = new Row<Measurement>(Timestamp.fromEpochSeconds(0), res);
        Measurement measurement = new Measurement(Timestamp.fromEpochSeconds(0), res, label, 0.0d);
        row.addElement(measurement);

        String name = ds != null ? ds : attr;

        ImmutableTimeSeriesData.ImmutableTimeSeriesDataBuilder data = ImmutableTimeSeriesData.builder();
        ImmutableMetric metric = ImmutableMetric.builder()
                .intrinsicTag(IntrinsicTagNames.resourceId, newtsResourceId)
                .intrinsicTag(IntrinsicTagNames.name, name)
                .build();
        data.metric(metric);
        data.dataPoint(ImmutableDataPoint.builder()
                .time(Instant.ofEpochMilli(START_TIME))
                .value(33.0)
                .build());

        TimeSeriesFetchRequest request = ImmutableTimeSeriesFetchRequest.builder()
                .metric(metric)
                .aggregation(Aggregation.AVERAGE)
                .start(Instant.ofEpochMilli(START_TIME))
                .end(Instant.ofEpochMilli(END_TIME))
                .step(Duration.ofMillis(STEP))
                .build();

        if (expect) {
            when(timeSeriesStorage.getTimeSeriesData(request)).thenReturn(data.build());
        }

        final Source source = new Source();
        source.setAggregation("AVERAGE");
        source.setAttribute(attr);
        source.setDataSource(ds);
        source.setLabel(label);
        source.setResourceId(resourceId.toString());
        source.setTransient(false);
        return source;
    }

    private void replay() {
        for (Entry<ResourceId, OnmsResource> entry : resources.entrySet()) {
            when(resourceDao.getResourceById(entry.getKey())).thenReturn(entry.getValue());
        }
    }
}
