# Elasticsearch Mapper WKT Plugin

This is a plugin for [Elasticsearch](http://github.com/elasticsearch/elasticsearch). It allows the use of [WKT](https://en.wikipedia.org/wiki/Well-known_text) for indexing Geo Shapes in Elasticsearch.

After indexing, the Elasticsearch [Query DSL for Geo Shapes](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-geo-shape-query.html) can be used normally.

## Installation
You need [Apache Maven](http://maven.apache.org/) to build and package the plugin.

Run the following in the project root directory:

    mvn package

This will create a zipped plugin under [mapper-wkt-home]/target/releases.

Then, in [elasticsearch_home] install the plugin:

    ./bin/plugin install file:///path/to/plugin.zip

or, on Windows:

    .bin\plugin.bat install file:///path/to/plugin.zip

If it was running, restart the node after installing.

## Regular scenario

Create index with geo mappings:

```
PUT http://localhost:9200/my_index
{
  "mappings": {
    "my_type": {
      "properties": {
        "name": {
          "type": "string"
        },
        "location": {
          "type": "geo_shape"
        }
      }
    }
  }
}
```

Load data:

```
PUT http://localhost:9200/my_index/my_type/1
{
  "name": "Wind & Wetter, Berlin, Germany",
  "location": {
    "type": "point",
    "coordinates": [13.400544, 52.530286]
  }
}
```

## Desired scenario

Create index with geo mappings:

```
PUT http://localhost:9200/my_index
{
  "mappings": {
    "my_type": {
      "properties": {
        "name": {
          "type": "string"
        },
        "location": {
          "type": "wkt"
        }
      }
    }    
  }
}
```

Load data:

```
PUT http://localhost:9200/my_index/my_type/1
{
  "name": "Wind & Wetter, Berlin, Germany",
  "location": "POINT (13.400544 52.530286)"
}
```

## Test query

Test the result of the above loading scenarios:

```
POST http://localhost:9200/my_index/my_type/_search
{
  "query":{
    "bool": {
      "must": {
        "match_all": {}
      },
      "filter": {
        "geo_shape": {
          "location": {
            "shape": {
              "type": "envelope",
              "coordinates" : [[13.0, 53.0], [14.0, 52.0]]
            },
            "relation": "within"
          }
        }
      }
    }
  }
}
```

## Links

* https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping.html
* https://www.elastic.co/guide/en/elasticsearch/reference/current/geo-shape.html
* https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-geo-shape-query.html
* https://github.com/elastic/elasticsearch/tree/2.3/plugins/mapper-murmur3
* https://github.com/elastic/elasticsearch/tree/2.3/plugins/mapper-attachments
* https://www.elastic.co/blog/found-writing-a-plugin
* https://en.wikipedia.org/wiki/Well-known_text
