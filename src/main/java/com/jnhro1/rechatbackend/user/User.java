package com.jnhro1.rechatbackend.user;

import com.jnhro1.rechatbackend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(name = "uk_users_username", columnNames = "username")
)
@Comment("서비스 사용자")
public class User extends BaseEntity {

    @Column(name = "username", nullable = false, length = 64)
    @Comment("로그인 없이 사용하는 외부 식별자 (참여자 userId로 사용)")
    private String username;

    public static User of(String username) {
        return User.builder()
                .username(username)
                .build();
    }
}
