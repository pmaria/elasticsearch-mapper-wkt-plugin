package org.elasticsearch.index.mapper.geo.wkt;

import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.Version;
import org.elasticsearch.common.Explicit;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.geo.SpatialStrategy;
import org.elasticsearch.common.geo.builders.ShapeBuilder;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentParser.Token;
import org.elasticsearch.common.xcontent.support.XContentMapValues;
import org.elasticsearch.index.mapper.FieldMapper;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.index.mapper.Mapper;
import org.elasticsearch.index.mapper.MapperParsingException;
import org.elasticsearch.index.mapper.ParseContext;
import org.elasticsearch.index.mapper.geo.GeoShapeFieldMapper;

import com.spatial4j.core.context.jts.JtsSpatialContext;
import com.spatial4j.core.context.jts.JtsSpatialContextFactory;
import com.spatial4j.core.exception.InvalidShapeException;
import com.spatial4j.core.io.jts.JtsWKTReader;
import com.spatial4j.core.shape.Point;
import com.spatial4j.core.shape.Shape;
import com.spatial4j.core.shape.jts.JtsGeometry;

import org.apache.lucene.document.Field;

import java.io.IOException;
import java.text.ParseException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.common.xcontent.support.XContentMapValues.nodeBooleanValue;

/**
 * FieldMapper for indexing WKT strings as {@link Shape}s.
 * <p>
 * Format supported:
 * <p>
 * "field" : "POINT (13.400544 52.530286)"
 * 
 * @author Pano Maria
 * @author Joost Farla
 *
 */
public class WktFieldMapper extends GeoShapeFieldMapper {

    public static final String CONTENT_TYPE = "wkt";

    public static final JtsSpatialContext SPATIAL_CONTEXT = JtsSpatialContext.GEO;

    /**
     * A copy of the {@link GeoShapeFieldMapper.Builder}. But, instead of
     * building a {@link GeoShapeFieldMapper}, this builds a
     * {@link WktFieldMapper}.
     * 
     * @author Pano Maria
     *
     */
    public static class Builder extends FieldMapper.Builder<Builder, WktFieldMapper> {
        private Boolean coerce;

        public Builder(String name) {
            super(name, Defaults.FIELD_TYPE, Defaults.FIELD_TYPE);
        }

        public GeoShapeFieldType fieldType() {
            return (GeoShapeFieldType) fieldType;
        }

        public Builder coerce(boolean coerce) {
            this.coerce = coerce;
            return builder;
        }

        protected Explicit<Boolean> coerce(BuilderContext context) {
            if (coerce != null) {
                return new Explicit<>(coerce, true);
            }
            if (context.indexSettings() != null) {
                return new Explicit<>(context.indexSettings().getAsBoolean("index.mapping.coerce", Defaults.COERCE.value()), false);
            }
            return Defaults.COERCE;
        }

        @Override
        public WktFieldMapper build(BuilderContext context) {
            GeoShapeFieldType geoShapeFieldType = (GeoShapeFieldType) fieldType;

            if (geoShapeFieldType.tree().equals(Names.TREE_QUADTREE) && context.indexCreatedVersion().before(Version.V_2_0_0_beta1)) {
                geoShapeFieldType.setTree("legacyquadtree");
            }

            if (context.indexCreatedVersion().before(Version.V_2_0_0_beta1)
                    || (geoShapeFieldType.treeLevels() == 0 && geoShapeFieldType.precisionInMeters() < 0)) {
                geoShapeFieldType.setDefaultDistanceErrorPct(Defaults.LEGACY_DISTANCE_ERROR_PCT);
            }
            setupFieldType(context);

            return new WktFieldMapper(name, fieldType, coerce(context), context.indexSettings(), multiFieldsBuilder.build(this, context),
                    copyTo);
        }
    }

    /**
     * A copy of the {@link GeoShapeFieldMapper.TypeParser}. But, instead
     * returning a {@link GeoShapeFieldMapper.Builder}, this builds a
     * {@link Builder}.
     * 
     * @author Pano Maria
     *
     */
    public static class TypeParser implements Mapper.TypeParser {

        @Override
        public Mapper.Builder<?, ?> parse(String name, Map<String, Object> node, ParserContext parserContext)
                throws MapperParsingException {
            Builder builder = new Builder(name);
            for (Iterator<Map.Entry<String, Object>> iterator = node.entrySet().iterator(); iterator.hasNext();) {
                Map.Entry<String, Object> entry = iterator.next();
                String fieldName = Strings.toUnderscoreCase(entry.getKey());
                Object fieldNode = entry.getValue();
                if (Names.TREE.equals(fieldName)) {
                    builder.fieldType().setTree(fieldNode.toString());
                    iterator.remove();
                } else if (Names.TREE_LEVELS.equals(fieldName)) {
                    builder.fieldType().setTreeLevels(Integer.parseInt(fieldNode.toString()));
                    iterator.remove();
                } else if (Names.TREE_PRESISION.equals(fieldName)) {
                    builder.fieldType()
                            .setPrecisionInMeters(DistanceUnit.parse(fieldNode.toString(), DistanceUnit.DEFAULT, DistanceUnit.DEFAULT));
                    iterator.remove();
                } else if (Names.DISTANCE_ERROR_PCT.equals(fieldName)) {
                    builder.fieldType().setDistanceErrorPct(Double.parseDouble(fieldNode.toString()));
                    iterator.remove();
                } else if (Names.ORIENTATION.equals(fieldName)) {
                    builder.fieldType().setOrientation(ShapeBuilder.orientationFromString(fieldNode.toString()));
                    iterator.remove();
                } else if (Names.STRATEGY.equals(fieldName)) {
                    builder.fieldType().setStrategyName(fieldNode.toString());
                    iterator.remove();
                } else if (Names.COERCE.equals(fieldName)) {
                    builder.coerce(nodeBooleanValue(fieldNode));
                    iterator.remove();
                } else if (Names.STRATEGY_POINTS_ONLY.equals(fieldName)
                        && builder.fieldType().strategyName().equals(SpatialStrategy.TERM.getStrategyName()) == false) {
                    builder.fieldType().setPointsOnly(XContentMapValues.nodeBooleanValue(fieldNode));
                    iterator.remove();
                }
            }
            return builder;
        }
    }

    protected WktFieldMapper(String simpleName, MappedFieldType fieldType, Explicit<Boolean> coerce, Settings indexSettings,
            MultiFields multiFields, CopyTo copyTo) {
        super(simpleName, fieldType, coerce, indexSettings, multiFields, copyTo);
    }

    @Override
    protected WktFieldMapper clone() {
        return (WktFieldMapper) super.clone();
    }

    /**
     * Parses the {@link ParseContext} to retrieve the WKT string and
     * subsequently attempts to parse that to a {@link Shape}. This shape is
     * then processed in the same way as is done by {@link GeoShapeFieldMapper}.
     * 
     * It always returns {@code null} because the mappings are not modified.
     */
    @Override
    public Mapper parse(ParseContext context) throws IOException {
        try {
            XContentParser parser = context.parser();
            if (parser.currentToken() == XContentParser.Token.VALUE_NULL) {
                return null;
            } else if (parser.currentToken() != XContentParser.Token.VALUE_STRING
                    && parser.nextToken() != XContentParser.Token.END_OBJECT) {
                throw new ElasticsearchParseException("Must be an object consisting of type and WKT string");
            }

            // parse the document and populate the spatial4j Shape
            Shape shape = parseWktToShape(parser);

            // If shape is still null, something has gone wrong
            if (shape == null) {
                return null;
            }

            // Standard GeoShapeFieldMapper behavior
            if (fieldType().pointsOnly() && !(shape instanceof Point)) {
                throw new MapperParsingException("[{" + fieldType().names().fullName() + "}] is configured for points only but a "
                        + ((shape instanceof JtsGeometry) ? ((JtsGeometry) shape).getGeom().getGeometryType() : shape.getClass())
                        + " was found");
            }

            Field[] fields = fieldType().defaultStrategy().createIndexableFields(shape);
            if (fields == null || fields.length == 0) {
                return null;
            }

            for (Field field : fields) {
                if (!customBoost()) {
                    field.setBoost(fieldType().boost());
                }
                context.doc().add(field);
            }

        } catch (Exception e) {
            throw new MapperParsingException("failed to parse [" + fieldType().names().fullName() + "]", e);
        }
        return null;
    }

    /**
     * Get's the current token from the {@link XContentParser}, and checks that
     * its value is a string. It reads the string as WKT using a
     * {@link JtsWKTReader}, and attempts to parse it to a {@link Shape}
     * 
     * @param parser
     *            A parser who's current state is expected to be on the WKT
     *            token.
     * @return The WKT string parsed to a {@link Shape}
     * @throws IOException
     */
    private Shape parseWktToShape(XContentParser parser) throws IOException {
        Shape shape = null;
        Token wktToken = parser.currentToken();
        if (wktToken == XContentParser.Token.VALUE_NULL) {
            throw new IllegalArgumentException("location cannot contain NULL values)");
        } else if (wktToken != Token.VALUE_STRING) {
            throw new IllegalArgumentException("location must be a WKT string)");
        } else {
            String wktString = parser.textOrNull();

            // Pano: This is a hack. The factory is required in the WKTReader
            // constructor, but isn't used.
            JtsSpatialContextFactory factory = new JtsSpatialContextFactory();
            
            // Pano: Use JtsWKTReader for now, to support Polygon and MultiPolygon
            // TODO: Revisit this when ES upgrades to Spatial4J 0.6.
            JtsWKTReader wktReader = new JtsWKTReader(SPATIAL_CONTEXT, factory);
            //com.spatial4j.core.io.WKTReader wktReader = new com.spatial4j.core.io.WKTReader(SPATIAL_CONTEXT, factory);
            try {
                shape = wktReader.parse(wktString);
            } catch (InvalidShapeException e) {
                // TODO: determine proper way
                e.printStackTrace();
            } catch (ParseException e) {
                // TODO: determine proper way
                e.printStackTrace();
            }
        }
        return shape;
    }

    @Override
    protected void parseCreateField(ParseContext context, List<Field> fields) throws IOException {
    }

    @Override
    protected String contentType() {
        return CONTENT_TYPE;
    }
}
