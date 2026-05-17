package com.ice.productservice.Repository;

import com.ice.productservice.Document.ProductDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ProductSearchRepo extends ElasticsearchRepository<ProductDocument, String> {
}
