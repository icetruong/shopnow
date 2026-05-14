package com.qlda.userservice.Util;

import com.qlda.userservice.Entity.User;
import org.springframework.data.jpa.domain.Specification;

public class UserSpecification {
    public static Specification<User> hasKeyword(String keyword)
    {
        return (root, query, cb) ->
                keyword == null
                    ? null
                        : cb.like(cb.lower(root.get("fullName")), keyword.toLowerCase() + "%");
    }

    public static Specification<User> isActive(Boolean active)
    {
        return (root, query, cb) ->
                active == null
                        ? null
                        : cb.equal(root.get("isActive"), active);
    }

    public static Specification<User> hasProvider(String Provider)
    {
        return (root, query, cb) ->
                Provider == null
                        ? null
                        : cb.equal(root.get("provider"), Provider);
    }
}
