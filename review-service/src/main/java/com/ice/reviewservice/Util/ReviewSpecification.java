package com.ice.reviewservice.Util;

import com.ice.reviewservice.Entity.Review;
import com.ice.reviewservice.Entity.ReviewImage;
import com.ice.reviewservice.Enum.ReviewStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;
import java.util.UUID;

public class ReviewSpecification {
    public static Specification<Review> hasRating(Short rating)
    {
        return (root, query, cb) ->
                rating == null
                ? null
                        : cb.equal(root.get("rating"), rating);
    }

    public static Specification<Review> hasProductId(UUID productId)
    {
        return (root, query, cb) ->
                productId == null
                        ? null
                        : cb.equal(root.get("productId"), productId);
    }

    public static Specification<Review> hasUserId(UUID userId)
    {
        return (root, query, cb) ->
                userId == null
                        ? null
                        : cb.equal(root.get("userId"), userId);
    }

    public static Specification<Review> hasStatus(ReviewStatus status) {
        return (root, q, cb) ->
                status == null
                ? null
                        : cb.equal(root.get("status"), status);
    }

    public static Specification<Review> hasStatusIn(Collection<ReviewStatus> statuses) {
        return (root, q, cb) ->
                (statuses == null || statuses.isEmpty())
                        ? null
                        : root.get("status").in(statuses);
    }

    public static Specification<Review> hasImage(Boolean hasImage)
    {
        if (!Boolean.TRUE.equals(hasImage))
            return null;

        return (root, query, cb) -> {
            var sub = query.subquery(Long.class);
            var img = sub.from(ReviewImage.class);

            sub.select(cb.literal(1L))
                    .where(cb.equal(img.get("review").get("id"), root.get("id")));

            return cb.exists(sub);
        };
    }
}
