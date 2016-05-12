# Elasticsearch Mapper WKT Plugin

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
    "type": "Point",
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
* https://www.elastic.co/guide/en/elasticsearch/reference/current/geo-point.html
* https://www.elastic.co/guide/en/elasticsearch/reference/current/geo-shape.html
* https://github.com/elastic/elasticsearch/tree/2.3/plugins/mapper-murmur3
* https://github.com/elastic/elasticsearch/tree/2.3/plugins/mapper-attachments
* https://www.elastic.co/blog/found-writing-a-plugin
* https://en.wikipedia.org/wiki/Well-known_text
