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

사용자 ID는 인증 세션에서 결정한다. 모임 생성자는 관리자이며, 초대받은 로그인 사용자는 가입한 모임의 멤버 목록과 경기 기록을 조회하고 자신의 회원 정보만 등록·수정할 수 있다. 모임 수정, 타인 회원 관리, 초대 링크 관리, 팀 편성 및 경기 확정은 관리자 전용이다.

## API 목록

| Method | URI | 기능 |
| --- | --- | --- |
| GET | `/api/users/me` | 내 사용자 정보 |
| GET | `/api/users/me/sport-profiles` | 내 종목별 선수 정보 목록 |
| PUT | `/api/users/me/sport-profiles/{sportType}` | 내 종목별 선수 정보 등록·수정 |
| GET | `/api/auth/csrf` | CSRF 토큰 |
| POST | `/api/auth/logout` | 세션 무효화, 쿠키 만료, 204 |
| POST | `/api/clubs` | 모임 생성, 201 |
| GET | `/api/clubs` | 내 모임 목록, 최신 ID 순 |
| GET | `/api/clubs/{clubId}` | 모임 상세 |
| PUT | `/api/clubs/{clubId}` | 이름·종목 수정 |
| POST | `/api/clubs/{clubId}/invitation` | 관리자: 기존 링크를 무효화하고 새 초대 토큰 발급 |
| DELETE | `/api/clubs/{clubId}/invitation` | 관리자: 초대 링크 비활성화, 204 |
| POST | `/api/invitations/join` | 로그인 사용자: 초대 토큰으로 가입, 반복 요청 가능 |
| PUT | `/api/clubs/{clubId}/members/me` | 가입자의 회원 정보 생성 또는 수정 |
| POST | `/api/clubs/{clubId}/members` | 회원 등록, 201 |
| POST | `/api/clubs/{clubId}/members/bulk` | 회원 1~100명 일괄 등록, 201 |
| GET | `/api/clubs/{clubId}/members` | 회원 목록, 최신 ID 순 |
| PUT | `/api/clubs/{clubId}/members/{memberId}` | 회원 정보 전체 수정 |
| PATCH | `/api/clubs/{clubId}/members/{memberId}/deactivate` | 비활성화, 반복 요청 가능 |
| POST | `/api/clubs/{clubId}/members/validate-participants` | 참석자 ID 검증 |
| POST | `/api/clubs/{clubId}/team-assignments` | 능력치 기반 편성 후보, 선택적 자연어 조건 |
| POST | `/api/clubs/{clubId}/team-assignments/analyze` | Claude 팀별 핵심 선수·전력 분석 |
| POST | `/api/clubs/{clubId}/team-assignments/validate` | 수동 편성 재검증·요약 재계산 |
| POST | `/api/clubs/{clubId}/team-assignments/swap` | 서로 다른 팀의 두 회원 교환 |
| POST | `/api/clubs/{clubId}/matches` | 경기 확정·스냅샷 저장, 201 |
| GET | `/api/clubs/{clubId}/matches?page=0&size=20` | 경기 요약 페이지 |
| GET | `/api/clubs/{clubId}/matches/{matchId}` | 경기 스냅샷 상세 |

모임 응답의 `activeMemberCount`는 활성 회원 수다. 모임 목록은 회원 수를 한 번에 집계한다. 회원 목록은 관리자와 가입자에게 공개되며, `mine`은 로그인 사용자와 연결된 본인 정보인지 나타낸다. 가입자에게는 다른 회원의 나이와 메모가 `null`로 반환된다. `active=true`는 활성만, `false`는 비활성만, 생략은 전체다.

종목별 선수 정보는 사용자 계정에 `(user_id, sport_type)`당 하나씩 저장한다. 요청 본문은 회원 등록과 같은 `name`, `position`, `rating`, `age`, `notes`이며 나이는 0~130이다. 본인 계정으로만 조회·수정할 수 있다. 프런트엔드는 같은 종목의 모임에서 **내 선수 정보**를 새로 등록할 때 이 값을 입력란에 채운다. 다른 회원을 추가할 때는 빈 입력란을 사용하며, 이미 등록된 모임 회원 정보는 종목별 정보 변경에 따라 자동 갱신되지 않는다.

초대 토큰은 재사용 가능하며 새 토큰 발급 또는 비활성화 전까지 유효하다. 링크 비활성화는 기존 가입자를 내보내지 않는다. 토큰 원문은 발급할 때만 반환하고 서버에는 SHA-256 해시만 저장한다. 사용자 계정과 기존에 관리자가 입력한 회원은 자동 연결하지 않으므로 동일 인물이 중복 등록될 수 있다. 관리자가 비활성화한 본인 프로필은 가입자가 수정할 수 없다.

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

일괄 등록은 `{ "members": [{"name":"선수","position":"PG","rating":70}] }` 형식이며 전체 항목을 검증하고 하나의 트랜잭션으로 저장한다. 인증·CSRF·모임 소유권 검증은 단일 등록과 같다.

편성 생성:

```json
{"memberIds":[1,2,3,4],"teamCount":2,"conditionText":"민수와 철수는 다른 팀\n포지션을 골고루 나눠줘"}
```

편성 재검증:

```json
{"memberIds":[1,2,3,4],"teams":[[1,4],[2,3]]}
```

교환:

```json
{"memberIds":[1,2,3,4],"teams":[[1,4],[2,3]],"firstMemberId":1,"secondMemberId":2,"conditionText":"민수와 철수는 다른 팀"}
```

모든 참석자는 현재 모임의 활성 회원이어야 한다. 빈 목록·중복·잘못된 ID·누락·초과 배정·빈 팀·2개 미만 팀·팀 간 2명 이상의 인원 차이는 거부한다. 합계와 평균은 DB의 현재 능력치로 재계산한다. 같은 입력은 같은 편성 후보를 반환하며 최적해는 보장하지 않는다.

`conditionText`는 선택 사항이며 최대 500자, 한 줄에 하나씩 최대 5개다. 로그인 모드에서 한국어 문장을 로컬 다국어 문장 임베딩으로 관계 조건 또는 포지션 균형 조건으로 분류한다. 선수 이름은 임베딩 서비스에 전달하기 전에 `선수`로 바꾼다. 서버가 참석 선수의 이름 2명과 같은 팀·다른 팀 표현을 확인하고 해당 관계를 필수 제약으로 적용한다. 포지션 균형은 가능한 배치 중 우선순위를 높이는 조건이며 보장되는 최적해는 아니다. 해석이 불명확하거나 충돌하여 배치할 수 없으면 400을 반환한다. 응답의 `interpretedConditions`에 적용한 조건을 표시한다. 교환 요청에도 같은 `conditionText`를 보내야 관계 제약을 검증할 수 있다. 편성 결과의 Claude 분석은 별도 요청이다.

포지션 균형 조건에는 축구의 `LWB`, `MF`, `LW`, `ST` 및 농구의 `PG`, `SG`, `SF`, `PF`, `C`, `가드`, `포워드`, `센터` 같은 세부 표현을 사용할 수 있다. `PG`와 `포인트가드`, `C`와 `센터`처럼 같은 위치의 표기만 비교 시 정규화한다. 저장된 포지션 문자열은 변경하지 않는다. 같은 세부 위치가 한 팀에 몰리는 것을 우선 피하고, 같은 위치 계열도 고려한다. 이것은 최적 배치 보장이 아닌 휴리스틱이다.

팀 분석은 편성 결과를 표시한 뒤 별도 요청으로 실행한다. 요청 본문은 편성 재검증과 같은 `memberIds`·`teams`다. 서버에서 소유권·활성 회원·팀 구성을 다시 검증하고, Claude에는 종목·선수 ID·이름·포지션·능력치·팀 평균만 전달한다. 나이·메모는 전송하지 않는다. 응답은 팀별 `teamNumber`, `keyPlayerId`, `keyPlayerReason`, `strength`이며 핵심 선수가 실제 팀원인지 서버에서 확인한다. `ANTHROPIC_API_KEY`가 없으면 503, AI 응답 오류는 502다. 분석 실패는 편성 결과에 영향을 주지 않는다. 실제 Claude 호출은 API 키와 네트워크가 있어야 검증할 수 있다.

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
