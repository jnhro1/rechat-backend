-- =====================================================================
-- V1: 초기 스키마 — 1:1 채팅 + 이벤트 소싱 기반 상태 복원
--
-- 설계 요약
--  * session_events 가 단일 진실 원천(append-only). 정렬·복원 기준 = server_sequence.
--  * session_participants / session_messages 는 이벤트를 멱등 반영한 projection.
--  * session_snapshots 는 특정 시퀀스까지의 상태 캐시(스냅샷 + 이후 이벤트 리플레이).
--  * 중복 차단: (session_id, event_id) 유니크 = 클라이언트 멱등키.
--  * 시각은 UTC, 마이크로초 정밀도(datetime(6)).
-- =====================================================================

-- ---------------------------------------------------------------------
-- sessions : 1:1 채팅 세션 (애그리거트 루트)
-- ---------------------------------------------------------------------
CREATE TABLE sessions (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '세션 PK',
    status        VARCHAR(20)  NOT NULL                COMMENT '세션 상태: ACTIVE/INTERRUPTED/COMPLETED',
    started_at    DATETIME(6)  NOT NULL                COMMENT '세션 시작 시각',
    ended_at      DATETIME(6)  NULL                    COMMENT '세션 종료 시각 (미종료 시 null)',
    last_sequence BIGINT       NOT NULL DEFAULT 0      COMMENT '세션 내 서버 시퀀스 발급기 (단조 증가)',
    created_at    DATETIME(6)  NOT NULL                COMMENT '생성 시각 (UTC)',
    updated_at    DATETIME(6)  NOT NULL                COMMENT '수정 시각 (UTC)',
    PRIMARY KEY (id),
    -- 세션 목록 조회(상태 필터 + 생성시각 정렬)용 복합 인덱스.
    --   status 필터 + created_at 범위/정렬을 한 인덱스로 처리(명세 7.1).
    KEY idx_sessions_status_created (status, created_at),
    -- status 필터가 없는 기본 목록(ORDER BY created_at DESC)용 단일 인덱스.
    --   복합 인덱스는 선두 컬럼(status)이 조건에 없으면 정렬에 활용 불가하므로 별도 인덱스가 필요.
    --   InnoDB가 PK(id)를 자동 append → (created_at, id) 안정 정렬을 인덱스만으로 충족.
    KEY idx_sessions_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '1:1 채팅 세션';

-- ---------------------------------------------------------------------
-- session_participants : 참여자 + Presence (projection)
-- ---------------------------------------------------------------------
CREATE TABLE session_participants (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '참여자 PK',
    session_id          BIGINT       NOT NULL                COMMENT '소속 세션 FK',
    user_id             VARCHAR(64)  NOT NULL                COMMENT '외부 사용자 식별자',
    presence            VARCHAR(16)  NOT NULL                COMMENT '접속 상태: ONLINE/OFFLINE',
    joined_at           DATETIME(6)  NOT NULL                COMMENT '입장 시각',
    left_at             DATETIME(6)  NULL                    COMMENT '퇴장 시각 (미퇴장 시 null)',
    last_seen_at        DATETIME(6)  NOT NULL                COMMENT '마지막 접속 시각',
    created_at          DATETIME(6)  NOT NULL                COMMENT '생성 시각 (UTC)',
    updated_at          DATETIME(6)  NOT NULL                COMMENT '수정 시각 (UTC)',
    PRIMARY KEY (id),
    -- 한 세션 내 동일 사용자 중복 참여 차단
    UNIQUE KEY uk_participant_session_user (session_id, user_id),
    KEY idx_participant_session (session_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '세션 참여자 + Presence 상태';

-- ---------------------------------------------------------------------
-- session_events : 이벤트 원장 (Event Store, append-only)
-- ---------------------------------------------------------------------
CREATE TABLE session_events (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '이벤트 PK (서버 채번)',
    session_id      BIGINT       NOT NULL                COMMENT '소속 세션 FK',
    server_sequence BIGINT       NOT NULL                COMMENT '세션 내 서버 시퀀스 (정렬·복원 기준)',
    event_id        VARCHAR(64)  NOT NULL                COMMENT '클라이언트 생성 멱등키(UUID) — 중복 차단',
    sender_id       VARCHAR(64)  NOT NULL                COMMENT '이벤트를 발생시킨 참여자',
    type            VARCHAR(20)  NOT NULL                COMMENT '이벤트 종류: MESSAGE/JOIN/LEAVE/DISCONNECT/RECONNECT/EDIT/DELETE',
    payload         JSON         NULL                    COMMENT '타입별 가변 데이터(JSON)',
    occurred_at     DATETIME(6)  NOT NULL                COMMENT '클라이언트 발생 시각',
    created_at      DATETIME(6)  NOT NULL                COMMENT '서버 수신 시각 (UTC)',
    updated_at      DATETIME(6)  NOT NULL                COMMENT '수정 시각 (UTC)',
    PRIMARY KEY (id),
    -- 중복 이벤트 차단(클라이언트 멱등키)
    UNIQUE KEY uk_event_session_eventid (session_id, event_id),
    -- 시퀀스 무결성 + 세션 순차 조회/복원 핫패스(WHERE session_id=? AND server_sequence>? ORDER BY server_sequence)
    UNIQUE KEY uk_event_session_seq (session_id, server_sequence),
    -- 특정 시점(occurred_at) 이전 이벤트 조회용 보조 인덱스
    KEY idx_event_session_occurred (session_id, occurred_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '세션 이벤트 원장 (append-only)';

-- ---------------------------------------------------------------------
-- session_messages : 메시지 조회 모델 (projection)
-- ---------------------------------------------------------------------
CREATE TABLE session_messages (
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '메시지 PK',
    session_id      BIGINT        NOT NULL                COMMENT '소속 세션 FK',
    server_sequence BIGINT        NOT NULL                COMMENT '생성 이벤트 서버 시퀀스 (메시지 식별·정렬 키)',
    sender_id       VARCHAR(64)   NOT NULL                COMMENT '보낸 참여자',
    content         VARCHAR(1000) NULL                    COMMENT '메시지 본문 (삭제 시 null)',
    status          VARCHAR(16)   NOT NULL                COMMENT '메시지 상태: SENT/EDITED/DELETED',
    edited_at       DATETIME(6)   NULL                    COMMENT '수정 시각',
    deleted_at      DATETIME(6)   NULL                    COMMENT '삭제 시각',
    created_at      DATETIME(6)   NOT NULL                COMMENT '생성 시각 (UTC)',
    updated_at      DATETIME(6)   NOT NULL                COMMENT '수정 시각 (UTC)',
    PRIMARY KEY (id),
    -- 메시지 식별 + 최근 N개 조회 정렬 인덱스 겸용
    UNIQUE KEY uk_message_session_seq (session_id, server_sequence)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '메시지 조회 모델 (projection)';

-- ---------------------------------------------------------------------
-- session_snapshots : 상태 스냅샷
-- ---------------------------------------------------------------------
CREATE TABLE session_snapshots (
    id              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '스냅샷 PK',
    session_id      BIGINT      NOT NULL                COMMENT '소속 세션 FK',
    upto_sequence   BIGINT      NOT NULL                COMMENT '이 서버 시퀀스까지 반영된 상태',
    state           JSON        NOT NULL                COMMENT '직렬화된 세션 상태(JSON)',
    version         INT         NOT NULL                COMMENT '스냅샷 포맷 버전',
    max_occurred_at DATETIME(6) NOT NULL                COMMENT '커버한 이벤트(1..upto_sequence) 중 최대 occurred_at(watermark) — 과거 시점 복원 base 안전성 판단',
    created_at      DATETIME(6) NOT NULL                COMMENT '생성 시각 (UTC)',
    updated_at      DATETIME(6) NOT NULL                COMMENT '수정 시각 (UTC)',
    PRIMARY KEY (id),
    -- 동일 지점 중복 스냅샷 방지
    UNIQUE KEY uk_snapshot_session_upto (session_id, upto_sequence),
    -- 과거 시점 복원: WHERE session_id=? AND max_occurred_at<=? ORDER BY upto_sequence DESC
    KEY idx_snapshot_session_watermark (session_id, max_occurred_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '세션 상태 스냅샷';

-- ---------------------------------------------------------------------
-- users : 서비스 사용자 (로그인 없는 데모용, 기동 시 ApplicationRunner가 시드)
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '사용자 PK',
    username   VARCHAR(64) NOT NULL                COMMENT '외부 식별자 (참여자 userId로 사용)',
    created_at DATETIME(6) NOT NULL                COMMENT '생성 시각 (UTC)',
    updated_at DATETIME(6) NOT NULL                COMMENT '수정 시각 (UTC)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '서비스 사용자';
