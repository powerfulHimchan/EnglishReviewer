# 문장복습 · EnglishReviewer

혼자 사용하는 Android 영어 문장 학습 앱입니다. 영어 문장을 저장하면 AI가 교정, 문법 설명, 예문, 유사 표현을 제공하고 매일 저장한 문장 중 최대 3개를 복습합니다.

## 0.1.0 기능

- Kotlin + Jetpack Compose 네이티브 Android 앱 (Android 8.0 이상)
- Room 로컬 DB에 원문, 교정문, 한국어 뜻, 문법, 예문, 유사 표현 저장
- 문장 원문·교정문·한국어 뜻 검색 (대소문자 무시)
- 앞뒤 공백·연속 공백·대소문자를 정규화한 중복 등록 방지
- WorkManager를 통한 분석 요청, 통신 실패 시 제한된 자동 재시도 및 수동 재시도
- 날짜별 최대 3문장 선정, 최근 선정되지 않은 문장 우선, 선정 결과·완료 상태 영속 저장
- 매일 복습 시간 설정, 로컬 알림, 알림 터치 시 오늘의 복습 이동
- 알림 권한 및 선택적 정확한 알람 접근 권한 처리, 재부팅·시각·시간대 변경 시 재예약
- 연결 토큰을 Android Keystore AES-GCM으로 암호화
- Spring Boot AI 중계 서버, 개인 토큰 인증, 단일 동시 요청 및 시간당 60요청 제한
- CI에서 Android 빌드·Lint·단위 테스트·에뮬레이터 테스트 및 APK 아티팩트 생성

### 데이터와 복습 규칙

문장과 분석 결과는 기기에 저장합니다. 분석 시에만 원문을 중계 서버와 OpenAI에 전송합니다. 복습에서는 저장된 결과를 사용하므로 인터넷과 추가 AI 요청이 필요하지 않습니다. API 키는 APK에 넣지 않습니다.

처음 복습을 열거나 알림이 울릴 때 해당 날짜의 문장을 선정합니다. 선정 이후 새로 등록한 문장은 다음 날부터 선정 대상이 됩니다. 저장된 문장이 3개 미만이면 해당 개수만 표시합니다. 오늘 선정된 문장을 삭제하면 오늘의 복습 개수도 줄어듭니다. 분석 대기·실패 문장도 보관되며 자세히 화면에서 재시도할 수 있습니다. 분석을 마친 문장에만 복습 완료 버튼이 표시됩니다.

알림은 기본적으로 꺼져 있습니다. 설정에서 켜고 시간을 정하세요. 정확한 알람 접근을 허용하지 않으면 절전 상태 등에 따라 알림이 늦어질 수 있습니다. 앱 강제 중지 상태에서는 다시 실행할 때까지 알림이 동작하지 않을 수 있습니다. 모든 문장을 복습한 날에는 알림을 보내지 않습니다.

현재 버전은 회원가입·클라우드 동기화·백업/복원·문장 수정·발음 재생을 포함하지 않습니다. **앱 삭제 시 기기의 문장과 기록이 삭제됩니다.** APK 업데이트 설치 시에는 같은 서명 키를 유지해야 합니다.

## 프로젝트 구성

```text
app/       Android UI, Room, DataStore, WorkManager, AlarmManager
core/      공용 분석 DTO, 입력 검증, 복습 선정 알고리즘
server/    Spring Boot AI 분석 API
```

## 빌드

필요 환경: JDK 21, Android SDK 35, Build Tools 35.0.0. Android Studio에서 저장소 루트를 열어도 됩니다.

```bash
./gradlew :core:test :server:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :server:bootJar
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

GitHub의 **Actions → Android and server checks → 성공한 실행 → Artifacts → EnglishReviewer-0.1.0-debug**에서도 APK를 받을 수 있습니다. 현재 CI는 임시 debug 키로 서명하므로 실행 간 설치 서명이 달라질 수 있습니다. 장기 사용 전에 고정된 개인 서명 키를 설정하세요. 기존 데이터가 있는 상태에서 서명 불일치 해결을 위해 앱을 삭제하지 마세요.

에뮬레이터 또는 USB 디버깅 기기를 연결한 후:

```bash
./gradlew :app:connectedDebugAndroidTest
```

## AI 서버 실행

개인 OpenAI API 키와 무작위 개인 연결 토큰이 필요합니다. 토큰은 24자 이상이어야 하며 `openssl rand -hex 32`로 만들 수 있습니다. 실제 키·토큰을 저장소에 커밋하지 마세요.

```bash
export OPENAI_API_KEY='your-api-key'
export REVIEWER_TOKEN='your-random-personal-token-at-least-24-characters'
export OPENAI_MODEL='gpt-4.1-mini'
./gradlew :server:bootRun
```

서버는 기본 8080 포트를 사용합니다 (`PORT` 환경변수로 변경 가능).

- `GET /health`: 상태 확인
- `POST /api/analyze`: `Authorization: Bearer <REVIEWER_TOKEN>` 및 JSON 요청

```json
{"sentence":"I'm in charge of cooking at home."}
```

응답 필드는 `corrected`, `translation`, `correctionNote`, `grammar[]`, `examples[]`, `similar[]`입니다. 영어 예문과 유사 표현에는 각각 한국어 뜻이 포함되며 유사 표현에는 뉘앙스 설명도 포함됩니다. OpenAI의 strict JSON Schema Structured Outputs를 사용하고 응답 내용을 추가 검증합니다. 실제 AI 호출에는 별도 API 사용 설정과 비용이 필요합니다.

Docker로 실행하려면 저장소 루트에서 빌드합니다.

```bash
docker build -f server/Dockerfile -t english-reviewer .
docker run --rm -p 8080:8080 --env-file server/.env english-reviewer
```

`server/.env.example`을 참고해 별도의 `.env`를 만드세요. 서버를 HTTPS 뒤에 배포한 후 앱의 **설정 → AI 분석 연결**에 서버 기본 주소와 `REVIEWER_TOKEN`을 입력합니다. 앱은 HTTPS만 허용합니다. 서버 배포 및 실제 API 키 설정은 이 저장소의 초기 구현에 포함되지 않습니다. 서버 설정 전에도 문장 등록·검색은 동작하고 AI 분석은 연결 후 다시 시도할 수 있습니다.

### 검증 범위

- 공용 테스트: 입력 검증·정규화, 0~3문장 처리, 중복 없는 선정, 날짜별 안정성, 전체 문장 순환
- 서버 테스트: 인증, 입력 길이, 구조화 응답, 공급자 요청 스키마, 거절·잘못된 응답 처리 (외부 유료 호출 없음)
- 기기 테스트: Room 트랜잭션·동시 선정·복습 완료·삭제 연쇄 처리, 등록·검색·삭제, 화면 이동
- 실제 API 키를 사용한 AI 응답 품질과 실기기 장시간 알림 동작은 별도 확인이 필요합니다.
