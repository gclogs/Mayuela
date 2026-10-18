# Mayuela

Mayuela는 Minecraft Paper 서버 기반의 2인 팀 PvP 점령전 미니게임 플러그인입니다.  
플레이어는 팀 색유리로 제단을 점령하고, 팀 누적 활성화율을 100%까지 올려 승리합니다.

## 개발 기준

- Java 21
- Gradle
- Paper API `1.21.11-R0.1-SNAPSHOT`
- Minecraft/Paper 테스트 서버 `1.21.11`

## 주요 기능

- `/입장` 기반 게임 참가
- `/퇴장` 기반 준비 취소
- 필수 4명 이상, 짝수 인원만 시작, 최대 10명 참가
- 입장 시 `준비` 상태 등록, 게임 시작 시 랜덤 팀/색상 배정
- 입장/퇴장 시 참가자 수 안내
- 운영자 수동 팀 지정과 2명 단위 팀 구성
- 제단 색염료 핫바 4번 슬롯 고정
- 보조 손 잠금 아이템 지급, F키 은행 GUI 열기, 보조 손 교체 차단
- 제단 등록, 목록 확인, 점령 처리
- 팀이 점령한 제단 수 기반 누적 활성화율 계산
- 게임 중 맵 밖 이동 허용
- 팀 색염료로 신호기 클릭 시 서버 설치 방식의 제단 점령
- 맨손 또는 모든 아이템으로 제단 파괴
- 보스바 1위 진행률/점령 누적 시간 표시
- 스코어보드, 팀원 목록, 아이템 lore, `ItemDisplay`, `TextDisplay` 표시
- 맵 안 랜덤 지상 위치 코인 드롭과 자동 제거
- 인원별 코인 인플레이션 설정과 운영자 모니터링
- `/상점열기`, `/은행열기` 은행 GUI와 화살, 밀치기, 점프강화, 밀치기 저항 아이템 구매
- `WAITING` 중 서버 접속 종료 시 Mayuela 참가자 자동 제외
- `READY`/`PLAYING` 중 서버 접속 종료 시 진행 상태 유지 및 재접속 복구
- 이동속도 II 기본 버프, 제단 미점령 팀 힘 버프
- 매치 종료 시 `stats/matches/` 폴더에 이벤트 레벨 기반 통계 YAML 저장
- 사망 시 게임 스폰에서 즉시 부활, 3초 카운트다운 이동 잠금, 인벤토리 보존
- 게임 종료 시 인벤토리 복구
- YAML 기반 설정 저장

## 문서

개발 문서는 `docs/` 폴더에 정리되어 있습니다.

시작 문서:

- [문서 허브](docs/00-문서-허브.md)

주요 문서:

- [개발 원칙](docs/11-개발-원칙.md)
- [시퀀스 다이어그램](docs/12-시퀀스-다이어그램.md)
- [이벤트 처리 매트릭스](docs/13-이벤트-처리-매트릭스.md)
- [Tick/Scheduler 설계](docs/14-Tick-Scheduler-설계.md)
- [성능 예산](docs/18-성능-예산.md)
- [QA 테스트 시나리오](docs/19-QA-테스트-시나리오.md)
- [운영자 명령어 가이드](docs/운영자-명령어-가이드.md)
- [변경로그](docs/CHANGELOG.md)

## 빌드

```bash
./gradlew compileJava
```

Windows PowerShell:

```powershell
.\gradlew.bat compileJava
```

## 개발 서버 실행

```powershell
.\gradlew.bat runServer
```

## 명령어

플레이어 명령어:

```text
/입장
/퇴장
/상점열기
```

관리자 명령어:

```text
/입장 로비등록
/game 맵 pos1
/game 맵 pos2
/game 맵 저장 <이름>
/game 제단등록 <이름>
/game 제단목록
/game 상점등록
/game 스폰등록
/game 팀지정 <플레이어> <색상>
/game 팀해제 <플레이어>
/game 모니터링
/game pdc
/game pdc 인벤토리
/game reload
/game 코인설정 <4|6|8|10> <주기초> <금액>
/game 코인제거 <초>
/game 코인최대 <개수>
/game 넉백설정 <블록>
/game 시작
/game 종료
```

관리자 권한:

```text
mayuela.admin
```

## Git 추적 권장 파일

Git에는 다음 파일과 폴더를 포함합니다.

- `src/`
- `docs/`
- `gradle/`
- `build.gradle`
- `settings.gradle`
- `gradle.properties`
- `gradlew`
- `gradlew.bat`
- `.gitignore`
- `README.md`

Git에서 제외되는 대표 항목:

- `.gradle/`
- `.idea/`
- `build/`
- `bin/`
- `run/`
- Paper 서버 월드/로그/플러그인 런타임 파일

## 현재 구현 상태

현재 코드는 MVP 골격입니다.  
장비 업그레이드 상세와 ItemsAdder 실제 API 연동은 이후 단계에서 확장합니다.

## 로컬 테스트

팀전은 4명 이상 짝수 인원이 필요합니다.  
개인전은 2명 이상부터 시작할 수 있습니다. 1인 검증은 코드 단위 테스트와 서비스 테스트 러너로 확인합니다.

```powershell
.\gradlew.bat build
```

`build`는 기존 팀 서비스 검사와 `gameplayTest`를 함께 실행합니다. `gameplayTest`는 밀치기 거리·충돌, 사망 원인별 현상수배, 커스텀 음악 수명주기를 서버 대역으로 검증합니다. 실제 클라이언트의 이동 감각과 음원 재생은 Paper 1.21.11 서버에서 확인해야 합니다.

## 밀치기 거리와 현상수배

- `/game 넉백설정 <n>`으로 수평 이동 거리를 설정합니다. 기본값은 15블록입니다.
- 빈 공간에서는 지정한 거리를 서버가 직접 이동시킵니다. 벽은 플레이어 몸 전체와 블록 충돌 모양을 검사해 앞에서 멈춥니다. 월드 경계와 아직 로드되지 않은 청크로도 밀어 넣지 않습니다.
- 같은 팀, 부활 대기·스폰 보호, 밀치기 저항은 밀치기를 막습니다. 연속 피격 시 가장 최근 타격 위치에서 새 거리를 적용합니다.
- 현상수배는 상대 팀 참가자의 공격으로 사망했을 때만 해제됩니다. 근접 공격과 상대가 쏜 화살 등은 인정하며, 자기 공격·몹·자살·환경 피해는 인정하지 않습니다. 이전 공격자가 `getKiller()`에 남아 있어도 환경 피해로 죽었다면 유지합니다.
- 점수 차이 감소나 1위 변경으로는 해제되지 않습니다. 게임 종료·초기화는 현상수배를 초기화합니다. 보상을 받을 인벤토리 공간이 없어도 유효한 플레이어 처치라면 해제됩니다.

## 커스텀 OGG 배경음악

플레이어가 적용한 리소스팩의 OGG를 사운드 이름으로 재생합니다. 서버의 MP3/OGG 파일을 클라이언트로 직접 전송하거나 자동 변환하는 기능은 포함하지 않습니다.

리소스팩에 `assets/mayuela/sounds/music/game.ogg`를 넣고 `assets/mayuela/sounds.json`에 다음 항목을 추가합니다. 기존 항목은 유지합니다.

```json
{
  "music.game": {
    "sounds": [{ "name": "mayuela:music/game", "stream": true }]
  }
}
```

플러그인 실행 시 자동 생성되는 `plugins/mayuela/music.yml`에서 설정합니다.

```yaml
music:
  enabled: true
  sound: mayuela:music.game
  duration-seconds: 180
  volume: 0.5
```

`sound`는 파일명이 아닌 `sounds.json`의 이벤트 이름입니다. `duration-seconds`는 곡 길이를 초 단위로 올림해 설정합니다. 짧게 설정하면 반복 시 곡이 잘립니다. `volume`은 0~1이며 클라이언트의 음악 볼륨도 켜져 있어야 합니다. 설정 변경 후 대기 상태에서 `/game reload`를 실행하면 다음 경기부터 적용됩니다.

기존 음악 설정은 자동으로 이전됩니다. 항목별 우선순위는 `music.yml` → `game settings.yml` → `game.yml`이며, 새 파일 저장 성공 후 `game.yml`의 `music` 항목은 제거됩니다. 기존 `game settings.yml`은 백업으로 남습니다. 이후 음악 설정은 `music.yml`에서 수정하고, 다른 게임 설정은 기존 `game.yml`을 사용합니다.

경기 시작 시 참가자에게 재생하고, 설정한 길이가 지나면 반복합니다. 재접속·부활·리소스팩 적용 완료 시 해당 플레이어의 곡을 처음부터 다시 재생하며, 경기 종료·강제 종료·플러그인 종료 시 정지합니다. 리소스팩 배포와 적용은 별도로 필요합니다.

형식 참고: [OGG와 sounds.json](https://docs.fabricmc.net/develop/sounds/custom), [Paper 사운드 API](https://jd.papermc.io/paper/1.21.11/org/bukkit/entity/Player.html).
