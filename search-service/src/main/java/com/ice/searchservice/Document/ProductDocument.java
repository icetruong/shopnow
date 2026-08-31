package com.ice.searchservice.Document;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Document(indexName = "products")
@Setting(settingPath = "elastic/product-settings.json")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDocument {

    @Id
    private String productId;

    // multi-field: name (text) để full-text search, name.keyword để sort A-Z, name.suggest để autocomplete
    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "vietnamese_analyzer"),
            otherFields = {
                    @InnerField(suffix = "keyword", type = FieldType.Keyword),
                    @InnerField(suffix = "suggest", type = FieldType.Search_As_You_Type)
            }
    )
    private String name;

    @Field(type = FieldType.Text, analyzer = "vietnamese_analyzer")
    private String description;

    @Field(type = FieldType.Keyword)
    private String slug;

    @Field(type = FieldType.Keyword, index = false)
    private String thumbnail;

    @Field(type = FieldType.Long)
    private Long basePrice;

    @Field(type = FieldType.Long)
    private Long salePrice;

    @Field(type = FieldType.Float)
    private BigDecimal rating;

    @Field(type = FieldType.Integer)
    private Integer reviewCount;

    @Field(type = FieldType.Integer)
    private Integer soldCount;

    @Field(type = FieldType.Keyword)
    private String categoryId;

    @Field(type = FieldType.Keyword)
    private String categoryName;

    @Field(type = FieldType.Keyword)
    private List<String> colors;

    @Field(type = FieldType.Keyword)
    private List<String> sizes;

    @Field(type = FieldType.Boolean)
    private Boolean isActive;

    @Field(type = FieldType.Boolean)
    private Boolean isDeleted;

    @Field(type = FieldType.Date, format = DateFormat.date_optional_time)
    private LocalDateTime createdAt;

    @Field(type = FieldType.Date, format = DateFormat.date_optional_time)
    private LocalDateTime updatedAt;
}
