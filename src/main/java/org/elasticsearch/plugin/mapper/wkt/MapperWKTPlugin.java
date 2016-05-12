package org.elasticsearch.plugin.mapper.wkt;

import org.elasticsearch.index.mapper.wkt.WKTFieldMapper;
import org.elasticsearch.indices.IndicesModule;
import org.elasticsearch.plugins.Plugin;

public class MapperWKTPlugin extends Plugin {

    @Override
    public String name() {
        return "mapper-wkt";
    }

    @Override
    public String description() {
        return "Adds WKT mapping types and indexes them as native geo properties";
    }

    public void onModule(IndicesModule indicesModule) {
        indicesModule.registerMapper(WKTFieldMapper.CONTENT_TYPE, new WKTFieldMapper.TypeParser());
    }

}
