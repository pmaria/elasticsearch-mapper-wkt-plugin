package org.elasticsearch.index.mapper.wkt;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.mapper.FieldMapper;
import org.elasticsearch.index.mapper.MappedFieldType;

public class WKTFieldMapper extends FieldMapper {

    public static final String CONTENT_TYPE = "wkt";

    public WKTFieldMapper(String simpleName, MappedFieldType fieldType, MappedFieldType defaultFieldType,
                          Settings indexSettings, MultiFields multiFields, CopyTo copyTo) {
        super(simpleName, fieldType, defaultFieldType, indexSettings, multiFields, copyTo);
    }

    @Override
    protected String contentType() {
        return CONTENT_TYPE;
    }

}
