package com.ice.searchservice.Repository;

import com.ice.searchservice.Document.ProductDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ProductSearchRepo extends ElasticsearchRepository<ProductDocument, String> {
}
