# TEAM UP API

기계 판독용 계약: [OpenAPI 3.1 문서](./openapi.json). Swagger Editor 등에서 가져와 확인할 수 있다.

## 로컬 실행과 인증

- Java 25, MySQL, Node.js 필요.
- 루트 `.env`에 실제 DB 및 소셜 제공자 설정을 입력한다. 값에 따옴표를 붙이지 않는다.
- 백엔드의 `oauth` 프로필을 활성화한다. `APP_BASE_URL=http://localhost:3000`이 기본값이다.
- 프론트엔드: `npm.cmd --prefix frontend run dev`. `BACKEND_URL` 기본값은 `http://127.0.0.1:8080`이며 Next.js 서버에서만 사용한다.
- 제공자 콘솔의 redirect URI: `http://localhost:3000/login/oauth2/code/{google|kakao|naver}`. 변경하면 콘솔과 `APP_BASE_URL`을 함께 맞춘다.
- 프론트엔드 `/oauth2/authorization/{provider}`에서 로그인한다. Next.js가 API·인증 시작·콜백을 Spring으로 프록시한다.
- 세션 쿠키 인증을 사용한다. 로그인 성공 후 프론트엔드로 돌아온다.
- 변경 요청 전에 `GET /api/auth/csrf` 응답의 `headerName`을 헤더 이름, `token`을 값으로 전달한다. 로그인·로그아웃 이후 토큰을 다시 받는다.
- 인증 정보는 localStorage에 저장하지 않는다. 게스트 데이터만 기존 localStorage에 남는다.

## 응답 규칙

Entity를 직접 반환하지 않고 JSON DTO를 사용한다. 미인증은 401, 잘못된 입력은 400, CSRF 실패는 403, 타인 소유 또는 없는 대상은 404다. 데이터 제약 충돌은 409다.

소유자 ID는 요청 본문이나 쿼리에서 받지 않는다. 아래 모든 모임 경로는 로그인한 소유자만 사용할 수 있다.

## API 목록

| Method | URI | 기능 |
| --- | --- | --- |
| GET | `/api/users/me` | 내 사용자 정보 |
| GET | `/api/auth/csrf` | CSRF 토큰 |
| POST | `/api/auth/logout` | 세션 무효화, 쿠키 만료, 204 |
| POST | `/api/clubs` | 모임 생성, 201 |
| GET | `/api/clubs` | 내 모임 목록, 최신 ID 순 |
| GET | `/api/clubs/{clubId}` | 모임 상세 |
| PUT | `/api/clubs/{clubId}` | 이름·종목 수정 |
| POST | `/api/clubs/{clubId}/members` | 회원 등록, 201 |
| GET | `/api/clubs/{clubId}/members` | 회원 목록, 최신 ID 순 |
| PUT | `/api/clubs/{clubId}/members/{memberId}` | 회원 정보 전체 수정 |
| PATCH | `/api/clubs/{clubId}/members/{memberId}/deactivate` | 비활성화, 반복 요청 가능 |
| POST | `/api/clubs/{clubId}/members/validate-participants` | 참석자 ID 검증 |
| POST | `/api/clubs/{clubId}/team-assignments` | 능력치 기반 편성 후보 |
| POST | `/api/clubs/{clubId}/team-assignments/validate` | 수동 편성 재검증·요약 재계산 |
| POST | `/api/clubs/{clubId}/team-assignments/swap` | 서로 다른 팀의 두 회원 교환 |
| POST | `/api/clubs/{clubId}/matches` | 경기 확정·스냅샷 저장, 201 |
| GET | `/api/clubs/{clubId}/matches?page=0&size=20` | 경기 요약 페이지 |
| GET | `/api/clubs/{clubId}/matches/{matchId}` | 경기 스냅샷 상세 |

모임 응답의 `activeMemberCount`는 활성 회원 수다. 모임 목록은 회원 수를 한 번에 집계한다. 회원 목록의 `active=true`는 활성만, `false`는 비활성만, 생략은 전체다.

## 요청 예시

모임 생성·수정:

```json
{"name":"주말 농구","sportType":"BASKETBALL"}
```

종목: `BASKETBALL`, `FUTSAL`, `FOOTBALL`, `VOLLEYBALL`, `BASEBALL`, `LEAGUE_OF_LEGENDS`, `OTHER`.

회원 등록·수정:

```json
{"name":"선수","position":"PG","rating":70,"age":25,"notes":"속공"}
```

이름 1~100자, 포지션 1~50자, 능력치 1~100, 나이 0 이상, 메모 최대 1000자. 나이·메모는 선택이다. PUT에서 선택 항목을 생략하면 기존 값이 삭제된다. 포지션은 현재 자유 입력이다. 비활성화는 삭제가 아니며 회원 정보 수정으로 재활성화되지 않는다.

편성 생성:

```json
{"memberIds":[1,2,3,4],"teamCount":2}
```

편성 재검증:

```json
{"memberIds":[1,2,3,4],"teams":[[1,4],[2,3]]}
```

교환:

```json
{"memberIds":[1,2,3,4],"teams":[[1,4],[2,3]],"firstMemberId":1,"secondMemberId":2}
```

모든 참석자는 현재 모임의 활성 회원이어야 한다. 빈 목록·중복·잘못된 ID·누락·초과 배정·빈 팀·2개 미만 팀·팀 간 2명 이상의 인원 차이는 거부한다. 합계와 평균은 DB의 현재 능력치로 재계산한다. 같은 입력은 같은 편성 후보를 반환하며 최적해는 보장하지 않는다. 포지션 분배·같은 팀/다른 팀·AI 제약은 아직 제공하지 않는다.

경기 확정:

```json
{
  "requestId":"00000000-0000-0000-0000-000000000001",
  "memberIds":[1,2,3,4],
  "teams":[[1,4],[2,3]],
  "playedAt":"2026-11-09T01:00:00Z",
  "memo":"주말 경기"
}
```

`requestId`는 한 번의 확정에 생성한 UUID이며 네트워크 재시도에도 유지한다. 같은 모임·요청 ID의 재요청은 처음 저장한 기록을 반환한다. 동시에 충돌하면 409를 받은 뒤 같은 ID로 재시도한다. `playedAt` 생략 시 서버 현재 시각, `memo` 최대 1000자다. 경기·팀·회원 스냅샷은 하나의 트랜잭션으로 저장한다. 현재 회원과 종목이 변경되거나 비활성화되어도 과거 기록은 바뀌지 않는다.

목록은 `playedAt DESC, id DESC`로 정렬한다. `page`는 0부터, `size`는 1~100이다. 응답은 `{matches, page, hasNext}`이며 상세는 선택 시 따로 조회한다.

## 검증 명령

```powershell
.\gradlew.bat test
npm.cmd --prefix frontend run lint
npm.cmd --prefix frontend run build
```

로컬 MySQL 마이그레이션을 명시적으로 적용·검증할 때만:

```powershell
.\gradlew.bat test --tests com.teamup.teamup.LocalMySqlMigrationTest -PverifyMysql
```

이 검증은 `.env`의 로컬 DB에 Flyway를 적용하며 데이터를 삭제하지 않는다. 일반 H2 통합 테스트는 운영 DB에 연결하지 않는다. 실제 소셜 제공자 로그인과 모바일/PWA 설치 검증은 별도이며, 자동 테스트 통과만으로 완료를 판단하지 않는다.
