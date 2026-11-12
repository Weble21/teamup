# 농구 목데이터 팀 편성 테스트

- 모임 이름: `test`
- 종목: `BASKETBALL`
- 등록 회원: 21명
- 참석 회원: 14명, 불참 7명
- 팀 수: 2개, 각 7명

[테스트 fixture](../src/test/kotlin/com/teamup/teamup/model/service/LakersBasketballFixture.kt)는 [NBA 레이커스 공식 명단](https://www.nba.com/team/1610612747/lakers/roster)의 선수 18명과 같은 페이지의 역대 선수 3명(Kobe Bryant, Magic Johnson, Shaquille O'Neal) 이름을 참고했다. **현재 레이커스의 21명 명단이라는 뜻은 아니다.** 포지션 세분화와 능력치는 앱 동작을 확인하기 위한 임의의 테스트 값이며 실제 선수 평가가 아니다.

## 편성 결과

`가드, 센터, 포워드를 골고루 나눠줘` 조건으로 14명을 편성했다.

| 팀 | 선수 | 인원 | 능력치 총합 | 화면 평균 |
| --- | --- | ---: | ---: | ---: |
| 1 | Luka Dončić, Shaquille O'Neal, Austin Reaves, Jarred Vanderbilt, Quentin Grimes, Sandro Mamukelashvili, Ziaire Williams | 7 | 560 | 8.0 / 10 |
| 2 | Kobe Bryant, Magic Johnson, Collin Sexton, Walker Kessler, Jake LaRavia, Kevon Looney, Bronny James | 7 | 550 | 약 7.9 / 10 |

두 팀의 능력치 총합 차이는 10점(화면 단위로 1.0점)이다. 팀 평균 차이는 화면 단위로 약 0.14점이며, 화면에서는 소수 첫째 자리로 반올림해 0.1점으로 보인다. 참석자 14명 모두 정확히 한 번 배정되고 불참자 7명은 배정되지 않는지, 포지션 조건이 반영되는지 검증한다.

## 실행과 범위

```powershell
.\gradlew.bat test --tests com.teamup.teamup.model.service.TeamAssignmentServiceTest
```

H2 테스트 DB에서 모임과 회원을 생성해 `TeamAssignmentService`를 검증한다. 자연어 분류 결과는 테스트에서 목 처리하고, 별도로 실행 중인 로컬 임베딩 서비스에서 위 문장을 `POSITION_BALANCE`로 분류하는 것을 확인했다. 테스트 데이터는 현재 로그인 계정의 모임이나 MySQL DB에 저장되지 않는다. Claude 팀 분석 API는 이 테스트에서 호출하지 않는다.
