package org.elasticsearch.plugin.mapper.geo.wkt;

import org.elasticsearch.index.mapper.geo.wkt.WktFieldMapper;
import org.elasticsearch.indices.IndicesModule;
import org.elasticsearch.plugins.Plugin;

/**
 * This plugin adds WKT mapping types and indexes them as native geo
 * {@link com.spatial4j.core.shape.Shape}s.
 * 
 * @author Joost Farla
 * @author Pano Maria
 *
 */
public class MapperWktPlugin extends Plugin {

    @Override
    public String name() {
        return "mapper-wkt";
    }

    @Override
    public String description() {
        return "Adds WKT mapping types and indexes them as native geo shapes";
    }

    public void onModule(IndicesModule indicesModule) {
        indicesModule.registerMapper(WktFieldMapper.CONTENT_TYPE, new WktFieldMapper.TypeParser());
    }
}
