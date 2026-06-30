package com.jnhro1.rechatbackend.session.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;

@Getter
@RequiredArgsConstructor
public enum SessionSortType {

    CREATED_DESC(Sort.by(Sort.Direction.DESC, "createdAt", "id")),
    CREATED_ASC(Sort.by(Sort.Direction.ASC, "createdAt", "id"));

    private final Sort sort;
}
