# Docker 로컬 실행

Docker Desktop에서 Linux containers를 사용하며, 웹앱·Spring 백엔드·MySQL을 함께 실행한다.

## 최초 설정

1. 루트 `.env`에 소셜 로그인 제공자의 인증 정보를 설정한다. 기존 로컬 실행과 동일하게 Java properties 형식으로 작성하며 값에 따옴표를 붙이지 않는다. `APP_BASE_URL`을 생략하면 `http://localhost:3000`을 사용한다.
2. `.env.docker.example`을 `.env.docker`로 복사하고 비밀번호를 변경한다. `DB_PASSWORD`와 `MYSQL_PASSWORD`는 같아야 한다. `.env.docker`는 Git에 올리지 않는다.
3. Docker Desktop을 실행한 후 프로젝트 루트에서 실행한다.

```powershell
docker info
docker compose config --quiet
docker compose up -d --build
docker compose ps
docker compose logs --tail=100 backend
```

브라우저에서 <http://localhost:3000>에 접속한다. 프론트엔드는 `/api`, `/oauth2`, `/login/oauth2`를 Docker 네트워크의 백엔드로 전달한다. 소셜 제공자의 redirect URI는 `http://localhost:3000/login/oauth2/code/{provider}`와 일치해야 한다.

회원 명단 Excel 파일은 내부 `imports` 컨테이너에서 읽는다. 사진 OCR 기능은 제공하지 않는다. 현재 포트가 PC의 `127.0.0.1`에만 바인딩되어 있어 휴대폰에서 직접 접속할 수 없으며, 모바일 접속에는 별도의 HTTPS 배포 및 OAuth 도메인 설정이 필요하다.

## 데이터와 종료

Docker MySQL의 데이터는 `mysql_data` 볼륨에 저장된다. 기존 PC의 MySQL과는 별개이므로 기존 데이터는 자동으로 복사되지 않는다. 백엔드는 컨테이너 DB에 Flyway 마이그레이션을 적용한다.

```powershell
docker compose logs -f backend
docker compose down
```

일반적인 `down`은 볼륨을 보존한다. `docker compose down -v`는 DB 볼륨을 삭제하므로 데이터를 지울 때만 사용한다. MySQL 계정과 비밀번호는 새 볼륨의 최초 시작 시 생성된다. 이후 `.env.docker`만 수정해도 이미 생성된 DB 계정의 비밀번호는 바뀌지 않으므로 파일을 보관해야 한다.

소셜 로그인과 쿠키는 실제 제공자 콘솔 설정 및 브라우저에서 별도 검증해야 한다. 이 구성은 로컬 개발용이며 운영 배포 설정은 포함하지 않는다.
