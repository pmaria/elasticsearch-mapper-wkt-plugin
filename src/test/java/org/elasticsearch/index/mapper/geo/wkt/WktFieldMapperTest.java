package org.elasticsearch.index.mapper.geo.wkt;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.geo.builders.ShapeBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.mapper.MapperParsingException;
import org.elasticsearch.test.EsSingleWktMockNodeTestCase;
import org.elasticsearch.test.geo.RandomShapeGenerator;

import com.spatial4j.core.io.WKTWriter;

import java.io.IOException;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.index.query.QueryBuilders.geoIntersectionQuery;
import static org.elasticsearch.index.query.QueryBuilders.geoShapeQuery;
import static org.elasticsearch.index.query.QueryBuilders.geoWithinQuery;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertSearchResponse;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

/**
 * Tests for the {@link WktFieldMapper}.
 * TODO: Test indexed WKT shape; Test all geo mapping options
 * 
 * @author Pano Maria
 *
 */
public class WktFieldMapperTest extends EsSingleWktMockNodeTestCase {
  
    public void testEnvelopeQuery() throws IOException {
        String[] index_type = initWktSimple();
        final String index = index_type[0], type = index_type[1];
        
        client()
        .prepareIndex(index, type, "1")
        .setSource(jsonBuilder()
                .startObject()
                    .field("name", "Wind & Wetter, Berlin, Germany")
                    .field("location", "POINT (13.400544 52.530286)")
                .endObject()
        )
        .setRefresh(true)
        .execute()
        .actionGet();
        
        ShapeBuilder shape = ShapeBuilder.newEnvelope().topLeft(13.0, 53.0).bottomRight(14.0, 52.0);

        SearchResponse searchResponse = client()
                .prepareSearch(index)
                .setTypes(type)
                .setQuery(geoWithinQuery("location", shape))
                .execute().actionGet();

        assertSearchResponse(searchResponse);
        assertThat(searchResponse.getHits().getTotalHits(), equalTo(1l));
        assertThat(searchResponse.getHits().hits().length, equalTo(1));
        assertThat(searchResponse.getHits().getAt(0).id(), equalTo("1"));
    }

    public void testIndexPointsFilterRectangle() throws Exception {
        String[] index_type = initWktSimple();
        final String index = index_type[0], type = index_type[1];
        
        client()
        .prepareIndex(index, type, "1")
        .setSource(jsonBuilder()
                .startObject()
                    .field("name", "Document1")
                    .field("location", "POINT (-30 -30)")
                .endObject()
        )
        .setRefresh(true)
        .execute()
        .actionGet();
        
        client()
        .prepareIndex(index, type, "2")
        .setSource(jsonBuilder()
                .startObject()
                    .field("name", "Document 2")
                    .field("location", "POINT (-45 -50)")
                .endObject()
        )
        .setRefresh(true)
        .execute()
        .actionGet();

        ShapeBuilder shape = ShapeBuilder.newEnvelope().topLeft(-45, 45).bottomRight(45, -45);

        SearchResponse searchResponse = client()
                .prepareSearch(index)
                .setTypes(type)
                .setQuery(geoIntersectionQuery("location", shape))
                .execute().actionGet();

        assertSearchResponse(searchResponse);
        assertThat(searchResponse.getHits().getTotalHits(), equalTo(1l));
        assertThat(searchResponse.getHits().hits().length, equalTo(1));
        assertThat(searchResponse.getHits().getAt(0).id(), equalTo("1"));

        searchResponse = client()
                .prepareSearch(index)
                .setTypes(type)
                .setQuery(geoShapeQuery("location", shape))
                .execute()
                .actionGet();

        assertSearchResponse(searchResponse);
        assertThat(searchResponse.getHits().getTotalHits(), equalTo(1l));
        assertThat(searchResponse.getHits().hits().length, equalTo(1));
        assertThat(searchResponse.getHits().getAt(0).id(), equalTo("1"));
    }
    
    public void testEdgeCases() throws Exception {
        String[] index_type = initWktQuadTree();
        final String index = index_type[0], type = index_type[1];
        
        client()
        .prepareIndex(index, type, "blakely")
        .setSource(jsonBuilder()
                .startObject()
                    .field("name", "Blakely Island")
                    .field("wkt-quadtree", "POLYGON ((-122.83 48.57, -122.77 48.56, -122.79 48.53, -122.83 48.57))")
                .endObject()
        )
        .setRefresh(true)
        .execute()
        .actionGet();

        ShapeBuilder query = ShapeBuilder.newEnvelope().topLeft(-122.88, 48.62).bottomRight(-122.82, 48.54);

        // This search would fail if both geoshape indexing and geoshape filtering
        // used the bottom-level optimization in SpatialPrefixTree#recursiveGetNodes.
        SearchResponse searchResponse = client()
                .prepareSearch(index)
                .setTypes(type)
                .setQuery(geoIntersectionQuery("wkt-quadtree", query))
                .execute()
                .actionGet();

        assertSearchResponse(searchResponse);
        assertThat(searchResponse.getHits().getTotalHits(), equalTo(1l));
        assertThat(searchResponse.getHits().hits().length, equalTo(1));
        assertThat(searchResponse.getHits().getAt(0).id(), equalTo("blakely"));
    }
    
    public void testPointsOnly() throws Exception {
        String[] index_type = initWktComplexPointsOnly();
        final String index = index_type[0], type = index_type[1];

        ShapeBuilder shape = RandomShapeGenerator.createShape(random());
        String wktShape = new WKTWriter().toString(shape.build());
        
        try {
            client()
            .prepareIndex(index, type, "1")
            .setSource(jsonBuilder()
                    .startObject()
                        .field("name", "Document1")
                        .field("location", wktShape)
                    .endObject()
            )
            .setRefresh(true).execute().actionGet();
        } catch (MapperParsingException e) {
            // RandomShapeGenerator created something other than a POINT type, verify the correct exception is thrown
            assertThat(e.getCause().getMessage(), containsString("is configured for points only"));
            return;
        }

        // test that point was inserted
        SearchResponse response = client()
                .prepareSearch(index)
                .setTypes(type)
                .setQuery(geoIntersectionQuery("location", shape))
                .execute()
                .actionGet();

        assertEquals(1, response.getHits().getTotalHits());
    }
    
    private String[] initWktSimple() throws IOException {
        final String index = "my_index";
        final String type = "my_type";
        
        String mapping = XContentFactory.jsonBuilder()
                .startObject()
                    .startObject(type)
                        .startObject("properties")
                            .startObject("name")
                                .field("type", "string")
                            .endObject()
                            .startObject("location")
                                .field("type", "wkt")
                            .endObject()
                        .endObject()
                    .endObject()
                .endObject()
                .string();
        
        client()
        .admin()
        .indices()
        .prepareCreate(index)
        .addMapping(type, mapping)
        .execute()
        .actionGet();
        
        ensureGreen();
        
        return new String[]{index, type};
    }
    
    private String[] initWktQuadTree() throws IOException {
        final String index = "my_index_2";
        final String type = "my_type_2";
        
        String mapping = XContentFactory.jsonBuilder()
                .startObject()
                    .startObject(type)
                        .startObject("properties")
                            .startObject("name")
                                .field("type", "string")
                            .endObject()
                            .startObject("wkt-quadtree")
                                .field("type", "wkt")
                                .field("tree", "quadtree")
                            .endObject()
                        .endObject()
                    .endObject()
                .endObject()
                .string();
  
        client()
        .admin()
        .indices()
        .prepareCreate(index)
        .addMapping(type, mapping)
        .execute()
        .actionGet();
        
        ensureGreen();
        
        return new String[]{index, type};
    }
    
    private String[] initWktComplexPointsOnly() throws IOException {
        final String index = "geo_points_only";
        final String type = "my_type_3";
        
        String mapping = XContentFactory.jsonBuilder()
                .startObject()
                    .startObject(type)
                        .startObject("properties")
                            .startObject("name")
                                .field("type", "string")
                            .endObject()
                            .startObject("location")
                                .field("type", "wkt")
                                .field("tree", randomBoolean() ? "quadtree" : "geohash")
                                .field("tree_levels", "6")
                                .field("distance_error_pct", "0.01")
                                .field("points_only", true)
                            .endObject()
                        .endObject()
                    .endObject()
                .endObject()
                .string();
  
        client()
        .admin()
        .indices()
        .prepareCreate(index)
        .addMapping(type, mapping)
        .execute()
        .actionGet();
        
        ensureGreen();
        
        return new String[]{index, type};
    }
}
