package org.elasticsearch.index.mapper.geo.wkt;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.geo.builders.ShapeBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.test.EsSingleWktMockNodeTestCase;

import java.io.IOException;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.index.query.QueryBuilders.geoIntersectionQuery;
import static org.elasticsearch.index.query.QueryBuilders.geoShapeQuery;
import static org.elasticsearch.index.query.QueryBuilders.geoWithinQuery;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertSearchResponse;
import static org.hamcrest.Matchers.equalTo;

public class WktFieldMapperTest extends EsSingleWktMockNodeTestCase {
  
    private void initWktSimple() throws IOException {
        
        String mapping = XContentFactory.jsonBuilder()
                .startObject()
                    .startObject("my_type")
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
        .prepareCreate("my_index")
        .addMapping("my_type", mapping)
        .execute()
        .actionGet();
        
        ensureGreen();
    }
   
    public void testEnvelopeQuery() throws IOException {
        initWktSimple();
        
        client()
        .prepareIndex("my_index", "my_type", "1")
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
        System.out.println(shape);

        SearchResponse searchResponse = client()
                .prepareSearch("my_index")
                .setTypes("my_type")
                .setQuery(geoWithinQuery("location", shape))
                .execute().actionGet();

        System.out.println(searchResponse);

        assertSearchResponse(searchResponse);
        assertThat(searchResponse.getHits().getTotalHits(), equalTo(1l));
        assertThat(searchResponse.getHits().hits().length, equalTo(1));
        assertThat(searchResponse.getHits().getAt(0).id(), equalTo("1"));
    }

    public void testIndexPointsFilterRectangle() throws Exception {
        initWktSimple();
        
        client()
        .prepareIndex("my_index", "my_type", "1")
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
        .prepareIndex("my_index", "my_type", "2")
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
        System.out.println(shape);

        SearchResponse searchResponse = client()
                .prepareSearch("my_index")
                .setTypes("my_type")
                .setQuery(geoIntersectionQuery("location", shape))
                .execute().actionGet();

        System.out.println(searchResponse);

        assertSearchResponse(searchResponse);
        assertThat(searchResponse.getHits().getTotalHits(), equalTo(1l));
        assertThat(searchResponse.getHits().hits().length, equalTo(1));
        assertThat(searchResponse.getHits().getAt(0).id(), equalTo("1"));

        searchResponse = client()
                .prepareSearch("my_index")
                .setTypes("my_type")
                .setQuery(geoShapeQuery("location", shape))
                .execute()
                .actionGet();

        assertSearchResponse(searchResponse);
        assertThat(searchResponse.getHits().getTotalHits(), equalTo(1l));
        assertThat(searchResponse.getHits().hits().length, equalTo(1));
        assertThat(searchResponse.getHits().getAt(0).id(), equalTo("1"));
    }
    
    public void testEdgeCases() throws Exception {        
        String mapping = XContentFactory.jsonBuilder()
                .startObject()
                    .startObject("my_type_2")
                        .startObject("properties")
                            .startObject("name")
                                .field("type", "string")
                            .endObject()
                            .startObject("location")
                                .field("type", "wkt")
                                .field("tree", "quadtree")
                            .endObject()
                        .endObject()
                    .endObject()
                .endObject()
                .string();
        
        System.out.println(mapping);
  
        client()
        .admin()
        .indices()
        .prepareCreate("my_index_2")
        .addMapping("my_type_2", mapping)
        .execute()
        .actionGet();
        
        ensureGreen();

        client()
        .prepareIndex("my_index_2", "my_type_2", "blakely")
        .setSource(jsonBuilder()
                .startObject()
                    .field("name", "Blakely Island")
                    .field("location", "POLYGON ((-122.83 48.57, -122.77 48.56, -122.79 48.53, -122.83 48.57))")
                .endObject()
        )
        .setRefresh(true)
        .execute()
        .actionGet();

        ShapeBuilder query = ShapeBuilder.newEnvelope().topLeft(-122.88, 48.62).bottomRight(-122.82, 48.54);
        
        System.out.println(query);

        // This search would fail if both geoshape indexing and geoshape filtering
        // used the bottom-level optimization in SpatialPrefixTree#recursiveGetNodes.
        SearchResponse searchResponse = client()
                .prepareSearch("my_index_2")
                .setTypes("my_type_2")
                .setQuery(geoIntersectionQuery("location", query))
                .execute()
                .actionGet();
        
        System.out.println(searchResponse);

        assertSearchResponse(searchResponse);
        assertThat(searchResponse.getHits().getTotalHits(), equalTo(1l));
        assertThat(searchResponse.getHits().hits().length, equalTo(1));
        assertThat(searchResponse.getHits().getAt(0).id(), equalTo("blakely"));
    }
}
